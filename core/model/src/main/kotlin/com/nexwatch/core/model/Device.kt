package com.nexwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val address: String,
    val model: String?,
    val firmwareVersion: String?,
    val sdkVersion: String?,
    val boundAtMs: Long,
)

enum class DeviceEventType { BOUND, UNBOUND, FIRMWARE_UPDATED, TIMEZONE_CHANGED }

data class DeviceEvent(
    val deviceAddress: String,
    val type: DeviceEventType,
    val details: String?,
    val atMs: Long,
)
