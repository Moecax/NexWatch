package com.nexwatch.core.export

import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.model.DailySummaryRecord
import com.nexwatch.core.model.Device
import com.nexwatch.core.model.HealthRecord
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class ImportResult(val recordCounts: Map<String, Int>)

private const val IMPORT_BATCH_SIZE = 500

/**
 * Reads the §6.1 format back and inserts through ExportRepository — the same DAO path a live
 * sync uses, so dedup-by-deterministic-ID and change-log triggers apply exactly as usual (§6.3).
 */
class ZipImporter @Inject constructor(private val repository: ExportRepository) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun import(input: InputStream): ImportResult {
        // manifest.json's own position isn't guaranteed to be first (Task 4 writes it last),
        // so buffer the whole archive once rather than assume entry order.
        val bytes = input.readBytes()
        val manifest = readManifest(bytes)
        if (manifest.dbSchemaVersion > repository.schemaVersion) {
            throw ImportException.UnsupportedSchema(manifest.dbSchemaVersion, repository.schemaVersion)
        }

        val counts = linkedMapOf<String, Int>()
        counts["steps"] = importRecords(bytes, "records/steps.jsonl", manifest, HealthRecord.Step.serializer()) { repository.insertSteps(it) }
        // After the whole file, not per batch: a bucket's start depends on its predecessor, which may be in a later batch.
        repository.repairStepIntervals()
        counts["heart_rate"] = importRecords(bytes, "records/heart_rate.jsonl", manifest, HealthRecord.HeartRate.serializer()) { repository.insertHeartRate(it) }
        counts["spo2"] = importRecords(bytes, "records/spo2.jsonl", manifest, HealthRecord.Spo2.serializer()) { repository.insertSpo2(it) }
        counts["blood_pressure"] = importRecords(bytes, "records/blood_pressure.jsonl", manifest, HealthRecord.BloodPressure.serializer()) { repository.insertBloodPressure(it) }
        counts["temperature"] = importRecords(bytes, "records/temperature.jsonl", manifest, HealthRecord.Temperature.serializer()) { repository.insertTemperature(it) }
        counts["stress"] = importRecords(bytes, "records/stress.jsonl", manifest, HealthRecord.Stress.serializer()) { repository.insertStress(it) }
        counts["sleep_session"] = importRecords(bytes, "records/sleep_session.jsonl", manifest, HealthRecord.SleepSession.serializer()) { repository.insertSleepSessions(it) }
        counts["workout"] = importRecords(bytes, "records/workout.jsonl", manifest, HealthRecord.Workout.serializer()) { repository.insertWorkouts(it) }
        counts["daily_summary"] = importRecords(bytes, "derived/daily_summary.jsonl", manifest, DailySummaryRecord.serializer()) { repository.insertDailySummaries(it) }

        repository.upsertDevices(manifest.devices.map { Device(it.id, it.model, it.firmware, sdkVersion = null, boundAtMs = 0L) })

        return ImportResult(counts)
    }

    private fun readManifest(bytes: ByteArray): ExportManifest {
        val text = findEntry(bytes, "manifest.json") ?: throw ImportException.MalformedManifest("manifest.json is missing")
        return try {
            json.decodeFromString(ExportManifest.serializer(), text.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw ImportException.MalformedManifest(e.message ?: "could not parse manifest.json")
        }
    }

    private suspend fun <T> importRecords(
        zipBytes: ByteArray,
        entryName: String,
        manifest: ExportManifest,
        serializer: kotlinx.serialization.KSerializer<T>,
        insert: suspend (List<T>) -> Unit,
    ): Int {
        val bytes = findEntry(zipBytes, entryName) ?: return 0
        val expected = manifest.files[entryName] ?: throw ImportException.MalformedManifest("$entryName missing from manifest")
        val actualSha = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        if (actualSha != expected.sha256) throw ImportException.ChecksumMismatch(entryName)

        var total = 0
        val batch = mutableListOf<T>()
        bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.forEach { line ->
            batch += json.decodeFromString(serializer, line)
            total++
            if (batch.size >= IMPORT_BATCH_SIZE) {
                insert(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) insert(batch)
        return total
    }

    private fun findEntry(zipBytes: ByteArray, name: String): ByteArray? {
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
