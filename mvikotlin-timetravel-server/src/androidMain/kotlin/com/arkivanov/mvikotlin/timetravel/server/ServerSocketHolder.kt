package com.arkivanov.mvikotlin.timetravel.server

import java.io.Closeable
import java.net.ServerSocket

internal class ServerSocketHolder : Closeable {

    private val monitor = Object()
    private var isClosed = false
    private var socket: ServerSocket? = null

    fun set(closeable: ServerSocket): Boolean {
        var socketToClose: ServerSocket?

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
        var socketToClose: ServerSocket?
        synchronized(monitor) {
            socketToClose = socket
            socket = null
            isClosed = true
        }

        socketToClose?.closeSafe()
    }
}
