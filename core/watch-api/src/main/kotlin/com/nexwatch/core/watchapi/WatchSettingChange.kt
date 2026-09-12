package com.nexwatch.core.watchapi

/**
 * Marker for applySettings() (§4.2). No subtypes yet — Phase 8 (Watch control) defines
 * the real settings (alarms, reminders, DND, units); this keeps the contract compiling
 * without guessing that shape early.
 */
sealed interface WatchSettingChange
