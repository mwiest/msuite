package com.msuite.todo

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.msuite.todo.domain.SnapshotFiles
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * SAF-backed [SnapshotFiles]: the platform half of the manual plug. The shell builds it in
 * `onCreate`, before the activity is started, as `registerForActivityResult` requires. A rotation
 * while a picker is open drops the request (the awaiting coroutine died with the old activity);
 * the settings screen starts every operation from a fresh, unbusy state.
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

    override suspend fun export(bytes: suspend () -> ByteArray): SnapshotFiles.Outcome {
        val picked = CompletableDeferred<Uri?>()
        // A replaced request fails instead of waiting on a picker that is already gone.
        pendingExport?.complete(null)
        pendingExport = picked
        createDocument.launch(FILE_NAME)
        val uri = picked.await() ?: return SnapshotFiles.Outcome.Cancelled
        return withContext(Dispatchers.IO) {
            try {
                val out = activity.contentResolver.openOutputStream(uri)
                    ?: return@withContext SnapshotFiles.Outcome.Failed
                out.use { it.write(bytes()) }
                SnapshotFiles.Outcome.Written
            } catch (ignored: IOException) {
                SnapshotFiles.Outcome.Failed
            } catch (ignored: SecurityException) {
                SnapshotFiles.Outcome.Failed
            }
        }
    }

    override suspend fun import(): SnapshotFiles.Outcome {
        val picked = CompletableDeferred<Uri?>()
        pendingImport?.complete(null)
        pendingImport = picked
        openDocument.launch(arrayOf(MIME))
        val uri = picked.await() ?: return SnapshotFiles.Outcome.Cancelled
        return withContext(Dispatchers.IO) {
            try {
                val input = activity.contentResolver.openInputStream(uri)
                    ?: return@withContext SnapshotFiles.Outcome.Failed
                // A picked file can be anything: a video must fail as a read, not exhaust memory.
                input.use { stream ->
                    val bytes = stream.readBounded() ?: return@withContext SnapshotFiles.Outcome.Failed
                    SnapshotFiles.Outcome.Read(bytes)
                }
            } catch (ignored: IOException) {
                SnapshotFiles.Outcome.Failed
            } catch (ignored: SecurityException) {
                SnapshotFiles.Outcome.Failed
            }
        }
    }

    /** At most [MAX_SNAPSHOT_BYTES]; `null` when there is more, which no snapshot is. */
    private fun InputStream.readBounded(): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) return out.toByteArray()
            total += read
            if (total > MAX_SNAPSHOT_BYTES) return null
            out.write(buffer, 0, read)
        }
    }

    private companion object {
        const val MIME = "application/octet-stream"
        const val FILE_NAME = "msuite-todo-snapshot.msuite"
        const val MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024
        const val BUFFER_BYTES = 8 * 1024
    }
}
