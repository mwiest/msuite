package com.msuite.core.sync

/**
 * A minimal file-system abstraction for the folder plug (decision 0042). Pure KMP so the simulator
 * can run it on `jvm` with plain `java.io.File` and Android can wrap SAF.
 *
 * Paths are `/`-separated strings. The interface is blocking-friendly but uses `suspend` so Android
 * SAF (which uses `ContentResolver` and `DocumentFile`) stays off the UI thread.
 */
interface FileStore {
    /** Read a file; returns `null` if it doesn't exist. */
    suspend fun read(path: String): ByteArray?

    /** Write bytes to a file. Creates parent directories if needed. */
    suspend fun write(path: String, bytes: ByteArray)

    /** List the filenames in a directory (not recursive). Returns an empty list if it doesn't exist. */
    suspend fun list(directory: String): List<String>

    /** Delete a file. */
    suspend fun delete(path: String)

    /** Create a directory and its parents. */
    suspend fun mkdir(path: String)

    /** Rename a file or directory. */
    suspend fun rename(from: String, to: String)

    /** Check if a file exists. */
    suspend fun exists(path: String): Boolean
}
