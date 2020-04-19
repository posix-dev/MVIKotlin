package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.StoreEventType
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.proto.type
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.BufferedReader
import java.io.IOException
import javax.swing.Icon
import javax.swing.SwingUtilities

fun exec(params: List<String>): Process = Runtime.getRuntime().exec(params.toTypedArray())

fun exec(vararg params: String): Process = exec(params.toList())

@Throws(IOException::class)
fun Process.readError(): String? = errorStream?.bufferedReader()?.use(BufferedReader::readText)

fun runOnUiThread(block: () -> Unit) {
    SwingUtilities.invokeLater(block)
}

inline fun anAction(text: String? = null, icon: Icon? = null, crossinline listener: (AnActionEvent) -> Unit): AnAction =
    object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) {
            listener(event)
        }
    }.apply {
        templatePresentation.apply {
            this.text = text
            this.icon = icon
            this.isEnabled = false
            this.isEnabledAndVisible = false
        }
    }

private val STORE_EVENT_TYPE_ALT_NAMES =
    StoreEventType.values().associateBy({ it }) {
        @Suppress("DefaultLocale")
        it.name.toLowerCase().capitalize()
    }

internal val StoreEventType.altName: String get() = STORE_EVENT_TYPE_ALT_NAMES.getValue(this)

internal val TimeTravelEvent.description: String get() = "[$storeName]: ${type.altName}.${value.type}"

internal val StoreEventType.isDebuggable: Boolean
    get() =
        when (this) {
            StoreEventType.INTENT,
            StoreEventType.ACTION,
            StoreEventType.RESULT,
            StoreEventType.LABEL -> true
            StoreEventType.STATE -> false
        }
