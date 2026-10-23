package com.msuite.core.sync

/**
 * A [BackendPlug] that stores batches as files in a folder (decision 0042). Each batch is one file
 * under `{appId}/{groupId}/{name}`; atomic publish writes `.tmp_*` then renames.
 */
class FolderPlug(private val store: FileStore) : BackendPlug {
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
        val filtered = files.filter { !it.startsWith(".tmp_") }
        return if (dir.isEmpty()) filtered else filtered.map { "$dir/$it" }
    }

    override suspend fun get(key: String): ByteArray? = store.read(key)

    private fun directory(keyOrPrefix: String): String {
        val lastSlash = keyOrPrefix.lastIndexOf('/')
        return if (lastSlash >= 0) keyOrPrefix.substring(0, lastSlash) else ""
    }

    private fun filename(key: String): String {
        val lastSlash = key.lastIndexOf('/')
        return if (lastSlash >= 0) key.substring(lastSlash + 1) else key
    }
}
