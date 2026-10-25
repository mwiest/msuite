package com.msuite.todo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.msuite.todo.domain.FolderPick
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SAF-backed [FolderPick]: uses the system folder picker to let the user choose a sync root,
 * then creates (or reuses) an `msuite` subfolder inside it. Returns the URI of the `msuite`
 * folder so each app (todo, notes, etc.) shares the same structure:
 *
 * ```
 * <user-picked-parent>/
 *   msuite/
 *     todo/
 *       <groupId>/
 *         <batch files>
 *     notes/          ← future
 *       <groupId>/
 *         <batch files>
 * ```
 *
 * Takes persistable URI permissions on the `msuite` folder so it remains accessible across app
 * restarts.
 */
class SafFolderPick(private val activity: ComponentActivity) : FolderPick {
    private var pendingPick: CompletableDeferred<String?>? = null

    private val openTree =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Create or reuse the msuite subfolder inside the picked parent
                val msuiteUri = createMsuiteFolder(activity, uri)
                if (msuiteUri != null) {
                    // Take persistable permissions on the msuite folder
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    activity.contentResolver.takePersistableUriPermission(msuiteUri, flags)
                    pendingPick?.complete(msuiteUri.toString())
                } else {
                    pendingPick?.complete(null)
                }
            } else {
                pendingPick?.complete(null)
            }
            pendingPick = null
        }

    override suspend fun pick(): String? {
        val deferred = CompletableDeferred<String?>()
        // Cancel any previous pending pick (user rotation while picker was open)
        pendingPick?.complete(null)
        pendingPick = deferred
        openTree.launch(null)
        return deferred.await()
    }
}

/**
 * Creates an `msuite` subfolder inside the given parent URI, or returns the existing one if it
 * already exists. Returns the URI of the `msuite` folder, or null on failure.
 */
private fun createMsuiteFolder(context: Context, parentUri: Uri): Uri? {
    val parent = DocumentFile.fromTreeUri(context, parentUri)?.takeIf { it.isDirectory }
        ?: return null

    // Check if msuite folder already exists, otherwise create it
    return parent.findFile(MSUITE_FOLDER_NAME)?.takeIf { it.isDirectory }?.uri
        ?: parent.createDirectory(MSUITE_FOLDER_NAME)?.uri
}

private const val MSUITE_FOLDER_NAME = "msuite"
