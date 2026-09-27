package com.nexwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SleepStage { AWAKE, LIGHT, DEEP, REM }

@Serializable
data class SleepStageSpan(val stage: SleepStage, val startMs: Long, val endMs: Long)
