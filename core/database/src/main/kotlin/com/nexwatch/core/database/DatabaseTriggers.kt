package com.nexwatch.core.database

/**
 * CLAUDE.md I4: change_log is written only by these triggers, created here with
 * IF NOT EXISTS so onOpen can run every launch without erroring on an existing trigger.
 * One AFTER INSERT and one AFTER UPDATE per RecordMeta-bearing table (§5.4).
 */
internal val CHANGE_LOG_TRIGGER_TABLES = listOf(
    "heart_rate", "spo2", "blood_pressure", "temperature", "stress", "steps", "sleep_session", "workout",
)

internal fun changeLogTriggerSql(table: String): List<String> = listOf(
    """
    CREATE TRIGGER IF NOT EXISTS trg_${table}_ai AFTER INSERT ON $table
    BEGIN
      INSERT INTO change_log(record_type, record_id, op, version, changed_at)
      VALUES ('$table', NEW.pk, CASE WHEN NEW.deleted THEN 'DELETE' ELSE 'UPSERT' END,
              NEW.version, CAST(strftime('%s','now') AS INTEGER) * 1000);
    END;
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS trg_${table}_au AFTER UPDATE ON $table
    BEGIN
      INSERT INTO change_log(record_type, record_id, op, version, changed_at)
      VALUES ('$table', NEW.pk, CASE WHEN NEW.deleted THEN 'DELETE' ELSE 'UPSERT' END,
              NEW.version, CAST(strftime('%s','now') AS INTEGER) * 1000);
    END;
    """.trimIndent(),
)

internal fun allChangeLogTriggerSql(): List<String> = CHANGE_LOG_TRIGGER_TABLES.flatMap(::changeLogTriggerSql)
