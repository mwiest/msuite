package com.msuite.server

import com.msuite.core.model.DeviceId
import com.msuite.core.model.Hlc
import com.msuite.core.relay.RelayPlug
import com.msuite.core.relay.RelayRefusal
import com.msuite.core.relay.RelayRefused
import com.msuite.core.sync.BatchType
import com.msuite.core.sync.ObjectKeyPrefix
import com.msuite.core.sync.ciphertextHash
import com.msuite.core.sync.testing.assertPlugContract
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.server.testing.testApplication
import kotlinx.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

private const val APP = "todo"
private const val GROUP = "0123456789ABCDEFGHIJKLMNOP"
private const val OTHER_GROUP = "PONMLKJIHGFEDCBA9876543210"
private val ROOT = ByteArray(32) { it.toByte() }
private val OTHER_ROOT = ByteArray(32) { (it + 1).toByte() }

private suspend fun keyFor(bytes: ByteArray, group: String = GROUP): String =
    ObjectKeyPrefix(APP, group, BatchType.Delta, DeviceId(1), Hlc.of(1, 0), Hlc.of(2, 0))
        .withHash(ciphertextHash(bytes))
        .text()

private fun bytesOf(text: String): ByteArray = text.encodeToByteArray()

class RelayPlugTest : StringSpec({
    lateinit var dataDir: Path

    beforeTest {
        dataDir = Files.createTempDirectory("relay-plug-test")
    }

    afterTest {
        dataDir.toFile().deleteRecursively()
    }

    "the plug keeps the backend contract over the in-process relay" {
        testApplication {
            application { relayModule(RelayConfig(dataDir = dataDir)) }
            val keys = mapOf(
                "missing" to keyFor(byteArrayOf(7)),
                "ns/a" to keyFor(byteArrayOf(1)),
                "ns/b" to keyFor(byteArrayOf(2)),
                "other/c" to keyFor(byteArrayOf(3), OTHER_GROUP),
                "ns/d" to keyFor(byteArrayOf(4)),
            )
            val plug = RelayPlug(baseUrl = "", appId = APP, root = ROOT, client = client)

            assertPlugContract(
                plug = plug,
                key = { keys.getValue(it) },
                namespace = "$APP/$GROUP/",
                otherNamespace = "$APP/$OTHER_GROUP/",
                contentAddressed = true,
            )
        }
    }

    "401 from another root is RelayRefused" {
        val first = keyFor(bytesOf("first"))
        val second = keyFor(bytesOf("second"))
        testApplication {
            application { relayModule(RelayConfig(dataDir = dataDir)) }
            RelayPlug("", APP, ROOT, client).put(first, bytesOf("first"))
            val refused = expectThrows<RelayRefused> {
                RelayPlug("", APP, OTHER_ROOT, client).put(second, bytesOf("second"))
            }
            refused.refusal shouldBe RelayRefusal.Unauthorized
        }
    }

    "403 from the allowlist is RelayRefused" {
        testApplication {
            application { relayModule(RelayConfig(dataDir = dataDir, allowedGroups = emptySet())) }
            val refused = expectThrows<RelayRefused> {
                RelayPlug("", APP, ROOT, client).put(keyFor(bytesOf("x")), bytesOf("x"))
            }
            refused.refusal shouldBe RelayRefusal.Forbidden
        }
    }

    "413 over the object limit is RelayRefused" {
        testApplication {
            application { relayModule(RelayConfig(dataDir = dataDir, maxObjectBytes = 1)) }
            val refused = expectThrows<RelayRefused> {
                RelayPlug("", APP, ROOT, client).put(keyFor(bytesOf("too-large")), bytesOf("too-large"))
            }
            refused.refusal shouldBe RelayRefusal.ObjectTooLarge
        }
    }

    "507 over the namespace limit is RelayRefused" {
        val first = keyFor(bytesOf("a"))
        val second = keyFor(bytesOf("b"))
        testApplication {
            application { relayModule(RelayConfig(dataDir = dataDir, maxNamespaceBytes = 1)) }
            val plug = RelayPlug("", APP, ROOT, client)
            plug.put(first, bytesOf("a"))
            val refused = expectThrows<RelayRefused> { plug.put(second, bytesOf("b")) }
            refused.refusal shouldBe RelayRefusal.NamespaceFull
        }
    }

    "a transport failure is an IOException, not a refusal" {
        val port = ServerSocket(0).use { it.localPort }
        val http = HttpClient(OkHttp) { expectSuccess = false }
        try {
            val plug = RelayPlug("http://127.0.0.1:$port", APP, ROOT, http)
            val error = expectThrows<IOException> { plug.list("$APP/$GROUP/") }
            (error is RelayRefused) shouldBe false
        } finally {
            http.close()
        }
    }
})

private suspend inline fun <reified T : Throwable> expectThrows(block: suspend () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw e
    }
    error("expected ${T::class.simpleName}")
}
