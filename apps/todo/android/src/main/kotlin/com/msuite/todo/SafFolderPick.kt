package com.msuite.todo

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.msuite.todo.domain.FolderPick
import kotlinx.coroutines.CompletableDeferred

/**
 * SAF-backed [FolderPick]: uses the system folder picker to let the user choose a synced folder
 * (decision 0042). Takes persistable URI permissions so the folder remains accessible across app
 * restarts.
 */
class SafFolderPick(private val activity: ComponentActivity) : FolderPick {
    private var pendingPick: CompletableDeferred<String?>? = null

    private val openTree =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Take persistable permissions so we can access the folder after restart
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                activity.contentResolver.takePersistableUriPermission(uri, flags)
                pendingPick?.complete(uri.toString())
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
