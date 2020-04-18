package com.arkivanov.mvikotlin.timetravel.proto

import java.io.Serializable

data class TimeTravelEvent(
    val id: Long,
    val storeName: String,
    val type: StoreEventType,
    val value: Value
) : Serializable
