package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.arkivanov.mvikotlin.timetravel.proto.StoreEventType
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.proto.Value
import com.arkivanov.mvikotlin.timetravel.proto.type
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
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

inline fun mouseListener(crossinline onClick: (MouseEvent) -> Unit): MouseListener =
    object : MouseAdapter() {
        override fun mouseClicked(ev: MouseEvent) {
            onClick(ev)
        }
    }

inline fun anAction(
    text: String? = null,
    icon: Icon? = null,
    crossinline onUpdate: (AnActionEvent) -> Unit = {},
    crossinline onAction: (AnActionEvent) -> Unit
): AnAction =
    object : AnAction() {
        override fun update(ev: AnActionEvent) {
            onUpdate(ev)
        }

        override fun actionPerformed(event: AnActionEvent) {
            onAction(event)
        }
    }.apply {
        templatePresentation.apply {
            this.text = text
            this.icon = icon
            this.isEnabled = false
            this.isEnabledAndVisible = false
        }
    }

internal fun log(text: String) {
    println("TimeTravelServer: $text")
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


internal val Value.size: Int
    get() =
        when (this) {
            is Value.Primitive.Int,
            is Value.Primitive.Long,
            is Value.Primitive.Short,
            is Value.Primitive.Byte,
            is Value.Primitive.Float,
            is Value.Primitive.Double,
            is Value.Primitive.Char,
            is Value.Primitive.Boolean,
            is Value.Object.Other,
            is Value.Object.Unparsed -> -1
            is Value.Object.String -> value?.length ?: -1
            is Value.Object.IntArray -> value?.size ?: -1
            is Value.Object.LongArray -> value?.size ?: -1
            is Value.Object.ShortArray -> value?.size ?: -1
            is Value.Object.ByteArray -> value?.size ?: -1
            is Value.Object.FloatArray -> value?.size ?: -1
            is Value.Object.DoubleArray -> value?.size ?: -1
            is Value.Object.CharArray -> value?.size ?: -1
            is Value.Object.BooleanArray -> value?.size ?: -1
            is Value.Object.Array -> value?.size ?: -1
            is Value.Object.Iterable -> value?.size ?: -1
            is Value.Object.Map -> value?.size ?: -1
        }

internal val Value.valueDescription: String?
    get() =
        when (this) {
            is Value.Primitive.Int -> value.toString()
            is Value.Primitive.Long -> value.toString()
            is Value.Primitive.Short -> value.toString()
            is Value.Primitive.Byte -> value.toString()
            is Value.Primitive.Float -> value.toString()
            is Value.Primitive.Double -> value.toString()
            is Value.Primitive.Char -> value.toString()
            is Value.Primitive.Boolean -> value.toString()
            is Value.Object.String -> value?.let { "\"$it\"" } ?: "null"
            is Value.Object.IntArray -> if (value == null) "null" else null
            is Value.Object.LongArray -> if (value == null) "null" else null
            is Value.Object.ShortArray -> if (value == null) "null" else null
            is Value.Object.ByteArray -> if (value == null) "null" else null
            is Value.Object.FloatArray -> if (value == null) "null" else null
            is Value.Object.DoubleArray -> if (value == null) "null" else null
            is Value.Object.CharArray -> if (value == null) "null" else null
            is Value.Object.BooleanArray -> if (value == null) "null" else null
            is Value.Object.Array -> if (value == null) "null" else null
            is Value.Object.Iterable -> if (value == null) "null" else null
            is Value.Object.Map -> if (value == null) "null" else null
            is Value.Object.Other -> if (value == null) "null" else null
            is Value.Object.Unparsed -> value
        }
