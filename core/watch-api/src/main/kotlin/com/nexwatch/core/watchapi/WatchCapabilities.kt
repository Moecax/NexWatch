package com.nexwatch.core.watchapi

/**
 * Converted from FcDeviceInfo.isSupport(Feature.X) checks after each CONNECTED (§4.5).
 * The UI shows only supported features; the sync pipeline only expects supported types.
 */
data class WatchCapabilities(
    val heartRate: Boolean,
    val spo2: Boolean,
    val bloodPressure: Boolean,
    val temperature: Boolean,
    val stress: Boolean,
    val sport: Boolean,
    val gps: Boolean,
    val advancedReminders: Boolean,
    val weather: Boolean,
    val contactsLimit: Int?,
    val firmwareVersion: String,
)
