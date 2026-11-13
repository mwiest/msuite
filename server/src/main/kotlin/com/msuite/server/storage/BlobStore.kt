package com.msuite.server.storage

import com.msuite.core.crypto.sha256
import com.msuite.core.relay.RelayApi
import com.msuite.core.relay.RelayPath
import com.msuite.core.sync.ObjectKey
import com.msuite.core.sync.ProbeKey
import com.msuite.core.sync.ciphertextHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/** The outcome of a create-only publish (decision 0003). */
sealed interface PutResult {
    /** A new object was stored. */
    data object Created : PutResult

    /** The key already exists; its bytes are untouched. */
    data object AlreadyPresent : PutResult

    /** The name is not a 0029 key, or the body's SHA-256 doesn't match the key's hash. */
    data object BadRequest : PutResult

    /** The namespace is registered to another token. */
    data object Unauthorized : PutResult

    /** Registering a new namespace is not allowed by the relay's configuration. */
    data object Forbidden : PutResult

    /** The object exceeds the relay's per-object limit. */
    data object TooLarge : PutResult

    /** The namespace would exceed the relay's size limit. */
    data object InsufficientStorage : PutResult
}

/** The operator's size limits; [Long.MAX_VALUE] means unlimited. */
data class BlobLimits(
    val maxObjectBytes: Long = Long.MAX_VALUE,
    val maxNamespaceBytes: Long = Long.MAX_VALUE,
)

/** A namespace listing; an unregistered namespace lists empty rather than failing. */
sealed interface ListResult {
    data class Ok(val names: List<String>) : ListResult

    data object Unauthorized : ListResult
}

/** One object fetch; an unregistered namespace and a missing key are both [Absent]. */
sealed interface GetResult {
    data class Ok(val bytes: ByteArray) : GetResult

    data object Absent : GetResult

    data object Unauthorized : GetResult
}

/** One object delete; deleting an absent key is a no-op (decision 0003). */
sealed interface DeleteResult {
    data object Deleted : DeleteResult

    data object Absent : DeleteResult

    data object Unauthorized : DeleteResult
}

/**
 * The relay's filesystem store (decision 0017): one file per object under
 * `{dataDir}/{appId}/{groupId}/{name}`, plus the namespace's `.token` (decision 0030). Publishes are
 * create-only through a temp file and a hard link, so a listed name always points at complete
 * bytes. Sizes are counted per namespace, scanned once at start and kept in memory.
 */
class BlobStore(private val root: Path, private val io: CoroutineContext = Dispatchers.IO) {
    private val sizes = ConcurrentHashMap<String, Long>()

    init {
        Files.createDirectories(root)
        purgeTemps(root)
        scan(root, sizes)
    }

