package com.nexwatch.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

/**
 * Requires the test classpath to resolve Room/sqlite-bundled's "standard-jvm" variant (forced
 * in build.gradle.kts) rather than their "android" variant — the android variant's
 * inMemoryDatabaseBuilder needs a Context, and its native SQLite loader expects a real Android
 * runtime, neither of which exist on a plain desktop JVM test run.
 *
 * `buildNexWatchDatabase()` installs the CLAUDE.md I4 change-log triggers through the
 * Android-only `RoomDatabase.Callback.onOpen(SupportSQLiteDatabase)` overload, which the
 * `BundledSQLiteDriver` connection pool used here never calls. The connection-based
 * `onOpen(SQLiteConnection)` overload is this driver's equivalent hook, so the triggers are
 * re-installed through it here to exercise the same `CREATE TRIGGER IF NOT EXISTS` SQL Task 10
 * wrote in `DatabaseTriggers.kt`.
 */
fun inMemoryTestDatabase(): NexWatchDatabase =
    Room.inMemoryDatabaseBuilder<NexWatchDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(
            object : RoomDatabase.Callback() {
                override fun onOpen(connection: SQLiteConnection) {
                    super.onOpen(connection)
                    allChangeLogTriggerSql().forEach(connection::execSQL)
                }
            },
        )
        .build()
