package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.DEFAULT_PORT
import com.arkivanov.mvikotlin.timetravel.proto.StoreEventType
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEventsUpdate
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelStateUpdate
import com.arkivanov.mvikotlin.timetravel.proto.Value
import com.arkivanov.mvikotlin.timetravel.proto.parseObject
import com.arkivanov.mvikotlin.timetravel.proto.type
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import org.jdesktop.swingx.renderer.DefaultListRenderer
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import java.io.BufferedReader
import java.io.ObjectInputStream
import java.net.Socket
import javax.swing.DefaultListModel
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
            cellRenderer = DefaultListRenderer { (it as TimeTravelEvent).description }
            addMouseListener(mouseListener(::onListItemClick))
            addListSelectionListener { onSelectionChanged() }
        }

    private val rootTreeNode = DefaultMutableTreeNode("")
    private val treeModel = DefaultTreeModel(rootTreeNode)
    private val tree = JTree(treeModel)

    val content =
        JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.NORTH)

            add(
                JBSplitter(false).apply {
                    firstComponent = JBScrollPane(list)
                    secondComponent = JBScrollPane(tree)
                },
                BorderLayout.CENTER
            )
        }

    private fun onListItemClick(ev: MouseEvent) {
        if (ev.clickCount == 2) {
            if (list.getCellBounds(0, list.lastVisibleIndex)?.contains(ev.point) == true) {
                list.selectedValue?.also(eventDetailsScreen::show)
            }
        }
    }

    private fun onSelectionChanged() {
        toolbar.updateActionsImmediately()
        updateTree()
    }

    private fun toolbarActions(): DefaultActionGroup =
        DefaultActionGroup().apply {
            add(connectAction())
            addSeparator()
            add(debugAction())
        }

    private fun connectAction(): AnAction =
        anAction(text = "Connect", icon = AllIcons.Actions.RunAll) { connect() }

    private fun debugAction(): AnAction =
        anAction(
            text = "Debug",
            icon = AllIcons.Actions.StartDebugger,
            onUpdate = { it.presentation.isEnabled = list.selectedValue?.type?.isDebuggable == true },
            onAction = { debug() }
        )

    private fun connect() {
        if (isDeviceReady() && forwardPort()) {
            ReaderThread(::onStateUpdate).start()
        }
    }

    private fun debug() {

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
