package com.msuite.todo.domain

import com.msuite.core.model.MalformedBatch
import com.msuite.core.sync.IngestResult
import com.msuite.core.sync.ManualSync

/** What an export or import did, for the settings screen's message. */
enum class SyncResult { Exported, Imported, Cancelled, Deferred, TooNew, WriteFailed, Failed }

/**
 * The manual plug wired to a file picker (decision 0066): export writes the whole state to a file
 * the user picks, import merges a file in. The export is plaintext, so this is a proof of concept
 * until step 6 encrypts it. App logic, so it sits in the domain module with tests (decision 0051).
 */
class SyncActions(private val sync: ManualSync, private val files: SnapshotFiles) {
    suspend fun export(): SyncResult = when (files.export { sync.export() }) {
        SnapshotFiles.Outcome.Written -> SyncResult.Exported
        SnapshotFiles.Outcome.Cancelled -> SyncResult.Cancelled
        else -> SyncResult.WriteFailed
    }

    suspend fun import(): SyncResult = when (val outcome = files.import()) {
        SnapshotFiles.Outcome.Cancelled -> SyncResult.Cancelled

        is SnapshotFiles.Outcome.Read -> merge(outcome.bytes)

        // Import only hands back Cancelled or Read; anything else is a picker that broke its seam.
        else -> SyncResult.Failed
    }

    private suspend fun merge(bytes: ByteArray): SyncResult = try {
        when (sync.import(bytes)) {
            IngestResult.Deferred -> SyncResult.Deferred
            IngestResult.TooNew -> SyncResult.TooNew
            is IngestResult.Merged -> SyncResult.Imported
        }
    } catch (ignored: MalformedBatch) {
        SyncResult.Failed
    }
}
