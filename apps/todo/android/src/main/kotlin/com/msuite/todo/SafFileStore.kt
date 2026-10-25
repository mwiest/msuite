package com.msuite.todo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.msuite.core.sync.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * SAF-backed [FileStore]: wraps [DocumentFile] and [ContentResolver] into the file-system
 * interface. The [baseUri] is the synced folder's URI (picked via SAF). Paths are relative to
 * this base. Uses persistable URI permissions to survive restarts (decision 0042).
 *
 * Temp files (paths with filenames starting with `.tmp_`) are stored in a local cache directory
 * instead of the cloud. This avoids eventual consistency issues with cloud providers like Dropbox,
 * where a file written to the cloud may not be immediately readable. The rename operation copies
 * from local temp directly to the cloud target, ensuring atomic semantics.
 */
class SafFileStore(
    private val context: Context,
    private val baseUri: Uri,
) : FileStore {

    private val contentResolver get() = context.contentResolver

    /** Local directory for temp files, mirroring the cloud path structure. */
    private val localTempDir: File
        get() = File(context.cacheDir, "sync_temp").also { it.mkdirs() }

    /** Returns true if the path's filename starts with `.tmp_`. */
    private fun isTempPath(path: String): Boolean {
        val name = path.substringAfterLast('/')
        return name.startsWith(".tmp_")
    }

    /** Returns the local File for a temp path, creating parent dirs as needed. */
    private fun localTempFile(path: String): File {
        val file = File(localTempDir, path)
        file.parentFile?.mkdirs()
        return file
    }

    private fun resolve(path: String): DocumentFile? {
        val base = if (path.isEmpty()) {
            DocumentFile.fromTreeUri(context, baseUri)
        } else {
            DocumentFile.fromTreeUri(context, baseUri) ?: return null
        }

        val segments = path.split('/')
        var current: DocumentFile? = base
        for (segment in segments) {
            if (segment.isNotEmpty()) {
                current = current?.findFile(segment)
            }
        }
        return current
    }

    private fun resolveOrCreate(path: String): DocumentFile? {
        val base = if (path.isEmpty()) {
            DocumentFile.fromTreeUri(context, baseUri)
        } else {
            DocumentFile.fromTreeUri(context, baseUri) ?: return null
        }

        val segments = path.split('/')
        var current: DocumentFile? = base
        for (segment in segments) {
            if (segment.isNotEmpty()) {
                current = current?.findFile(segment) ?: current?.createDirectory(segment)
            }
        }
        return current
    }

    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        if (isTempPath(path)) {
            // Read temp files from local cache
            val file = localTempFile(path)
            if (!file.exists()) return@withContext null
            return@withContext file.readBytes()
        }
        // Read non-temp files from cloud storage
        val file = resolve(path) ?: return@withContext null
        if (!file.isFile) return@withContext null
        try {
            contentResolver.openInputStream(file.uri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                val bytes = out.toByteArray()
                android.util.Log.d("SafFileStore", "read() path=$path, bytes=${bytes.size}")
                bytes
            }
        } catch (e: IOException) {
            throw IOException("Failed to read file at $path", e)
        }
    }

    override suspend fun write(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (isTempPath(path)) {
            // Write temp files to local cache to avoid cloud eventual consistency issues
            val file = localTempFile(path)
            file.writeBytes(bytes)
            android.util.Log.d("SafFileStore", "write() LOCAL TEMP path=$path, bytes=${bytes.size}")
            Unit
        } else {
            // Write non-temp files to cloud storage
            val lastSlash = path.lastIndexOf('/')
            val dir = if (lastSlash > 0) path.substring(0, lastSlash) else ""
            val name = if (lastSlash >= 0) path.substring(lastSlash + 1) else path

            val parent = resolveOrCreate(dir) ?: throw IOException("cannot create directory $dir")
            val existing = parent.findFile(name)
            if (existing != null && existing.isFile) {
                existing.delete()
            }
            val file = parent.createFile(MIME_TYPE, name) ?: throw IOException("cannot create file $path")
            android.util.Log.d("SafFileStore", "write() CLOUD path=$path, bytes=${bytes.size}")
            contentResolver.openOutputStream(file.uri, "w")?.use { stream ->
                stream.write(bytes)
            } ?: throw IOException("cannot open output stream for $path")
            
            // Clean up any duplicates created by Dropbox due to eventual consistency
            // Dropbox creates files like "name (1)", "name (2)" when it detects conflicts
            parent.listFiles().forEach { docFile ->
                val fileName = docFile.name
                if (fileName != null && fileName != name && fileName.startsWith(name) && 
                    fileName.matches(Regex("""^${Regex.escape(name)} \(\d+\)$"""))) {
                    android.util.Log.w("SafFileStore", "write() deleting duplicate file: $fileName")
                    docFile.delete()
                }
            }
            Unit
        }
    }

    override suspend fun list(directory: String): List<String> = withContext(Dispatchers.IO) {
        val dir = resolve(directory) ?: return@withContext emptyList()
        if (!dir.isDirectory) return@withContext emptyList()
        dir.listFiles().mapNotNull { it.name }.toList()
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        if (isTempPath(path)) {
            // Delete temp files from local cache
            val file = localTempFile(path)
            file.delete()
            return@withContext
        }
        // Delete non-temp files from cloud storage
        val file = resolve(path) ?: return@withContext
        file.delete()
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        resolveOrCreate(path) ?: throw IOException("cannot create directory $path")
        Unit
    }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        if (isTempPath(from)) {
            // Source is in local temp, copy to cloud destination
            val localFile = localTempFile(from)
            if (!localFile.exists()) {
                throw IOException("local temp file not found: $from")
            }
            val bytes = localFile.readBytes()
            
            // Write directly to cloud destination
            write(to, bytes)
            
            // Clean up local temp file
            localFile.delete()
            android.util.Log.d("SafFileStore", "rename() LOCAL→CLOUD from=$from, to=$to, bytes=${bytes.size}")
        } else {
            // Both source and destination are in cloud - try native rename, fallback to copy+delete
            val source = resolve(from) ?: throw IOException("source not found: $from")
            val lastSlash = to.lastIndexOf('/')
            val targetName = if (lastSlash >= 0) to.substring(lastSlash + 1) else to
            
            val renamed = try {
                source.renameTo(targetName)
            } catch (e: Exception) {
                android.util.Log.w("SafFileStore", "renameTo failed, falling back to copy+delete", e)
                false
            }
            
            if (!renamed) {
                // Copy to new file, delete source
                val parent = source.parentFile ?: throw IOException("cannot find parent of $from")
                val target = parent.createFile(MIME_TYPE, targetName)
                    ?: throw IOException("cannot create target file $to")
                contentResolver.openInputStream(source.uri)?.use { input ->
                    contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                        input.copyTo(output)
                    } ?: throw IOException("cannot open output stream for $to")
                } ?: throw IOException("cannot open input stream for $from")
                source.delete()
            }
            android.util.Log.d("SafFileStore", "rename() CLOUD from=$from, to=$to")
        }
        Unit
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        resolve(path) != null
    }

    private companion object {
        const val MIME_TYPE = "application/octet-stream"
        const val BUFFER_SIZE = 8192
    }
}
