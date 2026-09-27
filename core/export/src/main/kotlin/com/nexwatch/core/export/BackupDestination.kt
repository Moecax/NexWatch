package com.nexwatch.core.export

import java.io.OutputStream

/**
 * Abstracts SAF's DocumentFile so BackupWorker's policy (which files to write/prune, whether
 * it runs at all) is unit-testable on plain JVM without an Android runtime — the real
 * implementation (DocumentFileBackupDestination) wraps ContentResolver/DocumentFile and is
 * exercised by a manual on-device pass instead (§Workflow: hardware/OS-boundary behavior is
 * documented as manually verified here, the same way Phase 4/5 handled CDM/BLE specifics).
 */
interface BackupDestination {
    fun isConfigured(): Boolean
    fun createFile(name: String): OutputStream?
    fun listExistingBackups(): List<BackupFile>
    fun delete(file: BackupFile)
}

data class BackupFile(val name: String, val createdAtMs: Long, val id: String)
