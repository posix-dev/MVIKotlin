package com.arkivanov.mvikotlin.timetravel.server

import com.arkivanov.mvikotlin.core.store.StoreEventType
import com.arkivanov.mvikotlin.timetravel.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.TimeTravelState
import com.arkivanov.mvikotlin.timetravel.proto.parseObject
import com.arkivanov.mvikotlin.timetravel.proto.StoreEventType as StoreEventTypeProto
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent as TimeTravelEventProto
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelState as TimeTravelStateProto

internal fun TimeTravelState.toProto(): TimeTravelStateProto =
    TimeTravelStateProto(
        events = events.map { it.toProto() },
        selectedEventIndex = selectedEventIndex,
        mode = mode.toProto()
    )

private fun TimeTravelEvent.toProto(): TimeTravelEventProto =
    TimeTravelEventProto(
        id = id,
        storeName = storeName,
        type = type.toProto(),
        value = parseObject(value),
        state = parseObject(state)
    )

private fun StoreEventType.toProto(): StoreEventTypeProto =
    when (this) {
        StoreEventType.INTENT -> StoreEventTypeProto.INTENT
        StoreEventType.ACTION -> StoreEventTypeProto.ACTION
        StoreEventType.RESULT -> StoreEventTypeProto.RESULT
        StoreEventType.STATE -> StoreEventTypeProto.STATE
        StoreEventType.LABEL -> StoreEventTypeProto.LABEL
    }

private fun TimeTravelState.Mode.toProto(): TimeTravelStateProto.Mode =
    when (this) {
        TimeTravelState.Mode.IDLE -> TimeTravelStateProto.Mode.IDLE
        TimeTravelState.Mode.RECORDING -> TimeTravelStateProto.Mode.RECORDING
        TimeTravelState.Mode.STOPPED -> TimeTravelStateProto.Mode.STOPPED
    }
