package com.arkivanov.mvikotlin.timetravel.server

import com.arkivanov.mvikotlin.rx.observer
import com.arkivanov.mvikotlin.timetravel.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.TimeTravelState
import com.arkivanov.mvikotlin.timetravel.controller.TimeTravelController
import com.arkivanov.mvikotlin.timetravel.controller.timeTravelController
import com.arkivanov.mvikotlin.timetravel.proto.DEFAULT_PORT
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelStateUpdate
import java.io.IOException
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.WeakHashMap

class TimeTravelServer(
    private val controller: TimeTravelController = timeTravelController,
    private val port: Int = DEFAULT_PORT
) {

    private val stateHolder = StateHolder(controller.state)
    private val connectionThread = ConnectionThread(port = port, stateHolder = stateHolder)
    private val disposable = controller.states(observer(onNext = stateHolder::offer))

    init {
        connectionThread.start()
    }

    fun destroy() {
        disposable.dispose()
        connectionThread.interrupt()
    }

    private class ConnectionThread(
        private val port: Int,
        private val stateHolder: StateHolder
    ) : Thread() {
        private val socketHolder = ServerSocketHolder()

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

    private class WriterThread(
        private val socket: Socket,
        private val stateHolder: StateHolder
    ) : Thread() {
        override fun run() {
            try {
                execute()
            } catch (e: IOException) {
                log("Error in WriterThread")
                e.printStackTrace()
            } finally {
                socket.closeSafe()
                log("WriterThread finished")
            }
        }

        override fun interrupt() {
            socket.closeSafe()

            super.interrupt()
        }

        @Throws(IOException::class)
        private fun execute() {
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
                    if (previousState == null) {
                        TimeTravelStateUpdate.Full(state.toProto())
                    } else {
                        TimeTravelStateUpdate.Update(diffState(new = state, previous = previousState).toProto())
                    }

                previousState = state

                log("Writing state update")
                output.writeObject(update)
                output.flush()
            }
        }

        private fun diffState(new: TimeTravelState, previous: TimeTravelState): TimeTravelState {
            val events = ArrayList<TimeTravelEvent>()
            for (i in previous.events.size until new.events.size) {
                events += new.events[i]
            }

            return new.copy(events = events)
        }
    }
}
