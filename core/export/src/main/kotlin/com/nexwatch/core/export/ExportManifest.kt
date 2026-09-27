package com.nexwatch.core.export

import kotlinx.serialization.Serializable

const val EXPORT_FORMAT_NAME = "gtr-companion-export"
const val EXPORT_FORMAT_VERSION = 1

@Serializable
data class ExportManifest(
    val format: String,
    val formatVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val dbSchemaVersion: Int,
    val devices: List<ManifestDevice>,
    val range: ManifestRange,
    val files: Map<String, ManifestFileInfo>,
)

@Serializable
data class ManifestDevice(val id: String, val model: String?, val firmware: String?)

@Serializable
data class ManifestRange(val from: String, val to: String)

@Serializable
data class ManifestFileInfo(val count: Int, val sha256: String)
