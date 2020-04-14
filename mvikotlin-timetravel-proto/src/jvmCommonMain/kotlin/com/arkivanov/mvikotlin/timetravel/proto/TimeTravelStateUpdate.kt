package com.arkivanov.mvikotlin.timetravel.proto

import java.io.Serializable

sealed class TimeTravelStateUpdate : Serializable {

    data class Full(val state: TimeTravelState) : TimeTravelStateUpdate()
    data class Update(val state: TimeTravelState) : TimeTravelStateUpdate()
}
