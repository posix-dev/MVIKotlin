package com.arkivanov.mvikotlin.timetravel.server

import com.arkivanov.mvikotlin.utils.internal.closeSafe
import java.io.Closeable
import java.lang.Object

internal class CloseableHolder : Closeable {

    private val monitor = Object()
    private var isClosed = false
    private var socket: Any? = null

    fun set(closeable: Any): Boolean {
        var socketToClose: Any?

        synchronized(monitor) {
            if (isClosed) {
                socketToClose = closeable
            } else {
                socketToClose = this.socket
                this.socket = closeable
            }
        }

        socketToClose?.closeSafe()

        return socketToClose !== closeable
    }

    override fun close() {
        var socketToClose: Any?
        synchronized(monitor) {
            socketToClose = socket
            socket = null
            isClosed = true
        }

        socketToClose?.closeSafe()
    }
}