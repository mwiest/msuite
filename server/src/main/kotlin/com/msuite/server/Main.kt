package com.msuite.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

/**
 * The relay entry point: programmatic config from the environment, CIO engine, filesystem blobs
 * (decision 0017). TLS is the host's or a reverse proxy's job; the relay speaks plain HTTP.
 */
fun main() {
    val config = RelayConfig.fromEnv()
    embeddedServer(CIO, host = "0.0.0.0", port = config.port) {
        relayModule(config)
    }.start(wait = true)
}
