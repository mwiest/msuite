package com.msuite.todo.domain

/**
 * Picking a file to write or read a snapshot: the only platform-specific part of the manual plug,
 * so it stays behind this seam (AGENTS.md); the Android shell implements it with SAF. Neither call
 * throws: a cancel or a failure is an [Outcome].
 */
interface SnapshotFiles {
    /**
     * Asks where to save, then writes what [bytes] yields. The bytes are asked for after the
     * destination is picked, so the snapshot is taken at write time, not before the user has had
     * the picker open.
     */
    suspend fun export(bytes: suspend () -> ByteArray): Outcome

    /** The bytes of a file the user picks. */
    suspend fun import(): Outcome

    /** How a file operation ended: `Read` only from [import], `Written` only from [export]. */
    sealed interface Outcome {
        /** The user dismissed the picker. */
        data object Cancelled : Outcome

        /** The file could not be written or read: a bad destination, or an unreadable file. */
        data object Failed : Outcome

        /** The bytes were written. */
        data object Written : Outcome

        /** The picked file's bytes, at most as much as the implementation allows to read. */
        data class Read(val bytes: ByteArray) : Outcome
    }
}