    /** Publishes [bytes] under [path], registering the namespace on its first put with [token]. */
    suspend fun put(
        path: RelayPath,
        token: String,
        bytes: ByteArray,
        limits: BlobLimits = BlobLimits(),
        mayRegister: Boolean = true,
    ): PutResult = withContext(io) {
        val name = path.name ?: return@withContext PutResult.BadRequest
        if (!isWellFormed(path, name, bytes)) return@withContext PutResult.BadRequest
        if (bytes.size > limits.maxObjectBytes) return@withContext PutResult.TooLarge

        when (access(path, token)) {
            Access.Denied -> return@withContext PutResult.Unauthorized

            Access.Unregistered -> {
                if (!mayRegister) return@withContext PutResult.Forbidden
                if (bytes.size > limits.maxNamespaceBytes) return@withContext PutResult.InsufficientStorage
                if (!registerOrCheck(path, token)) return@withContext PutResult.Unauthorized
            }

            Access.Authorized -> Unit
        }

        val directory = namespaceDirectory(path)
        val finalPath = directory.resolve(name)
        if (Files.exists(finalPath)) return@withContext PutResult.AlreadyPresent
        if (usedBytes(path) + bytes.size > limits.maxNamespaceBytes) return@withContext PutResult.InsufficientStorage

        val temp = Files.createTempFile(directory, TEMP_PREFIX, "")
        try {
            Files.write(temp, bytes)
            Files.createLink(finalPath, temp)
            sizes.merge(path.namespace, bytes.size.toLong(), Long::plus)
            PutResult.Created
        } catch (ignored: FileAlreadyExistsException) {
            PutResult.AlreadyPresent
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    /** A 0029 key whose hash is the body's, or a probe name, which takes any body. */
    private suspend fun isWellFormed(path: RelayPath, name: String, bytes: ByteArray): Boolean =
        RelayApi.isValidKey(path.key) &&
            (ProbeKey.isProbeName(name) || ciphertextHash(bytes) == ObjectKey.parse(path.key).hash)

    /** The names under [path]'s namespace, without the namespace prefix; unregistered lists empty. */
    suspend fun list(path: RelayPath, token: String): ListResult = withContext(io) {
        if (path.name != null || !isValidNamespace(path)) return@withContext ListResult.Ok(emptyList())
        when (access(path, token)) {
            Access.Unregistered -> ListResult.Ok(emptyList())
            Access.Denied -> ListResult.Unauthorized
            Access.Authorized -> ListResult.Ok(names(namespaceDirectory(path)))
        }
    }

    /** The bytes under [path], or [GetResult.Absent]; unregistered namespaces are absent. */
    suspend fun get(path: RelayPath, token: String): GetResult = withContext(io) {
        val name = path.name
        if (name == null || !RelayApi.isValidKey(path.key)) return@withContext GetResult.Absent
        when (access(path, token)) {
            Access.Unregistered -> GetResult.Absent

            Access.Denied -> GetResult.Unauthorized

            Access.Authorized -> {
                val file = namespaceDirectory(path).resolve(name)
                if (isObject(file)) GetResult.Ok(Files.readAllBytes(file)) else GetResult.Absent
            }
        }
    }

    /** Deletes [path]'s object; deleting an absent key or an unregistered namespace is a no-op. */
    suspend fun delete(path: RelayPath, token: String): DeleteResult = withContext(io) {
        val name = path.name
        if (name == null || !RelayApi.isValidKey(path.key)) return@withContext DeleteResult.Absent
        when (access(path, token)) {
            Access.Unregistered -> DeleteResult.Absent

            Access.Denied -> DeleteResult.Unauthorized

            Access.Authorized -> {
                val file = namespaceDirectory(path).resolve(name)
                if (!isObject(file)) return@withContext DeleteResult.Absent
                val size = Files.size(file)
                Files.delete(file)
                sizes.computeIfPresent(path.namespace) { _, total -> (total - size).coerceAtLeast(0) }
                DeleteResult.Deleted
            }
        }
    }

    /** The object bytes currently stored for [path]'s namespace; the relay's limit checks use it. */
    fun usedBytes(path: RelayPath): Long = sizes[path.namespace] ?: 0L

    private suspend fun registerOrCheck(path: RelayPath, token: String): Boolean {
        val directory = namespaceDirectory(path)
        Files.createDirectories(directory)
        val tokenFile = directory.resolve(TOKEN_FILE)
        val hash = sha256(token.encodeToByteArray())
        if (Files.isRegularFile(tokenFile)) return Files.readAllBytes(tokenFile).contentEquals(hash)

        // Link the complete hash into place, like an object: CREATE_NEW can expose an empty file.
        val temp = Files.createTempFile(directory, TEMP_PREFIX, "")
        try {
            Files.write(temp, hash)
            return try {
                Files.createLink(tokenFile, temp)
                true
            } catch (ignored: FileAlreadyExistsException) {
                Files.readAllBytes(tokenFile).contentEquals(hash)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private suspend fun access(path: RelayPath, token: String): Access {
        val tokenFile = namespaceDirectory(path).resolve(TOKEN_FILE)
        if (!Files.isRegularFile(tokenFile)) return Access.Unregistered
        val expected = Files.readAllBytes(tokenFile)
        return if (expected.contentEquals(sha256(token.encodeToByteArray()))) Access.Authorized else Access.Denied
    }

    private fun namespaceDirectory(path: RelayPath): Path = root.resolve(path.appId).resolve(path.groupId)

    private fun isValidNamespace(path: RelayPath): Boolean = path.name == null && runCatching {
        RelayApi.listingPath(path.namespace)
    }.isSuccess

    private fun names(directory: Path): List<String> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { entries ->
            entries.map { it.fileName.toString() }.filter { !it.startsWith(".") }.sorted().toList()
        }
    }

    private enum class Access { Unregistered, Authorized, Denied }

    private companion object {
        const val TOKEN_FILE = ".token"
        const val TEMP_PREFIX = ".tmp_"

        fun isObject(path: Path): Boolean = Files.isRegularFile(path) && !path.fileName.toString().startsWith(".")

        /** A publish that died between write and link leaves a temp file; startup owns the directory. */
        fun purgeTemps(root: Path) {
            Files.walk(root).use { entries ->
                entries
                    .filter { it.fileName.toString().startsWith(TEMP_PREFIX) }
                    .forEach(Files::deleteIfExists)
            }
        }

        fun scan(root: Path, sizes: MutableMap<String, Long>) {
            if (!Files.isDirectory(root)) return
            forEachDirectory(root) { app ->
                forEachDirectory(app) { group ->
                    val total = Files.list(group).use { files ->
                        files.filter(::isObject).mapToLong { Files.size(it) }.sum()
                    }
                    if (total > 0) sizes["${app.fileName}/${group.fileName}/"] = total
                }
            }
        }

        fun forEachDirectory(directory: Path, block: (Path) -> Unit) {
            Files.list(directory).use { entries -> entries.filter { Files.isDirectory(it) }.forEach(block) }
        }
    }
}
