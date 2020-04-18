package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.proto.type
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.treeStructure.Tree
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreeSelectionModel


class ItemRenderer : ColoredListCellRenderer<TimeTravelEvent>() {

    override fun customizeCellRenderer(
        list: JList<out TimeTravelEvent>,
        value: TimeTravelEvent,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
    }

    override fun getListCellRendererComponent(
        list: JList<out TimeTravelEvent>,
        event: TimeTravelEvent,
        index: Int,
        isSelected: Boolean,
        cellHadsFocus: Boolean
    ): Component =
        JPanel(GridBagLayout()).apply {
            border = CompoundBorder(MatteBorder(0, 0, 1, 0, Color.GRAY), EmptyBorder(8, 8, 8, 8))
            background = null

            val constraints = GridBagConstraints()
            constraints.fill = GridBagConstraints.HORIZONTAL
            constraints.weightx = 1.0
            constraints.gridx = 0

            add(
                JLabel(event.storeName).apply {
                    font = font.deriveFont(Font.BOLD)
                },
                constraints
            )

            add(
                JLabel("${event.value.type} (${event.type})").apply {
                    font = font.deriveFont(Font.BOLD)
                },
                constraints
            )

//            add(JLabel(event.value.toString()))
            add(tree(), constraints)
        }
}

fun eventComponent(event: TimeTravelEvent): JComponent =
    JPanel(GridBagLayout()).apply {
        border = MatteBorder(0, 0, 1, 0, Color.GRAY)
        background = null

        val constraints = GridBagConstraints()
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.weightx = 1.0
        constraints.gridx = 0

        add(
            JLabel(event.storeName).apply {
                font = font.deriveFont(Font.BOLD)
            },
            constraints
        )

        add(
            JLabel("${event.value.type} (${event.type})").apply {
                font = font.deriveFont(Font.BOLD)
            },
            constraints
        )

//            add(JLabel(event.value.toString()))
        add(tree(), constraints)
    }

fun tree(): Tree {
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
//    val renderer = DefaultTreeCellRenderer()
//    renderer.backgroundSelectionColor = Color.GREEN
//    tree.cellRenderer = renderer

//    tree.addMouseListener(
//        object : MouseAdapter() {
//            override fun mouseClicked(e: MouseEvent) {
//                tree.selectionPath = tree.getPathForLocation(e.x, e.y)
//            }
//        }
//    )

//    tree.cellRenderer = CustomTreeCellRenderer()

//    tree.addTreeSelectionListener { event ->
//        val node = tree.lastSelectedPathComponent ?: return@addTreeSelectionListener
//        tree.selectionPath = node.pa
//    }

    return tree
}

private class CustomTreeCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        if (value is DefaultMutableTreeNode) {
//            text = FileSystemView.getFileSystemView().getSystemDisplayName(value.userObject as File)
//            icon = FileSystemView.getFileSystemView().getSystemIcon(value.userObject as File)
        }
        super.setBackgroundSelectionColor(Color.BLUE)
        foreground = if (selected) {
            super.setBackground(Color.gray)
            getTextSelectionColor()
        } else {
            super.setBackground(Color.CYAN)
            getTextNonSelectionColor()
        }
        this.isOpaque = true
        return this
    }
}
