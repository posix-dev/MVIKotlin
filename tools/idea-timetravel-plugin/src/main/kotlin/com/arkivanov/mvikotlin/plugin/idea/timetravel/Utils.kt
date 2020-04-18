package com.arkivanov.mvikotlin.plugin.idea.timetravel

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.BufferedReader
import java.io.IOException
import javax.swing.SwingUtilities

fun exec(params: List<String>): Process = Runtime.getRuntime().exec(params.toTypedArray())

fun exec(vararg params: String): Process = exec(params.toList())

@Throws(IOException::class)
fun Process.readError(): String? = errorStream?.bufferedReader()?.use(BufferedReader::readText)

fun runOnUiThread(block: () -> Unit) {
    SwingUtilities.invokeLater(block)
}

inline fun anAction(crossinline listener: (AnActionEvent) -> Unit): AnAction =
    object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) {
            listener(event)
        }
    }
