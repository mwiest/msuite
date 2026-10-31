package com.msuite.server

import com.msuite.core.model.DeviceId
import com.msuite.core.model.Hlc
import com.msuite.core.relay.RelayApi
import com.msuite.core.relay.relayToken
import com.msuite.core.sync.BatchType
import com.msuite.core.sync.ObjectKeyPrefix
import com.msuite.core.sync.ciphertextHash
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

private const val APP = "todo"
private const val GROUP = "0123456789ABCDEFGHIJKLMNOP"
private const val OTHER_GROUP = "PONMLKJIHGFEDCBA9876543210"
private val ROOT = ByteArray(32) { it.toByte() }
private val OTHER_ROOT = ByteArray(32) { (it + 1).toByte() }
private val TOKEN = runBlocking { relayToken(ROOT, APP) }
private val OTHER_TOKEN = runBlocking { relayToken(OTHER_ROOT, APP) }

private fun bytesOf(text: String): ByteArray = text.encodeToByteArray()

private suspend fun keyFor(text: String, group: String = GROUP): String {
    val bytes = bytesOf(text)
    return ObjectKeyPrefix(APP, group, BatchType.Delta, DeviceId(1), Hlc.of(1, 0), Hlc.of(2, 0))
        .withHash(ciphertextHash(bytes))
        .text()
}

private fun pathOf(key: String): String = RelayApi.keyPath(key)

private fun namespaceOf(group: String = GROUP): String = RelayApi.listingPath("$APP/$group/")

private fun relayTest(
    dataDir: Path,
    configure: (RelayConfig) -> RelayConfig = { it },
    block: suspend ApplicationTestBuilder.(RelayConfig) -> Unit,
) = testApplication {
    val config = configure(RelayConfig(dataDir = dataDir))
    application { relayModule(config) }
    block(config)
}

