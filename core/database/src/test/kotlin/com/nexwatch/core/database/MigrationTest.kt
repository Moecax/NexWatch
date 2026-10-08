package com.nexwatch.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        schemaDirectoryPath = Paths.get("schemas"),
        databasePath = Files.createTempDirectory("migration-test").resolve("nexwatch.db"),
        driver = BundledSQLiteDriver(),
        databaseClass = NexWatchDatabase::class,
    )

    @Test
    fun `1 to 2 turns instant step rows into intervals and logs the correction`() {
        helper.createDatabase(1).use { v1 ->
            // Triggers are created on open, not by the schema, so a database that has been opened has them.
            allChangeLogTriggerSql().forEach(v1::execSQL)
            v1.insertStep("first", endMs = 1_000_000L)
            v1.insertStep("soon-after", endMs = 1_120_000L)
            v1.insertStep("after-gap", endMs = 5_000_000L)
            v1.insertStep("already-an-interval", endMs = 9_000_000L, startMs = 8_900_000L)
            v1.execSQL("DELETE FROM change_log")
        }

        helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).use { v2 ->
            assertEquals(
                listOf(
                    Triple("first", 700_000L, 2),
                    Triple("soon-after", 1_000_000L, 2),
                    Triple("after-gap", 4_700_000L, 2),
                    Triple("already-an-interval", 8_900_000L, 1),
                ),
                v2.query("SELECT pk, start_time, version FROM steps ORDER BY end_time") {
                    Triple(it.getText(0), it.getLong(1), it.getInt(2))
                },
            )
            assertEquals(
                listOf("first", "soon-after", "after-gap"),
                v2.query("SELECT record_id FROM change_log ORDER BY seq") { it.getText(0) },
            )
        }
    }

    private fun SQLiteConnection.insertStep(pk: String, endMs: Long, startMs: Long = endMs) = execSQL(
        "INSERT INTO steps (pk, count, distance_m, energy_kcal, dedupe_key, device_id, start_time, end_time, " +
            "zone_offset_s, origin, version, deleted, ingested_at) " +
            "VALUES ('$pk', 10, 7.0, 0.4, 'steps:AA:$endMs', 'AA', $startMs, $endMs, 0, 'MONITOR', 1, 0, 0)",
    )

    private fun <T> SQLiteConnection.query(sql: String, row: (androidx.sqlite.SQLiteStatement) -> T): List<T> =
        prepare(sql).use { statement -> buildList { while (statement.step()) add(row(statement)) } }
}
