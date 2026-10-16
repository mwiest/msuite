package com.msuite.todo

/**
 * Picking a file to write or read a snapshot. This is the only platform-specific part of the manual
 * plug, so it stays behind this seam (AGENTS.md); the Android shell implements it with SAF. Neither
 * call throws: a cancel or a failure is reported as `false`/`null`.
 */
interface SnapshotFiles {
    /** Asks where to save [bytes]; `false` if the user cancels or the write fails. */
    suspend fun export(bytes: ByteArray): Boolean

    /** The bytes of a file the user picks; `null` if they cancel or the read fails. */
    suspend fun import(): ByteArray?
}
