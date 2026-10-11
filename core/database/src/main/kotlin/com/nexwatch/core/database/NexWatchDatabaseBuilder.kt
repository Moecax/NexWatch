package com.nexwatch.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Public so storage reporting can size the file without guessing its name. */
const val NEXWATCH_DATABASE_NAME = "nexwatch.db"

fun buildNexWatchDatabase(context: Context): NexWatchDatabase =
    Room.databaseBuilder(context.applicationContext, NexWatchDatabase::class.java, NEXWATCH_DATABASE_NAME)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                allChangeLogTriggerSql().forEach(db::execSQL)
            }
        })
        .addMigrations(MIGRATION_1_2)
        .build()
