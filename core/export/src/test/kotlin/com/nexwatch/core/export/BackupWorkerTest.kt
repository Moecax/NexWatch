package com.nexwatch.core.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.backup.BackupSettings
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.database.NexWatchDatabase
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

private class FixedAppVersion : AppVersionProvider { override fun versionName() = "1.0.0-test" }

private class FakeBackupDestination(
    private var configured: Boolean = true,
    private val files: MutableList<BackupFile> = mutableListOf(),
) : BackupDestination {
    val created = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    override fun isConfigured() = configured
    override fun createFile(name: String) = ByteArrayOutputStream().also { created += name; files += BackupFile(name, files.size.toLong(), name) }
    override fun listExistingBackups() = files.toList()
    override fun delete(file: BackupFile) { deleted += file.name; files.removeAll { it.id == file.id } }
}

class BackupWorkerTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    private fun repo(db: NexWatchDatabase) = ExportRepository(db.stepsDao(),
        db.healthSampleDao(), db.sleepDao(), db.workoutDao(), db.dailySummaryDao(), db.deviceDao(),
        db.exportHistoryDao(), dispatchers)

    @Test
    fun `does nothing when auto-backup is disabled`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val destination = FakeBackupDestination()

        val result = runBackup(BackupSettings(enabled = false, folderUri = null, keepCount = 5),
            destination, JsonlZipExporter(repo(db), FixedAppVersion()), repo(db))

        assertTrue(result)
        assertEquals(0, destination.created.size)
        db.close()
    }

    @Test
    fun `writes a backup and prunes beyond keepCount when enabled`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val destination = FakeBackupDestination(files = mutableListOf(
            BackupFile("old-1", 1L, "old-1"), BackupFile("old-2", 2L, "old-2"),
        ))

        val result = runBackup(BackupSettings(enabled = true, folderUri = "content://x", keepCount = 2),
            destination, JsonlZipExporter(repo(db), FixedAppVersion()), repo(db))

        assertTrue(result)
        assertEquals(1, destination.created.size)
        assertEquals(1, destination.deleted.size)
        assertEquals("old-1", destination.deleted[0])
        db.close()
    }
}
