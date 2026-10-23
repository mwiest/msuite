package com.msuite.todo

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.msuite.core.sync.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * SAF-backed [FileStore]: wraps [DocumentFile] and [ContentResolver] into the file-system
 * interface. The [baseUri] is the synced folder's URI (picked via SAF). Paths are relative to
 * this base. Uses persistable URI permissions to survive restarts (decision 0042).
 */
class SafFileStore(
    private val contentResolver: ContentResolver,
    private val baseUri: Uri,
) : FileStore {
    
    private fun resolve(path: String): DocumentFile? {
        if (path.isEmpty()) return DocumentFile.fromTreeUri(contentResolver, baseUri)
        val base = DocumentFile.fromTreeUri(contentResolver, baseUri) ?: return null
        val segments = path.split('/')
        var current = base
        for (segment in segments) {
            if (segment.isEmpty()) continue
            current = current.findFile(segment) ?: return null
        }
        return current
    }
    
    private fun resolveOrCreate(path: String): DocumentFile? {
        if (path.isEmpty()) return DocumentFile.fromTreeUri(contentResolver, baseUri)
        val base = DocumentFile.fromTreeUri(contentResolver, baseUri) ?: return null
        val segments = path.split('/')
        var current = base
        for (segment in segments) {
            if (segment.isEmpty()) continue
            current = current.findFile(segment) ?: current.createDirectory(segment) ?: return null
        }
        return current
    }
    
    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: return@withContext null
        if (!file.isFile) return@withContext null
        try {
            contentResolver.openInputStream(file.uri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } catch (e: IOException) {
            null
        }
    }
    
    override suspend fun write(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val lastSlash = path.lastIndexOf('/')
        val dir = if (lastSlash > 0) path.substring(0, lastSlash) else ""
        val name = if (lastSlash >= 0) path.substring(lastSlash + 1) else path
        
        val parent = resolveOrCreate(dir) ?: throw IOException("cannot create directory $dir")
        val existing = parent.findFile(name)
        if (existing != null && existing.isFile) {
            existing.delete()
        }
        val file = parent.createFile(MIME_TYPE, name) ?: throw IOException("cannot create file $path")
        contentResolver.openOutputStream(file.uri, "w")?.use { stream ->
            stream.write(bytes)
        } ?: throw IOException("cannot open output stream for $path")
    }
    
    override suspend fun list(directory: String): List<String> = withContext(Dispatchers.IO) {
        val dir = resolve(directory) ?: return@withContext emptyList()
        if (!dir.isDirectory) return@withContext emptyList()
        dir.listFiles().mapNotNull { it.name }.toList()
    }
    
    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: return@withContext
        file.delete()
    }
    
    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        resolveOrCreate(path) ?: throw IOException("cannot create directory $path")
    }
    
    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        val source = resolve(from) ?: throw IOException("source not found: $from")
        val lastSlash = to.lastIndexOf('/')
        val targetName = if (lastSlash >= 0) to.substring(lastSlash + 1) else to
        source.renameTo(targetName)
    }
    
    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        resolve(path) != null
    }
    
    private companion object {
        const val MIME_TYPE = "application/octet-stream"
    }
}
