package com.msuite.core.sync

import co.touchlab.kermit.Logger

/**
 * A [BackendPlug] that stores batches as files in a folder (decision 0042). Each batch is one file
 * under `{appId}/{groupId}/{name}`; atomic publish writes `.tmp_*` then renames.
 */
class FolderPlug(private val store: FileStore) : BackendPlug {
    private val log = Logger.withTag("FolderPlug")

    override suspend fun put(key: String, bytes: ByteArray) {
        val dir = directory(key)
        val name = filename(key)
        val finalPath = if (dir.isEmpty()) name else "$dir/$name"
        if (store.exists(finalPath)) return
        val tempPath = if (dir.isEmpty()) ".tmp_$name" else "$dir/.tmp_$name"
        store.write(tempPath, bytes)
        store.rename(tempPath, finalPath)
    }

    override suspend fun list(prefix: String): List<String> {
        val dir = directory(prefix)
        val files = store.list(dir)
        log.i { "list() dir=$dir, found ${files.size} files before filtering: $files" }
        
        // Filter out .tmp_ files (legacy from atomic write pattern, kept for backward compatibility)
        val tmpFiles = files.filter { it.startsWith(".tmp_") }
        if (tmpFiles.isNotEmpty()) {
            log.w { "list() found ${tmpFiles.size} legacy .tmp_ files (should be cleaned up): $tmpFiles" }
        }
        
        val filtered = files.filter { !it.startsWith(".tmp_") }
        log.i { "list() after filtering .tmp_: ${filtered.size} files: $filtered" }
        return if (dir.isEmpty()) filtered else filtered.map { "$dir/$it" }
    }

    override suspend fun get(key: String): ByteArray? = store.read(key)

    override suspend fun delete(key: String) {
        store.delete(key)
    }

    private fun directory(keyOrPrefix: String): String {
        val lastSlash = keyOrPrefix.lastIndexOf('/')
        return if (lastSlash >= 0) keyOrPrefix.substring(0, lastSlash) else ""
    }

    private fun filename(key: String): String {
        val lastSlash = key.lastIndexOf('/')
        return if (lastSlash >= 0) key.substring(lastSlash + 1) else key
    }
}
