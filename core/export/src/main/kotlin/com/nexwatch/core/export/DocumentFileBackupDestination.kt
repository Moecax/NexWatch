package com.nexwatch.core.export

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.nexwatch.core.data.backup.BackupPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Real SAF implementation — exercised by a manual on-device pass (§Workflow), not a JVM test,
 * since DocumentFile/ContentResolver need a real Android content provider to talk to.
 */
class DocumentFileBackupDestination @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: BackupPrefs,
) : BackupDestination {

    private fun folder(): DocumentFile? {
        val uriString = runBlocking { prefs.settings.first() }.folderUri ?: return null
        return DocumentFile.fromTreeUri(context, uriString.toUri())
    }

    override fun isConfigured(): Boolean = folder()?.exists() == true

    override fun createFile(name: String): OutputStream? {
        val file = folder()?.createFile("application/zip", name) ?: return null
        return context.contentResolver.openOutputStream(file.uri)
    }

    override fun listExistingBackups(): List<BackupFile> =
        folder()?.listFiles()?.filter { it.name?.startsWith("nexwatch-backup-") == true }
            ?.map { BackupFile(it.name.orEmpty(), it.lastModified(), it.uri.toString()) }
            .orEmpty()

    override fun delete(file: BackupFile) {
        DocumentFile.fromSingleUri(context, file.id.toUri())?.delete()
    }
}
