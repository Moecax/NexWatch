package com.nexwatch.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/** The watch's step bucket length (§2). A bucket never claims more time than this. */
const val STEP_BUCKET_MS = 5 * 60_000L

/**
 * §5.3's step-interval rule, start = max(previous end, end − 5 min), applied to rows stored before the
 * normaliser followed it (start == end). Health Connect rejects zero-length step records. The version bump
 * is what makes the change-log triggers hand the correction to sync providers (CLAUDE.md I3).
 */
const val REPAIR_STEP_INTERVALS_SQL =
    "UPDATE steps SET " +
        "start_time = MAX(end_time - $STEP_BUCKET_MS, COALESCE((SELECT MAX(p.end_time) FROM steps p " +
        "WHERE p.device_id = steps.device_id AND p.end_time < steps.end_time AND p.deleted = 0), 0)), " +
        "version = version + 1 " +
        "WHERE start_time = end_time"

/** v2 changes no tables; it repairs the step rows Phase 6 stored as instants. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) = db.execSQL(REPAIR_STEP_INTERVALS_SQL)

    override fun migrate(connection: SQLiteConnection) = connection.execSQL(REPAIR_STEP_INTERVALS_SQL)
}
