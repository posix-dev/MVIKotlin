package com.arkivanov.mvikotlin.timetravel.server

import com.arkivanov.mvikotlin.core.store.StoreEventType
import com.arkivanov.mvikotlin.timetravel.TimeTravelEvent
import com.arkivanov.mvikotlin.timetravel.TimeTravelState
import com.arkivanov.mvikotlin.timetravel.proto.StoreEventType as StoreEventTypeProto
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelEvent as TimeTravelEventProto
import com.arkivanov.mvikotlin.timetravel.proto.TimeTravelStateUpdate as TimeTravelStateUpdateProto
import com.arkivanov.mvikotlin.utils.internal.parseObject

//internal fun TimeTravelState.toProto(): TimeTravelStateProto =
//    TimeTravelStateProto(
//        events = events.map { it.toProto() },
//        selectedEventIndex = selectedEventIndex,
//        mode = mode.toProto()
//    )

internal fun List<TimeTravelEvent>.toProto(): List<TimeTravelEventProto> = map { it.toProto() }

private fun TimeTravelEvent.toProto(): TimeTravelEventProto =
    TimeTravelEventProto(
        id = id,
        storeName = storeName,
        type = type.toProto(),
        value = parseObject(value)
    )

private fun StoreEventType.toProto(): StoreEventTypeProto =
    when (this) {
        StoreEventType.INTENT -> StoreEventTypeProto.INTENT
        StoreEventType.ACTION -> StoreEventTypeProto.ACTION
        StoreEventType.RESULT -> StoreEventTypeProto.RESULT
        StoreEventType.STATE -> StoreEventTypeProto.STATE
        StoreEventType.LABEL -> StoreEventTypeProto.LABEL
    }

internal fun TimeTravelState.Mode.toProto(): TimeTravelStateUpdateProto.Mode =
    when (this) {
        TimeTravelState.Mode.IDLE -> TimeTravelStateUpdateProto.Mode.IDLE
        TimeTravelState.Mode.RECORDING -> TimeTravelStateUpdateProto.Mode.RECORDING
        TimeTravelState.Mode.STOPPED -> TimeTravelStateUpdateProto.Mode.STOPPED
    }
