package com.msuite.server.storage

import com.msuite.core.model.DeviceId
import com.msuite.core.model.Hlc
import com.msuite.core.relay.RelayApi
import com.msuite.core.relay.RelayPath
import com.msuite.core.relay.relayToken
import com.msuite.core.sync.BatchType
import com.msuite.core.sync.ObjectKeyPrefix
import com.msuite.core.sync.ciphertextHash
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

private const val APP = "todo"
private const val GROUP = "0123456789ABCDEFGHIJKLMNOP"
private val ROOT = ByteArray(32) { it.toByte() }
private val TOKEN = runBlocking { relayToken(ROOT, APP) }
private val NAMESPACE = RelayPath(APP, GROUP)
private const val WRONG_TOKEN = "WRONGTOKEN"

private fun bytesOf(text: String): ByteArray = text.encodeToByteArray()

private suspend fun keyFor(text: String): String {
    val bytes = bytesOf(text)
    return ObjectKeyPrefix(APP, GROUP, BatchType.Delta, DeviceId(1), Hlc.of(1, 0), Hlc.of(2, 0))
        .withHash(ciphertextHash(bytes))
        .text()
}

private fun pathOf(key: String): RelayPath = requireNotNull(RelayApi.parsePath(RelayApi.keyPath(key)))

