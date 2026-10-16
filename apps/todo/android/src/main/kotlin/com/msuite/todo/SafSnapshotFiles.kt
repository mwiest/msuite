package com.msuite.todo

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * SAF-backed [SnapshotFiles]: the platform half of the manual plug. The shell builds it in
 * `onCreate`, before the activity is started, as `registerForActivityResult` requires.
 */
class SafSnapshotFiles(private val activity: ComponentActivity) : SnapshotFiles {
    private var pendingExport: CompletableDeferred<Uri?>? = null
    private var pendingImport: CompletableDeferred<Uri?>? = null

    private val createDocument =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument(MIME)) { uri ->
            pendingExport?.complete(uri)
            pendingExport = null
        }

    private val openDocument =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pendingImport?.complete(uri)
            pendingImport = null
        }

    override suspend fun export(bytes: ByteArray): Boolean {
        val picked = CompletableDeferred<Uri?>()
        pendingExport = picked
        createDocument.launch(FILE_NAME)
        val uri = picked.await() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                val out = activity.contentResolver.openOutputStream(uri) ?: return@withContext false
                out.use { it.write(bytes) }
                true
            } catch (ignored: IOException) {
                false
            }
        }
    }

    override suspend fun import(): ByteArray? {
        val picked = CompletableDeferred<Uri?>()
        pendingImport = picked
        openDocument.launch(arrayOf(MIME))
        val uri = picked.await() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (ignored: IOException) {
                null
            }
        }
    }

    private companion object {
        const val MIME = "application/octet-stream"
        const val FILE_NAME = "msuite-todo-snapshot.msuite"
    }
}
