package com.arkivanov.mvikotlin.timetravel.server

import android.os.Handler
import android.os.Looper
import com.arkivanov.mvikotlin.rx.observer
import com.arkivanov.mvikotlin.timetravel.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.TimeTravelState
import com.arkivanov.mvikotlin.timetravel.controller.TimeTravelController
import com.arkivanov.mvikotlin.timetravel.controller.timeTravelController
import com.arkivanov.mvikotlin.timetravel.proto.DEFAULT_PORT
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelCommand
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEventsUpdate
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelStateUpdate
import com.arkivanov.mvikotlin.utils.internal.closeSafe
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.WeakHashMap

class TimeTravelServer(
    controller: TimeTravelController = timeTravelController,
    port: Int = DEFAULT_PORT
) {

    private val stateHolder = StateHolder(controller.state)
    private val connectionThread = ConnectionThread(port = port, stateHolder = stateHolder, timeTravelController = timeTravelController)
    private val disposable = controller.states(observer(onNext = stateHolder::offer))

    fun start() {
        connectionThread.start()
    }

    fun stop() {
        disposable.dispose()
        connectionThread.interrupt()
    }

    private class ConnectionThread(
        private val port: Int,
        private val stateHolder: StateHolder,
        private val timeTravelController: TimeTravelController
    ) : Thread() {
        private val socketHolder = CloseableHolder()

        override fun run() {
            while (true) {
                try {
                    listen()
                    break
                } catch (e: IOException) {
                    if (isInterrupted) {
                        break
                    }

                    log("Error in ConnectionThread")
                    e.printStackTrace()
                }

                try {
                    sleep(1000L)
                } catch (ignored: InterruptedException) {
                    interrupt()
                    break
                }
            }

            log("ConnectionThread finished")
        }

        @Throws(IOException::class)
        private fun listen() {
            val serverSocket = ServerSocket(port)
            if (!socketHolder.set(serverSocket)) {
                return
            }

            serverSocket.use {
                val threads = Collections.newSetFromMap(WeakHashMap<Thread, Boolean>())
                try {
                    while (!isInterrupted) {
                        log("Waiting for connection")
                        val socket = serverSocket.accept()
                        log("Socket accepted")

                        val reader = ReaderThread(socket, timeTravelController)
                        threads += reader
                        reader.start()
                        val writer = WriterThread(socket, stateHolder)
                        threads += writer
                        writer.start()
                    }
                } finally {
                    threads.toList().forEach(Thread::interrupt)
                }
            }
        }

        override fun interrupt() {
            socketHolder.close()

            super.interrupt()
        }
    }

    private abstract class SocketThread(
        private val socket: Socket
    ) : Thread() {
        override fun run() {
            try {
                run(socket)
            } catch (e: IOException) {
                log("Error in WriterThread")
                e.printStackTrace()
            } finally {
                socket.closeSafe()
                log("WriterThread finished")
            }
        }

        @Throws(IOException::class)
        protected abstract fun run(socket: Socket)

        override fun interrupt() {
            socket.closeSafe()

            super.interrupt()
        }
    }

    private class ReaderThread(
        socket: Socket,
        private val controller: TimeTravelController
    ) : SocketThread(socket) {
        override fun run(socket: Socket) {
            val input = ObjectInputStream(socket.getInputStream().buffered())
            val handler = Handler(Looper.getMainLooper())

            while (!isInterrupted) {
                val command = input.readObject() as TimeTravelCommand
                handler.post { process(command) }
            }
        }

        private fun process(command: TimeTravelCommand) {
            when (command) {
                is TimeTravelCommand.StartRecording -> controller.startRecording()
                is TimeTravelCommand.StopRecording -> controller.stopRecording()
                is TimeTravelCommand.MoveToStart -> controller.moveToStart()
                is TimeTravelCommand.StepBackward -> controller.stepBackward()
                is TimeTravelCommand.StepForward -> controller.stepForward()
                is TimeTravelCommand.MoveToEnd -> controller.moveToEnd()
                is TimeTravelCommand.Cancel -> controller.cancel()
                is TimeTravelCommand.DebugEvent -> controller.debugEvent(eventId = command.eventId)
            }
        }
    }

    private class WriterThread(
        socket: Socket,
        private val stateHolder: StateHolder
    ) : SocketThread(socket) {
        override fun run(socket: Socket) {
            val output = ObjectOutputStream(socket.getOutputStream().buffered())
            var previousState: TimeTravelState? = null

            while (!isInterrupted) {
                val state =
                    try {
                        stateHolder.getNew(previousState)
                    } catch (e: InterruptedException) {
                        interrupt()
                        break
                    }


                val update =
                    TimeTravelStateUpdate(
                        eventsUpdate = if (previousState == null) {
                            TimeTravelEventsUpdate.All(state.events.toProto())
                        } else {
                            TimeTravelEventsUpdate.New(diffEvents(new = state.events, previous = previousState.events).toProto())
                        },
                        selectedEventIndex = state.selectedEventIndex,
                        mode = state.mode.toProto()
                    )

                previousState = state

                log("Writing state update")
                output.writeObject(update)
                output.flush()
            }
        }

        private fun diffEvents(new: List<TimeTravelEvent>, previous: List<TimeTravelEvent>): List<TimeTravelEvent> =
            if (new.size > previous.size) new.subList(previous.size, new.size) else emptyList()
    }
}
