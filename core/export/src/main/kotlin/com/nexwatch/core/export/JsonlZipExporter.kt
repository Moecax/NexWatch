package com.nexwatch.core.export

import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.model.DailySummaryRecord
import com.nexwatch.core.model.ExportHistoryEntry
import com.nexwatch.core.model.HealthRecord
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PAGE_SIZE = 1000

/**
 * §6.1/§6.2: streams every table straight from a DB page into a ZipOutputStream entry and
 * discards the page before fetching the next one. Takes ownership of [out] (closes it).
 */
class JsonlZipExporter @Inject constructor(
    private val repository: ExportRepository,
    private val appVersionProvider: AppVersionProvider,
) {
    private val json = Json { encodeDefaults = true }
    private var minMs = Long.MAX_VALUE
    private var maxMs = Long.MIN_VALUE

    suspend fun export(out: OutputStream): ExportHistoryEntry {
        minMs = Long.MAX_VALUE
        maxMs = Long.MIN_VALUE
        val zip = ZipOutputStream(out)
        val fileInfo = linkedMapOf<String, ManifestFileInfo>()
        val counts = linkedMapOf<String, Int>()

        writeRecords(zip, "records/steps.jsonl", HealthRecord.Step.serializer(), fileInfo, counts, "steps",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,count,distance_m,energy_kcal",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.count},${it.distanceM},${it.energyKcal}" },
        ) { a, l -> repository.pageSteps(a, l) }

        writeRecords(zip, "records/heart_rate.jsonl", HealthRecord.HeartRate.serializer(), fileInfo, counts, "heart_rate",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,bpm",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.bpm}" },
        ) { a, l -> repository.pageHeartRate(a, l) }

        writeRecords(zip, "records/spo2.jsonl", HealthRecord.Spo2.serializer(), fileInfo, counts, "spo2",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,percent",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.percent}" },
        ) { a, l -> repository.pageSpo2(a, l) }

        writeRecords(zip, "records/blood_pressure.jsonl", HealthRecord.BloodPressure.serializer(), fileInfo, counts, "blood_pressure",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,systolic,diastolic",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.systolic},${it.diastolic}" },
        ) { a, l -> repository.pageBloodPressure(a, l) }

        writeRecords(zip, "records/temperature.jsonl", HealthRecord.Temperature.serializer(), fileInfo, counts, "temperature",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,celsius",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.celsius}" },
        ) { a, l -> repository.pageTemperature(a, l) }

        writeRecords(zip, "records/stress.jsonl", HealthRecord.Stress.serializer(), fileInfo, counts, "stress",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,level",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.level}" },
        ) { a, l -> repository.pageStress(a, l) }

        writeRecords(zip, "records/sleep_session.jsonl", HealthRecord.SleepSession.serializer(), fileInfo, counts, "sleep_session",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,night_date,content_hash,score,efficiency,stage_count",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.nightDate},${it.contentHash},${it.score},${it.efficiency},${it.stages.size}" },
        ) { a, l -> repository.pageSleepSessions(a, l) }

        writeRecords(zip, "records/workout.jsonl", HealthRecord.Workout.serializer(), fileInfo, counts, "workout",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,sport_id,sport_type,duration_s,distance_m,energy_kcal,avg_hr_bpm,max_hr_bpm,steps,route_point_count",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.sportId},${it.sportType},${it.durationS},${it.distanceM},${it.energyKcal},${it.avgHrBpm ?: ""},${it.maxHrBpm ?: ""},${it.steps ?: ""},${it.route.size}" },
        ) { a, l -> repository.pageWorkouts(a, l) }

        writeDailySummaries(zip, fileInfo, counts)

        val devices = repository.allDevices()
        val manifest = ExportManifest(
            format = EXPORT_FORMAT_NAME,
            formatVersion = EXPORT_FORMAT_VERSION,
            exportedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            appVersion = appVersionProvider.versionName(),
            dbSchemaVersion = repository.schemaVersion,
            devices = devices.map { ManifestDevice(it.address, it.model, it.firmwareVersion) },
            range = ManifestRange(from = isoDate(if (minMs == Long.MAX_VALUE) System.currentTimeMillis() else minMs),
                to = isoDate(if (maxMs == Long.MIN_VALUE) System.currentTimeMillis() else maxMs)),
            files = fileInfo,
        )
        writeManifest(zip, manifest)
        zip.close()

        return ExportHistoryEntry(atMs = System.currentTimeMillis(), uri = "", format = "zip",
            range = "${manifest.range.from}..${manifest.range.to}", recordCounts = counts)
    }

    /**
     * Holding [csvRows] in memory per table trades the "never load a whole table" rule for CSV
     * specifically: at this app's scale (§5.6 — a few hundred rows/day per table), even five
     * years is low hundreds of thousands of short strings, a few tens of MB at most. Deferred
     * to a same-page-interleaved third ZipEntry if that ever stops being true.
     */
    private suspend fun <T : HealthRecord> writeRecords(
        zip: ZipOutputStream,
        entryName: String,
        serializer: KSerializer<T>,
        fileInfo: MutableMap<String, ManifestFileInfo>,
        counts: MutableMap<String, Int>,
        countKey: String,
        csvHeader: String,
        toCsvRow: (T) -> String,
        nextPage: suspend (afterId: String, limit: Int) -> List<T>,
    ) {
        val jsonlDigest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(entryName))
        // DigestOutputStream wraps `zip` so every byte written is hashed as it streams; the
        // BufferedWriter must only be flushed, never closed, or it would close `zip` too (Writer.close()
        // cascades through OutputStreamWriter -> DigestOutputStream -> ZipOutputStream) and truncate the archive.
        val jsonlWriter = BufferedWriter(OutputStreamWriter(DigestOutputStream(zip, jsonlDigest), Charsets.UTF_8))
        var cursor = ""
        var count = 0
        val csvRows = mutableListOf<String>()
        while (true) {
            val page = nextPage(cursor, PAGE_SIZE)
            if (page.isEmpty()) break
            for (record in page) {
                jsonlWriter.write(json.encodeToString(serializer, record))
                jsonlWriter.newLine()
                csvRows += toCsvRow(record)
                if (record.startMs < minMs) minMs = record.startMs
                if (record.endMs > maxMs) maxMs = record.endMs
                count++
            }
            cursor = page.last().id
        }
        jsonlWriter.flush()
        zip.closeEntry()
        fileInfo[entryName] = ManifestFileInfo(count, jsonlDigest.digest().toHex())
        counts[countKey] = count

        val csvName = "csv/${countKey}.csv"
        val csvDigest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(csvName))
        val csvWriter = BufferedWriter(OutputStreamWriter(DigestOutputStream(zip, csvDigest), Charsets.UTF_8))
        csvWriter.write(csvHeader)
        csvWriter.newLine()
        csvRows.forEach { csvWriter.write(it); csvWriter.newLine() }
        csvWriter.flush()
        zip.closeEntry()
        fileInfo[csvName] = ManifestFileInfo(count, csvDigest.digest().toHex())
    }

    private suspend fun writeDailySummaries(
        zip: ZipOutputStream,
        fileInfo: MutableMap<String, ManifestFileInfo>,
        counts: MutableMap<String, Int>,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry("derived/daily_summary.jsonl"))
        val digestOut = DigestOutputStream(zip, digest)
        val writer = BufferedWriter(OutputStreamWriter(digestOut, Charsets.UTF_8))
        var cursor = 0L
        var count = 0
        while (true) {
            val page = repository.pageDailySummaries(cursor, PAGE_SIZE)
            if (page.isEmpty()) break
            for (record in page) {
                writer.write(json.encodeToString(DailySummaryRecord.serializer(), record))
                writer.newLine()
                count++
            }
            cursor = repository.dailySummaryCursorAfter(cursor, PAGE_SIZE) ?: break
        }
        writer.flush()
        zip.closeEntry()
        fileInfo["derived/daily_summary.jsonl"] = ManifestFileInfo(count, digest.digest().toHex())
        counts["daily_summary"] = count
    }

    private fun writeManifest(zip: ZipOutputStream, manifest: ExportManifest) {
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(json.encodeToString(ExportManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun isoDate(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
