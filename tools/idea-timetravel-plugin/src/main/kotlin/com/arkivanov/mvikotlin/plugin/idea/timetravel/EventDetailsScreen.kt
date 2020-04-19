package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.proto.Value
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class EventDetailsScreen {

    private val actionManager = ActionManager.getInstance()

    fun show(event: TimeTravelEvent, onDebug: () -> Unit) {
        val frame = JFrame(event.description)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane = content(event.value, onDebug)
        frame.size = Dimension(640, 480)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    private fun content(value: Value, onDebug: () -> Unit): Container =
        JPanel(BorderLayout()).apply {
            add(toolbar(onDebug), BorderLayout.NORTH)
            add(tree(value), BorderLayout.CENTER)
        }

    private fun toolbar(onDebug: () -> Unit): JComponent =
        actionManager
            .createActionToolbar(ActionPlaces.COMMANDER_TOOLBAR, toolbarActions(onDebug), true)
            .component

    private fun toolbarActions(onDebug: () -> Unit): DefaultActionGroup =
        DefaultActionGroup(debugAction(onDebug))

    private fun debugAction(listener: () -> Unit): AnAction {
        val action = anAction { listener() }
        action.templatePresentation.apply {
            text = "Debug"
            icon = AllIcons.Actions.StartDebugger
        }

        return action
    }


    private fun tree(value: Value): JComponent {
        val top = DefaultMutableTreeNode("Top node")

        val sub1 = DefaultMutableTreeNode("Sub node 1")
        top.add(sub1)
        val sub2 = DefaultMutableTreeNode("Sub node 2")
        top.add(sub2)
        val sub3 = DefaultMutableTreeNode("Sub node 3")
        top.add(sub3)

        val sub11 = DefaultMutableTreeNode("Sub node 1 1")
        sub1.add(sub11)
        val sub21 = DefaultMutableTreeNode("Sub node 2 1")
        sub2.add(sub21)

        val tree = Tree(top)
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        return JBScrollPane(tree)
    }

}