class RelayHttpTest : StringSpec({
    lateinit var dataDir: Path

    beforeTest {
        dataDir = Files.createTempDirectory("relay-http-test")
    }

    afterTest {
        dataDir.toFile().deleteRecursively()
    }

    "health answers 200" {
        relayTest(dataDir) {
            client.get(RelayApi.HEALTH_PATH).status shouldBe HttpStatusCode.OK
        }
    }

    "put, get, list and delete round-trip with their statuses" {
        val key = keyFor("object")
        val bytes = bytesOf("object")
        val name = key.substringAfterLast('/')

        relayTest(dataDir) {
            client.put(pathOf(key)) {
                authorize()
                setBody(bytes)
            }.status shouldBe HttpStatusCode.Created
            client.put(pathOf(key)) {
                authorize()
                setBody(bytes)
            }.status shouldBe HttpStatusCode.OK

            val fetched = client.get(pathOf(key)) { authorize() }
            fetched.status shouldBe HttpStatusCode.OK
            fetched.bodyAsBytes().toList() shouldBe bytes.toList()

            val listing = client.get(namespaceOf()) { authorize() }
            listing.status shouldBe HttpStatusCode.OK
            listing.bodyAsText() shouldBe name

            client.delete(pathOf(key)) { authorize() }.status shouldBe HttpStatusCode.OK
            client.get(pathOf(key)) { authorize() }.status shouldBe HttpStatusCode.NotFound
            client.delete(pathOf(key)) { authorize() }.status shouldBe HttpStatusCode.NotFound
        }
    }

    "a malformed name and a hash mismatch are 400" {
        val key = keyFor("expected")
        relayTest(dataDir) {
            client.put("/v1/$APP/$GROUP/notaname") {
                authorize()
                setBody(bytesOf("x"))
            }
                .status shouldBe HttpStatusCode.BadRequest
            client.put(pathOf(key)) {
                authorize()
                setBody(bytesOf("other"))
            }
                .status shouldBe HttpStatusCode.BadRequest
        }
    }

    "a missing or wrong token is 401" {
        val key = keyFor("token")
        val bytes = bytesOf("token")
        relayTest(dataDir) {
            client.put(pathOf(key)) { setBody(bytes) }.status shouldBe HttpStatusCode.Unauthorized
            client.put(pathOf(key)) {
                authorize()
                setBody(bytes)
            }.status shouldBe HttpStatusCode.Created
            client.put(pathOf(keyFor("other"))) {
                authorize(OTHER_TOKEN)
                setBody(bytesOf("other"))
            }
                .status shouldBe HttpStatusCode.Unauthorized
            client.get(pathOf(key)) { authorize(OTHER_TOKEN) }.status shouldBe HttpStatusCode.Unauthorized
            client.get(namespaceOf()) { authorize(OTHER_TOKEN) }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "an allowlist refuses registration with 403 but keeps registered namespaces usable" {
        val key = keyFor("first")
        relayTest(dataDir) {
            client.put(pathOf(key)) {
                authorize()
                setBody(bytesOf("first"))
            }.status shouldBe HttpStatusCode.Created
        }
        relayTest(dataDir, { it.copy(allowedGroups = emptySet()) }) {
            client.put(pathOf(keyFor("second"))) {
                authorize()
                setBody(bytesOf("second"))
            }
                .status shouldBe HttpStatusCode.Created
            client.put(pathOf(keyFor("blocked", OTHER_GROUP))) {
                authorize()
                setBody(bytesOf("blocked"))
            }
                .status shouldBe HttpStatusCode.Forbidden
        }
    }

    "an object over the object limit is 413" {
        val bytes = bytesOf("too-large")
        relayTest(dataDir, { it.copy(maxObjectBytes = bytes.size - 1L) }) {
            client.put(pathOf(keyFor("too-large"))) {
                authorize()
                setBody(bytes)
            }
                .status shouldBe HttpStatusCode.PayloadTooLarge
        }
    }

    "a namespace over its limit is 507" {
        val first = bytesOf("first")
        val second = bytesOf("second")
        relayTest(dataDir, { it.copy(maxNamespaceBytes = first.size.toLong()) }) {
            client.put(pathOf(keyFor("first"))) {
                authorize()
                setBody(first)
            }
                .status shouldBe HttpStatusCode.Created
            client.put(pathOf(keyFor("second"))) {
                authorize()
                setBody(second)
            }
                .status shouldBe HttpStatusCode.InsufficientStorage
        }
    }

    "an unregistered namespace lists empty and reads absent" {
        val key = keyFor("absent")
        relayTest(dataDir) {
            val listing = client.get(namespaceOf())
            listing.status shouldBe HttpStatusCode.OK
            listing.bodyAsText() shouldBe ""
            client.get(pathOf(key)).status shouldBe HttpStatusCode.NotFound
            client.delete(pathOf(key)).status shouldBe HttpStatusCode.NotFound
        }
    }

    "another group's token cannot read the first group's names" {
        val key = keyFor("private")
        relayTest(dataDir) {
            client.put(pathOf(key)) {
                authorize()
                setBody(bytesOf("private"))
            }.status shouldBe HttpStatusCode.Created
            client.get(namespaceOf(OTHER_GROUP)) { authorize(OTHER_TOKEN) }.bodyAsText() shouldBe ""
            client.get(pathOf(key)) { authorize(OTHER_TOKEN) }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "the data directory holds ciphertext only under the key names" {
        val key = keyFor("ciphertext")
        val bytes = bytesOf("ciphertext")
        relayTest(dataDir) {
            client.put(pathOf(key)) {
                authorize()
                setBody(bytes)
            }.status shouldBe HttpStatusCode.Created
        }
        val stored = dataDir.resolve(APP).resolve(GROUP).resolve(key.substringAfterLast('/'))
        Files.readAllBytes(stored).toList() shouldBe bytes.toList()
        Files.readAllBytes(dataDir.resolve(APP).resolve(GROUP).resolve(".token")).size shouldBe 32
        Files.walk(dataDir).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .map { it.fileName.toString() }
                .filter { it != ".token" }
                .toList() shouldBe listOf(key.substringAfterLast('/'))
        }
    }
})

private fun io.ktor.client.request.HttpRequestBuilder.authorize(token: String = TOKEN) {
    header(RelayApi.AUTHORIZATION_HEADER, RelayApi.authorization(token))
}
