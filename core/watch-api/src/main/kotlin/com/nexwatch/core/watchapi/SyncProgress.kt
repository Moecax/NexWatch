package com.nexwatch.core.watchapi

/**
 * One SDK-shaped item pulled during syncData() (§5.2) — the journal writer in
 * :core:data stores payloadJson as-is, untouched, into raw_ingest.
 */
data class RawBatch(
    val dataType: String,
    val payloadJson: String,
)

data class SyncProgress(
    val batch: RawBatch?,
    val itemsSynced: Int,
    val totalItems: Int,
    val completed: Boolean,
)
