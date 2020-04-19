package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.panel
import java.awt.Component
import javax.swing.JList


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
        panel(LCFlags.fillX) {
            row {
                label(event.description)
            }
        }
}
