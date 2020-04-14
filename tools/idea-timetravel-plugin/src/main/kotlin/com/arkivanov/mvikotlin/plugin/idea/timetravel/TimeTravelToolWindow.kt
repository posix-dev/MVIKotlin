package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.DEFAULT_PORT
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelStateUpdate
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.panel
import java.io.BufferedReader
import java.io.ObjectInputStream
import java.net.Socket
import javax.swing.DefaultListModel
import javax.swing.JComponent

class TimeTravelToolWindow(toolWindow: ToolWindow) {

    private val listModel = DefaultListModel<String>()

    private val content =
        panel {
            row {
                button("Connect") {
                    if (isDeviceReady() && forwardPort()) {
                        ReaderThread {
                            when (it) {
                                is TimeTravelStateUpdate.Full -> onFull(it)
                                is TimeTravelStateUpdate.Update -> onUpdate(it)
                            }.let {}
                        }.start()
                    }
                }
            }

            row {
                JBList(listModel)()
            }
        }

    private fun onFull(full: TimeTravelStateUpdate.Full) {
        listModel.clear()
        full.state.events.forEach {
            listModel.addElement("${it.storeName}: ${it.type.name}, ${it.value}")
        }
    }

    private fun onUpdate(update: TimeTravelStateUpdate.Update) {
        update.state.events.forEach {
            listModel.addElement("${it.storeName}: ${it.type.name}, ${it.value}")
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
                    listOf(ADB_PATH, "-s", deviceId, "forward", "tcp:$DEFAULT_PORT", "tcp:$DEFAULT_PORT")
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
