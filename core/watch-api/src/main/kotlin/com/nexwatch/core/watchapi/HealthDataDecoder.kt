package com.nexwatch.core.watchapi

import com.nexwatch.core.model.DecodedHealthRecord

/**
 * §5.2's decode step. Implemented in :core:watch-fitcloud, the only module allowed to
 * reconstruct FcSyncData from raw bytes — :core:data calls this and never sees an SDK type.
 * A dataType this implementation doesn't recognise returns an empty list rather than
 * throwing, so one unmapped payload never blocks the rest of a normalization batch.
 */
interface HealthDataDecoder {
    fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord>
}