class BlobStoreTest : StringSpec({
    lateinit var root: Path
    lateinit var store: BlobStore

    beforeTest {
        root = Files.createTempDirectory("relay-blob-test")
        store = BlobStore(root)
    }

    afterTest {
        root.toFile().deleteRecursively()
    }

    "put is create-only and stores the bytes once" {
        val key = keyFor("first")
        val path = pathOf(key)
        val bytes = bytesOf("first")

        store.put(path, TOKEN, bytes) shouldBe PutResult.Created
        store.put(path, TOKEN, bytes) shouldBe PutResult.AlreadyPresent
        store.get(path, TOKEN).bytes() shouldBe bytes
        store.list(NAMESPACE, TOKEN) shouldBe ListResult.Ok(listOf(checkNotNull(path.name)))
        store.usedBytes(NAMESPACE) shouldBe bytes.size.toLong()
    }

    "a body whose hash doesn't match its key is refused" {
        val key = keyFor("expected")

        store.put(pathOf(key), TOKEN, bytesOf("other")) shouldBe PutResult.BadRequest
        store.list(NAMESPACE, TOKEN) shouldBe ListResult.Ok(emptyList())
        store.usedBytes(NAMESPACE) shouldBe 0
    }

    "concurrent puts of one key create it exactly once" {
        val key = keyFor("same")
        val path = pathOf(key)
        val bytes = bytesOf("same")

        val results = coroutineScope {
            (1..CONCURRENCY).map { async(Dispatchers.IO) { store.put(path, TOKEN, bytes) } }.awaitAll()
        }

        results.count { it == PutResult.Created } shouldBe 1
        results.count { it == PutResult.AlreadyPresent } shouldBe CONCURRENCY - 1
        store.list(NAMESPACE, TOKEN) shouldBe ListResult.Ok(listOf(checkNotNull(path.name)))
        store.usedBytes(NAMESPACE) shouldBe bytes.size.toLong()
    }

    "concurrent puts of distinct keys all land" {
        val objects = (1..CONCURRENCY).map { keyFor("value-$it") to bytesOf("value-$it") }

        val results = coroutineScope {
            objects.map { (key, bytes) ->
                async(Dispatchers.IO) { store.put(pathOf(key), TOKEN, bytes) }
            }.awaitAll()
        }

        results.all { it == PutResult.Created } shouldBe true
        store.list(NAMESPACE, TOKEN) shouldBe ListResult.Ok(
            objects.map { checkNotNull(pathOf(it.first).name) }.sorted(),
        )
        store.usedBytes(NAMESPACE) shouldBe objects.sumOf { it.second.size.toLong() }
    }

    "two first puts race the namespace registration safely" {
        val objects = (1..CONCURRENCY).map { keyFor("race-$it") to bytesOf("race-$it") }

        val results = coroutineScope {
            objects.map { (key, bytes) ->
                async(Dispatchers.IO) { store.put(pathOf(key), TOKEN, bytes) }
            }.awaitAll()
        }

        results.all { it == PutResult.Created } shouldBe true
        store.list(NAMESPACE, TOKEN).names().size shouldBe CONCURRENCY
    }

    "a half-written temp file is never listed or counted" {
        val key = keyFor("complete")
        val path = pathOf(key)
        val bytes = bytesOf("complete")
        store.put(path, TOKEN, bytes)

        val directory = root.resolve(APP).resolve(GROUP)
        Files.write(directory.resolve(".tmp_half"), bytesOf("incomplete"))

        store.list(NAMESPACE, TOKEN) shouldBe ListResult.Ok(listOf(checkNotNull(path.name)))
        store.usedBytes(NAMESPACE) shouldBe bytes.size.toLong()
    }

    "the first put registers the namespace and another token is refused" {
        val key = keyFor("registered")
        val path = pathOf(key)
        store.put(path, TOKEN, bytesOf("registered")) shouldBe PutResult.Created

        store.put(pathOf(keyFor("other")), WRONG_TOKEN, bytesOf("other")) shouldBe PutResult.Unauthorized
        store.list(NAMESPACE, WRONG_TOKEN) shouldBe ListResult.Unauthorized
        store.get(path, WRONG_TOKEN) shouldBe GetResult.Unauthorized
        store.delete(path, WRONG_TOKEN) shouldBe DeleteResult.Unauthorized

        val tokenFile = Files.readAllBytes(root.resolve(APP).resolve(GROUP).resolve(".token"))
        tokenFile.contentEquals(TOKEN.encodeToByteArray()) shouldBe false
    }

    "an unregistered namespace lists empty, reads absent and deletes as a no-op" {
        val path = pathOf(keyFor("missing"))

        store.list(NAMESPACE, TOKEN) shouldBe ListResult.Ok(emptyList())
        store.get(path, TOKEN) shouldBe GetResult.Absent
        store.delete(path, TOKEN) shouldBe DeleteResult.Absent
    }

    "a restart re-reads the namespace sizes and keeps serving objects" {
        val first = keyFor("first")
        val second = keyFor("second")
        val firstBytes = bytesOf("first")
        val secondBytes = bytesOf("second")
        store.put(pathOf(first), TOKEN, firstBytes)
        store.put(pathOf(second), TOKEN, secondBytes)

        val restarted = BlobStore(root)
        restarted.usedBytes(NAMESPACE) shouldBe (firstBytes.size + secondBytes.size).toLong()
        restarted.list(NAMESPACE, TOKEN) shouldBe ListResult.Ok(
            listOf(checkNotNull(pathOf(first).name), checkNotNull(pathOf(second).name)).sorted(),
        )
        restarted.get(pathOf(first), TOKEN).bytes() shouldBe firstBytes

        restarted.delete(pathOf(first), TOKEN) shouldBe DeleteResult.Deleted
        restarted.usedBytes(NAMESPACE) shouldBe secondBytes.size.toLong()
        restarted.get(pathOf(first), TOKEN) shouldBe GetResult.Absent
    }

    "delete removes an object and is a no-op the second time" {
        val path = pathOf(keyFor("delete"))
        store.put(path, TOKEN, bytesOf("delete"))

        store.delete(path, TOKEN) shouldBe DeleteResult.Deleted
        store.get(path, TOKEN) shouldBe GetResult.Absent
        store.delete(path, TOKEN) shouldBe DeleteResult.Absent
        store.usedBytes(NAMESPACE) shouldBe 0
    }
})

private const val CONCURRENCY = 8

private fun GetResult.bytes(): ByteArray = (this as GetResult.Ok).bytes

private fun ListResult.names(): List<String> = (this as ListResult.Ok).names
