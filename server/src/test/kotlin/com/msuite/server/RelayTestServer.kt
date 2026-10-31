package com.msuite.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/** A real CIO relay on a free local port, for tests that need HTTP rather than the test client. */
class RelayTestServer(dataDir: Path, configure: (RelayConfig) -> RelayConfig = { it }) : AutoCloseable {
    private val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
        relayModule(configure(RelayConfig(dataDir = dataDir)))
    }.start(wait = false)

    val url: String = "http://127.0.0.1:${runBlocking { server.engine.resolvedConnectors().first().port }}"

    override fun close() {
        server.stop(0, 0)
    }
}
