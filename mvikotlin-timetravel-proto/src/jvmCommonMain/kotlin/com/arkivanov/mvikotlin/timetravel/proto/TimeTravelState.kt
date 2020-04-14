package com.arkivanov.mvikotlin.timetravel.proto

import java.io.Serializable

data class TimeTravelState(
    val events: List<TimeTravelEvent>,
    val selectedEventIndex: Int,
    val mode: Mode
) : Serializable {

    enum class Mode {
        IDLE, RECORDING, STOPPED
    }
}
