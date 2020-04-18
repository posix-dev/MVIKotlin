package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.DEFAULT_PORT
import com.arkivanov.mvikotlin.timetravel.proto.StoreEventType
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEventsUpdate
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelStateUpdate
import com.arkivanov.mvikotlin.timetravel.proto.Value
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.BufferedReader
import java.io.ObjectInputStream
import java.net.Socket
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

class TimeTravelToolWindow {

    private val eventConstraints =
        GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
        }

    private val eventsPanel =
        JPanel(GridBagLayout()).apply {
            border = CompoundBorder(MatteBorder(0, 0, 1, 0, Color.GRAY), EmptyBorder(8, 8, 8, 8))
            background = null
        }

    private val content =
        JPanel(BorderLayout()).apply {
            val actionManager = ActionManager.getInstance()
            val toolBar = actionManager.createActionToolbar(ActionPlaces.COMMANDER_TOOLBAR, createToolbarActions(actionManager), true)
            add(toolBar.component, BorderLayout.NORTH)

            add(JBScrollPane(eventsPanel), BorderLayout.CENTER)
        }

    private fun createToolbarActions(actionManager: ActionManager): DefaultActionGroup {
        val connectAction =
            anAction {

            }

        connectAction.copyFrom(actionManager.getAction(IdeActions.ACTION_DEFAULT_RUNNER));
        connectAction.templatePresentation.text = "Connect";

        return DefaultActionGroup(connectAction)
    }

    init {
        eventsPanel.add(eventComponent(
            TimeTravelEvent(
                id = 1,
                storeName = "MyStore",
                type = StoreEventType.INTENT,
                value = Value.Object.String("Some value")
            )
        ), eventConstraints)

        eventsPanel.add(eventComponent(
            TimeTravelEvent(
                id = 2,
                storeName = "MyStore",
                type = StoreEventType.INTENT,
                value = Value.Object.String("Some value")
            )
        ), eventConstraints)
    }

    private fun onStateUpdate(update: TimeTravelStateUpdate) {
        onEventsUpdate(update.eventsUpdate)
    }

    private fun onEventsUpdate(eventsUpdate: TimeTravelEventsUpdate) {
        when (eventsUpdate) {
            is TimeTravelEventsUpdate.All -> {
                eventsPanel.removeAll()
                addEvents(eventsUpdate.events)
            }

            is TimeTravelEventsUpdate.New -> addEvents(eventsUpdate.events)
        }.let {}
    }

    private fun addEvents(events: Iterable<TimeTravelEvent>) {
        events.forEach {
            eventsPanel.add(eventComponent(it), eventConstraints)
        }
    }

    fun getContent(): JComponent = content

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
