package com.msuite.core.sync.testing

import com.msuite.core.sync.FileStore
import java.io.File

/**
 * A [FileStore] backed by plain `java.io.File` in a root directory. For `jvm` tests and the
 * simulator.
 */
class PlainFileStore(root: File) : FileStore {
    private val root: File = root.apply { mkdirs() }

    private fun resolve(path: String): File {
        require(!path.contains("..")) { "path traversal: $path" }
        return File(root, path)
    }

    override suspend fun read(path: String): ByteArray? {
        val file = resolve(path)
        return if (file.exists()) file.readBytes() else null
    }

    override suspend fun write(path: String, bytes: ByteArray) {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override suspend fun list(directory: String): List<String> {
        val dir = resolve(directory)
        return if (dir.isDirectory) dir.list()?.toList() ?: emptyList() else emptyList()
    }

    override suspend fun delete(path: String) {
        resolve(path).delete()
    }

    override suspend fun mkdir(path: String) {
        resolve(path).mkdirs()
    }

    override suspend fun rename(from: String, to: String) {
        val fromFile = resolve(from)
        val toFile = resolve(to)
        toFile.parentFile?.mkdirs()
        fromFile.renameTo(toFile)
    }

    override suspend fun exists(path: String): Boolean = resolve(path).exists()
}
