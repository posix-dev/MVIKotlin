package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.DEFAULT_PORT
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelCommand
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEventsUpdate
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelStateUpdate
import com.arkivanov.mvikotlin.utils.internal.Value
import com.arkivanov.mvikotlin.utils.internal.closeSafe
import com.arkivanov.mvikotlin.utils.internal.type
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import org.jdesktop.swingx.renderer.CellContext
import org.jdesktop.swingx.renderer.ComponentProvider
import org.jdesktop.swingx.renderer.DefaultListRenderer
import org.jdesktop.swingx.renderer.JRendererLabel
import java.awt.BorderLayout
import java.awt.Font
import java.io.BufferedReader
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class TimeTravelToolWindow(
    private val eventDetailsScreen: EventDetailsScreen
) {

    private val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.COMMANDER_TOOLBAR, toolbarActions(), true)

    private val listModel = DefaultListModel<TimeTravelEvent>()

    private val list =
        JBList(listModel).apply {
            cellRenderer = DefaultListRenderer(
                object : ComponentProvider<JLabel>() {
                    override fun createRendererComponent(): JLabel = JRendererLabel()

                    override fun configureState(cellContext: CellContext) {
                        (rendererComponent as JLabel).horizontalAlignment = horizontalAlignment
                    }

                    override fun format(cellContext: CellContext) {
                        val label = rendererComponent as JLabel
                        val event = cellContext.value as TimeTravelEvent
                        label.text = event.description
                        label.font = font.deriveFont(if (state?.selectedEventIndex == cellContext.row) Font.BOLD else Font.PLAIN)
                    }
                }
            )
            addListSelectionListener { onSelectionChanged() }
        }

    private val rootTreeNode = DefaultMutableTreeNode("")
    private val treeModel = DefaultTreeModel(rootTreeNode)
    private val tree = JTree(treeModel)

    val content =
        JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.NORTH)

            add(
                JBSplitter(false, 0.4F).apply {
                    firstComponent = JBScrollPane(list)
                    secondComponent = JBScrollPane(tree)
                },
                BorderLayout.CENTER
            )
        }

    private var state: State? = null
    private var reader: ReaderThread? = null
    private var writer: WriterThread? = null
    private val isConnected: Boolean get() = (reader != null) && (writer != null)

    init {
        onSelectionChanged()
    }

    private fun onSelectionChanged() {
        toolbar.updateActionsImmediately()
        updateTree()
    }

    private fun toolbarActions(): DefaultActionGroup =
        DefaultActionGroup().apply {
            addAll(connectAction(), disconnectAction())
            addSeparator()
            addAll(
                startRecordingAction(),
                stopRecordingAction(),
                moveToStartAction(),
                stepBackwardAction(),
                stepForwardAction(),
                moveToEndAction(),
                cancelAction()
            )
            addSeparator()
            add(debugAction())
        }

    private fun connectAction(): AnAction =
        anAction(
            text = "Connect",
            icon = AllIcons.Nodes.Plugin,
            onUpdate = { it.presentation.isEnabled = !isConnected },
            onAction = { connect() }
        )

    private fun disconnectAction(): AnAction =
        anAction(
            text = "Disconnect",
            icon = AllIcons.Actions.Close,
            onUpdate = { it.presentation.isEnabled = isConnected },
            onAction = { disconnect() }
        )

    private fun startRecordingAction(): AnAction =
        anAction(
            text = "Start recording",
            icon = AllIcons.Debugger.Db_set_breakpoint,
            onUpdate = { it.presentation.isEnabled = isConnected && (state?.mode?.isRecordingActionEnabled == true) },
            onAction = { writer?.send(TimeTravelCommand.StartRecording) }
        )

    private val TimeTravelStateUpdate.Mode.isRecordingActionEnabled: Boolean
        get() =
            when (this) {
                TimeTravelStateUpdate.Mode.IDLE -> true
                TimeTravelStateUpdate.Mode.RECORDING,
                TimeTravelStateUpdate.Mode.STOPPED -> false
            }

    private fun stopRecordingAction(): AnAction =
        anAction(
            text = "Stop recording",
            icon = AllIcons.Process.StopSmall,
            onUpdate = { it.presentation.isEnabled = isConnected && (state?.mode?.isStopActionEnabled == true) },
            onAction = { writer?.send(TimeTravelCommand.StopRecording) }
        )

    private val TimeTravelStateUpdate.Mode.isStopActionEnabled: Boolean
        get() =
            when (this) {
                TimeTravelStateUpdate.Mode.IDLE -> false
                TimeTravelStateUpdate.Mode.RECORDING -> true
                TimeTravelStateUpdate.Mode.STOPPED -> false
            }

    private fun moveToStartAction(): AnAction =
        anAction(
            text = "Move to start",
            icon = AllIcons.Actions.Play_first,
            onUpdate = { it.presentation.isEnabled = isConnected && (state?.mode?.isMovingActionEnabled == true) },
            onAction = { writer?.send(TimeTravelCommand.MoveToStart) }
        )

    private fun stepBackwardAction(): AnAction =
        anAction(
            text = "Step backward",
            icon = AllIcons.Actions.Play_back,
            onUpdate = { it.presentation.isEnabled = isConnected && (state?.mode?.isMovingActionEnabled == true) },
            onAction = { writer?.send(TimeTravelCommand.StepBackward) }
        )

    private fun stepForwardAction(): AnAction =
        anAction(
            text = "Step forward ",
            icon = AllIcons.Actions.Play_forward,
            onUpdate = { it.presentation.isEnabled = isConnected && (state?.mode?.isMovingActionEnabled == true) },
            onAction = { writer?.send(TimeTravelCommand.StepForward) }
        )

    private fun moveToEndAction(): AnAction =
        anAction(
            text = "Move to end",
            icon = AllIcons.Actions.Play_last,
            onUpdate = { it.presentation.isEnabled = isConnected && (state?.mode?.isMovingActionEnabled == true) },
            onAction = { writer?.send(TimeTravelCommand.MoveToEnd) }
        )

    private val TimeTravelStateUpdate.Mode.isMovingActionEnabled: Boolean
        get() =
            when (this) {
                TimeTravelStateUpdate.Mode.IDLE,
                TimeTravelStateUpdate.Mode.RECORDING -> false
                TimeTravelStateUpdate.Mode.STOPPED -> true
            }

    private fun cancelAction(): AnAction =
        anAction(
            text = "Cancel",
            icon = AllIcons.Actions.Cancel,
            onUpdate = { it.presentation.isEnabled = isConnected && (state?.mode?.isCancelActionEnabled == true) },
            onAction = { writer?.send(TimeTravelCommand.Cancel) }
        )

    private val TimeTravelStateUpdate.Mode.isCancelActionEnabled: Boolean
        get() =
            when (this) {
                TimeTravelStateUpdate.Mode.IDLE -> false
                TimeTravelStateUpdate.Mode.RECORDING -> true
                TimeTravelStateUpdate.Mode.STOPPED -> true
            }

    private fun debugAction(): AnAction =
        anAction(
            text = "Debug",
            icon = AllIcons.Actions.StartDebugger,
            onUpdate = { it.presentation.isEnabled = (writer != null) && (list.selectedValue?.type?.isDebuggable == true) },
            onAction = { debug() }
        )

    private fun connect() {
        if (isDeviceReady() && forwardPort()) {
            ConnectionThread(
                onConnectionResult = ::onConnectionResult,
                onUpdateRead = ::onStateUpdate
            ).start()
        }
    }

    private fun disconnect() {
        reader?.interrupt()
        reader = null
        writer?.interrupt()
        writer = null

        state = null
        listModel.clear()
        updateTree()
    }

    private fun debug() {
        val event = list.selectedValue ?: return
        writer?.send(TimeTravelCommand.DebugEvent(eventId = event.id))
    }

    private fun onConnectionResult(result: ConnectionResult) {
        when (result) {
            is ConnectionResult.Success -> {
                reader = result.reader
                writer = result.writer
            }

            is ConnectionResult.Error -> {
            }
        }.let {}
    }

    private fun onStateUpdate(update: TimeTravelStateUpdate) {
        state = State(selectedEventIndex = update.selectedEventIndex, mode = update.mode)
        onEventsUpdate(update.eventsUpdate)
        list.updateUI()
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
        events.forEach(listModel::addElement)
    }

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

    private fun updateTree() {
        rootTreeNode.removeAllChildren()
        val selectedEvent: TimeTravelEvent? = list.selectedValue

        selectedEvent?.value?.also {
            rootTreeNode.userObject = it.getNodeText()
            rootTreeNode.addChildren(it)
        }

        tree.isVisible = selectedEvent != null
        treeModel.reload()
    }

    private fun Value.toNode(prefix: String? = null): DefaultMutableTreeNode {
        val node = DefaultMutableTreeNode(getNodeText(prefix = prefix))
        node.addChildren(this)

        return node
    }

    private fun Value.getNodeText(prefix: String? = null): String {
        val builder = StringBuilder()
        if (prefix != null) {
            builder.append(prefix)
            builder.append(": ")
        }

        builder.append(type)

        val valueDescription: String? = valueDescription
        if (valueDescription != null) {
            builder.append(" = ")
            builder.append(valueDescription)
        }

        return builder.toString()
    }

    private fun DefaultMutableTreeNode.addChildren(value: Value) {
        when (value) {
            is Value.Primitive.Int,
            is Value.Primitive.Long,
            is Value.Primitive.Short,
            is Value.Primitive.Byte,
            is Value.Primitive.Float,
            is Value.Primitive.Double,
            is Value.Primitive.Char,
            is Value.Primitive.Boolean,
            is Value.Object.String,
            is Value.Object.Unparsed -> Unit
            is Value.Object.IntArray -> addChildren(value)
            is Value.Object.LongArray -> addChildren(value)
            is Value.Object.ShortArray -> addChildren(value)
            is Value.Object.ByteArray -> addChildren(value)
            is Value.Object.FloatArray -> addChildren(value)
            is Value.Object.DoubleArray -> addChildren(value)
            is Value.Object.CharArray -> addChildren(value)
            is Value.Object.BooleanArray -> addChildren(value)
            is Value.Object.Array -> addChildren(value)
            is Value.Object.Iterable -> addChildren(value)
            is Value.Object.Map -> addChildren(value)
            is Value.Object.Other -> addChildren(value)
        }.let {}
    }

    private fun DefaultMutableTreeNode.addChildren(array: Value.Object.IntArray) {
        array.value?.forEachIndexed { index, value -> addArrayItem(index, value.toString()) }
    }

    private fun DefaultMutableTreeNode.addChildren(array: Value.Object.LongArray) {
        array.value?.forEachIndexed { index, value -> addArrayItem(index, value.toString()) }
    }

    private fun DefaultMutableTreeNode.addChildren(array: Value.Object.ShortArray) {
        array.value?.forEachIndexed { index, value -> addArrayItem(index, value.toString()) }
    }

    private fun DefaultMutableTreeNode.addChildren(array: Value.Object.ByteArray) {
        array.value?.forEachIndexed { index, value -> addArrayItem(index, value.toString()) }
    }

    private fun DefaultMutableTreeNode.addChildren(array: Value.Object.FloatArray) {
        array.value?.forEachIndexed { index, value -> addArrayItem(index, value.toString()) }
    }

    private fun DefaultMutableTreeNode.addChildren(array: Value.Object.DoubleArray) {
        array.value?.forEachIndexed { index, value -> addArrayItem(index, value.toString()) }
    }

    private fun DefaultMutableTreeNode.addChildren(array: Value.Object.CharArray) {
        array.value?.forEachIndexed { index, value -> addArrayItem(index, value.toString()) }
    }

    private fun DefaultMutableTreeNode.addChildren(array: Value.Object.BooleanArray) {
        array.value?.forEachIndexed { index, value -> addArrayItem(index, value.toString()) }
    }

    private fun DefaultMutableTreeNode.addArrayItem(index: Int, value: String) {
        add(DefaultMutableTreeNode("[$index]: $value"))
    }

    private fun DefaultMutableTreeNode.addChildren(array: Value.Object.Array) {
        array.value?.forEachIndexed { index, value ->
            add(value.toNode(prefix = "[$index]"))
        }
    }

    private fun DefaultMutableTreeNode.addChildren(iterable: Value.Object.Iterable) {
        iterable.value?.forEachIndexed { index, value ->
            add(value.toNode(prefix = "[$index]"))
        }
    }

    private fun DefaultMutableTreeNode.addChildren(map: Value.Object.Map) {
        map.value?.forEach { (key, value) ->
            add(value.toNode(prefix = "[${key.valueDescription}]"))
        }
    }

    private fun DefaultMutableTreeNode.addChildren(other: Value.Object.Other) {
        other.value?.forEach { (key, value) ->
            add(value.toNode(prefix = key))
        }
    }

    private class ConnectionThread(
        private val onConnectionResult: (ConnectionResult) -> Unit,
        private val onUpdateRead: (TimeTravelStateUpdate) -> Unit
    ) : Thread() {
        override fun run() {
            val socket: Socket
            try {
                socket = Socket("localhost", DEFAULT_PORT)
            } catch (e: IOException) {
                runOnUiThread { onConnectionResult(ConnectionResult.Error(e)) }
                return
            }

            if (isInterrupted) {
                socket.closeSafe()
                return
            }

            val reader = ReaderThread(socket, onUpdateRead)
            reader.start()
            val writer = WriterThread(socket)
            writer.start()

            runOnUiThread { onConnectionResult(ConnectionResult.Success(reader, writer)) }
        }
    }

    private sealed class ConnectionResult {
        class Success(val reader: ReaderThread, val writer: WriterThread) : ConnectionResult()
        class Error(val error: IOException) : ConnectionResult()
    }

    private abstract class SocketThread(
        private val socket: Socket
    ) : Thread() {
        override fun run() {
            try {
                run(socket)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                socket.closeSafe()
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
        private val onUpdateRead: (TimeTravelStateUpdate) -> Unit
    ) : SocketThread(socket) {
        override fun run(socket: Socket) {
            println("Reader thread started")
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

    private class WriterThread(socket: Socket) : SocketThread(socket) {
        private val queue = LinkedBlockingQueue<TimeTravelCommand>()

        override fun run(socket: Socket) {
            println("Writer thread started")
            val output = ObjectOutputStream(socket.getOutputStream().buffered())
            while (!isInterrupted) {
                val command =
                    try {
                        queue.take()
                    } catch (e: InterruptedException) {
                        interrupt()
                        break
                    }

                log("Writing command: $command")
                output.writeObject(command)
                output.flush()
            }
        }

        fun send(command: TimeTravelCommand) {
            queue.offer(command)
        }
    }

    private data class State(
        val selectedEventIndex: Int,
        val mode: TimeTravelStateUpdate.Mode
    )

    private companion object {
        private const val ADB_PATH = "/home/aivanov/dev/android-sdk/platform-tools/adb"
    }
}
