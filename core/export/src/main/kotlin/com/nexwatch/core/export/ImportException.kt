package com.nexwatch.core.export

/** Thrown for anything that makes importing unsafe — never silently ingest a bad file. */
sealed class ImportException(message: String) : Exception(message) {
    data class ChecksumMismatch(val entryName: String) : ImportException("$entryName failed its manifest checksum — the file may be corrupted or tampered with")
    data class UnsupportedSchema(val fileSchemaVersion: Int, val appSchemaVersion: Int) :
        ImportException("Export was made with schema v$fileSchemaVersion; this app only understands up to v$appSchemaVersion")
    data class MalformedManifest(val reason: String) : ImportException("manifest.json is malformed: $reason")
}
