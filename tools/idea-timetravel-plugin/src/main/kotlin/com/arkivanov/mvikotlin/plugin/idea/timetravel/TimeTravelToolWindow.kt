package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.DEFAULT_PORT
import com.arkivanov.mvikotlin.timetravel.proto.StoreEventType
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEventsUpdate
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelStateUpdate
import com.arkivanov.mvikotlin.timetravel.proto.Value
import com.arkivanov.mvikotlin.timetravel.proto.parseObject
import com.arkivanov.mvikotlin.timetravel.proto.type
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.io.BufferedReader
import java.io.ObjectInputStream
import java.net.Socket
import javax.swing.DefaultListModel
import javax.swing.JPanel

class TimeTravelToolWindow {

    private val listModel = DefaultListModel<String>()

    val content =
        JPanel(BorderLayout()).apply {
            val actionManager = ActionManager.getInstance()
            val toolBar = actionManager.createActionToolbar(ActionPlaces.COMMANDER_TOOLBAR, createToolbarActions(actionManager), true)
            add(toolBar.component, BorderLayout.NORTH)

            add(JBScrollPane(JBList(listModel)), BorderLayout.CENTER)
        }

    private fun createToolbarActions(actionManager: ActionManager): DefaultActionGroup {
        val connectAction = anAction { connect() }
        connectAction.copyFrom(actionManager.getAction(IdeActions.ACTION_DEFAULT_RUNNER));
        connectAction.templatePresentation.text = "Connect";

        return DefaultActionGroup(connectAction)
    }

    private fun connect() {
        if (isDeviceReady() && forwardPort()) {
            ReaderThread(::onStateUpdate).start()
        }
    }

    data class State(val text: String)

    init {
        addEvents(
            listOf(
                TimeTravelEvent(
                    id = 1,
                    storeName = "MyStore",
                    type = StoreEventType.INTENT,
                    value = Value.Object.String("Some value")
                ),
                TimeTravelEvent(
                    id = 2,
                    storeName = "MyStore",
                    type = StoreEventType.STATE,
                    value = parseObject(State(text = "Some text"))
                )
            )
        )
    }

    private fun onStateUpdate(update: TimeTravelStateUpdate) {
        onEventsUpdate(update.eventsUpdate)
    }

    private fun onEventsUpdate(eventsUpdate: TimeTravelEventsUpdate) {
        when (eventsUpdate) {
            is TimeTravelEventsUpdate.All -> {
                listModel.clear()
                addEvents(eventsUpdate.events)
            }

            is TimeTravelEventsUpdate.New -> addEvents(eventsUpdate.events)
        }.let {}
    }

    private fun addEvents(events: Iterable<TimeTravelEvent>) {
        events.forEach { listModel.addElement(it.description) }
    }

    private val TimeTravelEvent.description: String get() = "[$storeName]: ${type.altName}.${value.type}"

    private fun isDeviceReady(): Boolean {
        try {
            val process = exec(ADB_PATH, "devices", "-l")
            if (process.waitFor() == 0) {
                val devices = process.inputStream.bufferedReader().use(BufferedReader::readLines)

//                return when (devices.size) {
//                    0 -> {
//                        println("No devices connected")
//                        false
//                    }
//                    1 -> true
//                    else -> {
//                        println("More than one device connected")
//                        false
//                    }
//                }

                return true
            } else {
                println("Failed to get list of connected devices: ${process.readError()}")
            }
        } catch (e: Exception) {
            println("Failed to get list of connected devices: ${e.message} ")
            e.printStackTrace()
        }

        return false
    }

    private fun forwardPort(deviceId: String? = null): Boolean {
        try {
            val params =
                if (deviceId == null) {
                    listOf(ADB_PATH, "forward", "tcp:$DEFAULT_PORT", "tcp:$DEFAULT_PORT")
                } else {
                    listOf(
                        ADB_PATH,
                        "-s",
                        deviceId,
                        "forward",
                        "tcp:$DEFAULT_PORT",
                        "tcp:$DEFAULT_PORT"
                    )
                }

            val process = exec(params)
            if (process.waitFor() == 0) {
                println("Port forwarded successfully")
                return true
            } else {
                println("Failed to forward the port: ${process.readError()}")
            }
        } catch (e: Exception) {
            println("Failed to forward the port: ${e.message}")
            e.printStackTrace()
        }

        return false
    }

    private class ReaderThread(
        private val onUpdateRead: (TimeTravelStateUpdate) -> Unit
    ) : Thread() {
        override fun run() {
            super.run()

            println("Reader thread started")
            Socket("localhost", DEFAULT_PORT).use { socket ->
                println("Reader socket created")
                val input = ObjectInputStream(socket.getInputStream().buffered())
                while (!isInterrupted) {
                    println("Reading...")
                    val update = input.readObject() as TimeTravelStateUpdate
                    println("Update read: $update")
                    runOnUiThread {
                        onUpdateRead(update)
                    }
                }
            }
        }
    }

    private companion object {
        private const val ADB_PATH = "/home/aivanov/dev/android-sdk/platform-tools/adb"
    }
}
