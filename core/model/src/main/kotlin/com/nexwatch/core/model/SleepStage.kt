package com.nexwatch.core.model

enum class SleepStage { AWAKE, LIGHT, DEEP, REM }

data class SleepStageSpan(val stage: SleepStage, val startMs: Long, val endMs: Long)
