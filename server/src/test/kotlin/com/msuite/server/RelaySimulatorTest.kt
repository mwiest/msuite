package com.msuite.server

import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.Hlc
import com.msuite.core.relay.RelayPlug
import com.msuite.core.sync.BatchType
import com.msuite.core.sync.ObjectKeyPrefix
import com.msuite.core.sync.ciphertextHash
import com.msuite.core.sync.testing.Cluster
import com.msuite.core.sync.testing.EnvelopePlugStore
import com.msuite.core.sync.testing.MemoryState
import com.msuite.core.sync.testing.PlugStore
import com.msuite.core.sync.testing.ReplicaState
import com.msuite.core.sync.testing.SimBackend
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Files
import java.nio.file.Path
import kotlin.uuid.Uuid

private const val APP = "todo"
private const val GROUP = "0123456789ABCDEFGHIJKLMNOP"
private val ROOT = ByteArray(32) { it.toByte() }

private suspend fun keyFor(bytes: ByteArray): String =
    ObjectKeyPrefix(APP, GROUP, BatchType.Delta, DeviceId(1), Hlc.of(1, 0), Hlc.of(2, 0))
        .withHash(ciphertextHash(bytes))
        .text()

private fun write(cluster: Cluster, device: Int, record: Long, value: String) {
    cluster.devices[device].state.write(CellKey(Uuid.fromLongs(1, record), FieldId(10)), value.encodeToByteArray())
}

private fun runLayer2(server: RelayTestServer, http: HttpClient, backend: SimBackend) {
    val plug = RelayPlug(server.url, APP, ROOT, http)
    Cluster(listOf(0L, 10L), seed = 41, backend = backend, store = PlugStore(plug, APP, ROOT), snapshots = true)
        .use { cluster ->
            write(cluster, 0, 1, "a")
            cluster.push(cluster.devices[0])
            cluster.pullAll(cluster.devices[1])

            write(cluster, 1, 1, "b")
            write(cluster, 1, 2, "c")
            cluster.push(cluster.devices[1])
            cluster.pullAll(cluster.devices[0])

            write(cluster, 0, 2, "d")
            cluster.quiesce()
            cluster.checkIdentical()
        }
}

private fun runLayer3(server: RelayTestServer, http: HttpClient, backend: SimBackend) {
    val plug = RelayPlug(server.url, APP, ROOT, http)
    val store = EnvelopePlugStore(plug, APP, { if (it == KEY_GEN_V1) ROOT else null })
    Cluster(listOf(0L, 10L), seed = 42, backend = backend, store = store, snapshots = true).use { cluster ->
        write(cluster, 0, 1, "a")
        cluster.push(cluster.devices[0])
        cluster.pullAll(cluster.devices[1])

        write(cluster, 1, 1, "b")
        write(cluster, 1, 2, "c")
        cluster.push(cluster.devices[1])
        cluster.pullAll(cluster.devices[0])

        write(cluster, 0, 2, "d")
        cluster.quiesce()
        cluster.checkIdentical()
    }
}

class RelaySimulatorTest : StringSpec({
    lateinit var dataDir: Path

    beforeTest {
        dataDir = Files.createTempDirectory("relay-sim-test")
    }

    afterTest {
        dataDir.toFile().deleteRecursively()
    }

    "invariant 12: concurrent puts of the same and distinct keys" {
        val http = HttpClient(OkHttp) { expectSuccess = false }
        try {
            RelayTestServer(dataDir).use { server ->
                val plug = RelayPlug(server.url, APP, ROOT, http)
                val same = keyFor(byteArrayOf(1))
                val distinct = (2..CONCURRENCY).map { byteArrayOf(it.toByte()) }.map { it to keyFor(it) }

                coroutineScope {
                    (1..CONCURRENCY).map {
                        async(Dispatchers.IO) { plug.put(same, byteArrayOf(1)) }
                    }.awaitAll()
                }
                plug.list("$APP/$GROUP/") shouldBe listOf(same)

                coroutineScope {
                    distinct.map { (bytes, key) ->
                        async(Dispatchers.IO) { plug.put(key, bytes) }
                    }.awaitAll()
                }
                plug.list("$APP/$GROUP/").sorted() shouldBe (listOf(same) + distinct.map { it.second }).sorted()
                plug.get(same)?.toList() shouldBe listOf<Byte>(1)
            }
        } finally {
            http.close()
        }
    }

    "Layer 2 converges in memory through the relay" {
        val http = HttpClient(OkHttp) { expectSuccess = false }
        try {
            RelayTestServer(dataDir).use { server -> runLayer2(server, http, ::MemoryState) }
        } finally {
            http.close()
        }
    }

    "Layer 2 converges through Replica and SQLite through the relay" {
        val http = HttpClient(OkHttp) { expectSuccess = false }
        try {
            RelayTestServer(dataDir).use { server -> runLayer2(server, http, ::ReplicaState) }
        } finally {
            http.close()
        }
    }

    "Layer 3 converges in memory through the relay" {
        val http = HttpClient(OkHttp) { expectSuccess = false }
        try {
            RelayTestServer(dataDir).use { server -> runLayer3(server, http, ::MemoryState) }
        } finally {
            http.close()
        }
    }

    "Layer 3 converges through Replica and SQLite through the relay" {
        val http = HttpClient(OkHttp) { expectSuccess = false }
        try {
            RelayTestServer(dataDir).use { server -> runLayer3(server, http, ::ReplicaState) }
        } finally {
            http.close()
        }
    }
})

private const val CONCURRENCY = 8
