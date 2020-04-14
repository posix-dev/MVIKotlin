package com.arkivanov.mvikotlin.timetravel.server

import java.io.Closeable

internal fun Closeable.closeSafe() {
    try {
        close()
    } catch (ignored: Exception) {
    }
}

internal fun log(text: String) {
    println("TimeTravelServer: $text")
}
