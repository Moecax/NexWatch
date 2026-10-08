package com.nexwatch.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

private const val DATABASE_NAME = "nexwatch.db"

fun buildNexWatchDatabase(context: Context): NexWatchDatabase =
    Room.databaseBuilder(context.applicationContext, NexWatchDatabase::class.java, DATABASE_NAME)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                allChangeLogTriggerSql().forEach(db::execSQL)
            }
        })
        .addMigrations(MIGRATION_1_2)
        .build()
