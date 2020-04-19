package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.proto.Value
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class EventDetailsScreen {

    fun show(event: TimeTravelEvent) {
        val frame = JFrame(event.description)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane = tree(event.value)
        frame.size = Dimension(640, 480)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
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
