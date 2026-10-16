package com.msuite.todo

import com.msuite.core.model.MalformedBatch
import com.msuite.core.sync.IngestResult
import com.msuite.core.sync.ManualSync

/** What an export or import did, for the settings screen's message. */
enum class SyncResult { Exported, Imported, Cancelled, Deferred, TooNew, Failed }

/**
 * The manual plug wired to a file picker (decision 0066). Export writes the whole state to a file
 * the user picks; import merges a file in. The export is plaintext, so this is a proof of concept
 * until step 6 encrypts it.
 */
class SyncActions(private val sync: ManualSync, private val files: SnapshotFiles) {
    suspend fun export(): SyncResult = if (files.export(sync.export())) SyncResult.Exported else SyncResult.Cancelled

    suspend fun import(): SyncResult {
        val bytes = files.import() ?: return SyncResult.Cancelled
        return try {
            when (sync.import(bytes)) {
                IngestResult.Deferred -> SyncResult.Deferred
                IngestResult.TooNew -> SyncResult.TooNew
                is IngestResult.Merged -> SyncResult.Imported
            }
        } catch (ignored: MalformedBatch) {
            SyncResult.Failed
        }
    }
}
