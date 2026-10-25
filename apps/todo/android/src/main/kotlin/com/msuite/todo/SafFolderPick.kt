package com.msuite.todo

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.msuite.todo.domain.FolderPick
import kotlinx.coroutines.CompletableDeferred

/**
 * SAF-backed [FolderPick]: uses the system folder picker to let the user choose a sync folder.
 * The folder structure is: `<picked>/{appId}/{groupId}/{name}` (handled by FolderPlug).
 *
 * Takes persistable URI permissions so the folder remains accessible across app restarts.
 * Note: Some document providers (like Dropbox) don't support persistable permissions.
 * In that case, the user will need to re-pick the folder after each app restart.
 */
class SafFolderPick(private val activity: ComponentActivity) : FolderPick {
    private var pendingPick: CompletableDeferred<String?>? = null

    private val openTree =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Take persistable permissions so we can access the folder after restart.
                // Some providers (notably Dropbox) don't support this; if the call fails we
                // still return the URI so the current session works, but the user will need
                // to re-pick after a restart.
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    activity.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: SecurityException) {
                    android.util.Log.w("SafFolderPick", "Provider doesn't support persistable permissions", e)
                }
                pendingPick?.complete(uri.toString())
                pendingPick = null
            } else {
                pendingPick?.complete(null)
                pendingPick = null
            }
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
