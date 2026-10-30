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
        adoptTwins(dir)
        val files = store.list(dir)
        log.i { "list() dir=$dir, found ${files.size} files: $files" }

        // Filter out .tmp_ files (in-flight writes from atomic write pattern)
        val filtered = files.filter { !it.startsWith(".tmp_") }
        if (filtered.size != files.size) {
            log.i { "list() filtered out ${files.size - filtered.size} .tmp_ files" }
        }

        return if (dir.isEmpty()) filtered else filtered.map { "$dir/$it" }
    }

    /**
     * Moves the files of a sync client's twin of [dir] (`dir_<digits>`, `dir (N)`) into [dir] and
     * deletes the twin. Two devices creating [dir] at once get one each (Dropbox renames the
     * loser), and a device keeps writing into its twin until it sees the rename.
     */
    private suspend fun adoptTwins(dir: String) {
        if (dir.isEmpty()) return
        val parent = directory(dir)
        val twin = Regex("""^${Regex.escape(filename(dir))}(?:_\d+| \(\d+\))$""")
        for (name in store.list(parent).filter(twin::matches)) {
            adopt(if (parent.isEmpty()) name else "$parent/$name", dir)
        }
    }

    private suspend fun adopt(twinDir: String, dir: String) {
        for (file in store.list(twinDir)) {
            if (!file.startsWith(".tmp_")) store.read("$twinDir/$file")?.let { put("$dir/$file", it) }
            store.delete("$twinDir/$file")
        }
        store.delete(twinDir)
        log.w { "adopted twin folder $twinDir into $dir" }
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
