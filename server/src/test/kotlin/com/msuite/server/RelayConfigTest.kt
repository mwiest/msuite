package com.msuite.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class RelayConfigTest : StringSpec({
    "the defaults open registration and cap sizes" {
        val config = RelayConfig.fromEnv(emptyMap())

        config.port shouldBe 8080
        config.dataDir shouldBe Path.of("relay-data")
        config.allowedGroups shouldBe null
        config.maxObjectBytes shouldBe 16L * 1024 * 1024
        config.maxNamespaceBytes shouldBe 1024L * 1024 * 1024
        config.mayRegister("ANY") shouldBe true
    }

    "the environment sets every field" {
        val config = RelayConfig.fromEnv(
            mapOf(
                RelayConfig.PORT to "9000",
                RelayConfig.DATA to "/var/lib/msuite",
                RelayConfig.ALLOW_GROUPS to "AAA, BBB ,,",
                RelayConfig.MAX_OBJECT_BYTES to "1024",
                RelayConfig.MAX_NAMESPACE_BYTES to "2048",
            ),
        )

        config.port shouldBe 9000
        config.dataDir shouldBe Path.of("/var/lib/msuite")
        config.allowedGroups shouldBe setOf("AAA", "BBB")
        config.maxObjectBytes shouldBe 1024
        config.maxNamespaceBytes shouldBe 2048
        config.mayRegister("AAA") shouldBe true
        config.mayRegister("CCC") shouldBe false
    }

    "a host's PORT is used unless MSUITE_RELAY_PORT is set" {
        RelayConfig.fromEnv(mapOf(RelayConfig.HOST_PORT to "10000")).port shouldBe 10000
        RelayConfig.fromEnv(mapOf(RelayConfig.HOST_PORT to "10000", RelayConfig.PORT to "9000")).port shouldBe 9000
        shouldThrow<IllegalArgumentException> { RelayConfig.fromEnv(mapOf(RelayConfig.HOST_PORT to "x")) }
            .message shouldBe "PORT is not a number"
    }

    "an allowlist that names no group is rejected rather than denying everyone" {
        listOf("", " ", " , ").forEach { blank ->
            shouldThrow<IllegalArgumentException> { RelayConfig.fromEnv(mapOf(RelayConfig.ALLOW_GROUPS to blank)) }
                .message shouldBe "MSUITE_RELAY_ALLOW_GROUPS names no group; unset it to let any group register"
        }
    }

    "bad numbers are rejected with the variable named" {
        shouldThrow<IllegalArgumentException> { RelayConfig.fromEnv(mapOf(RelayConfig.PORT to "huge")) }
            .message shouldBe "MSUITE_RELAY_PORT is not a number"
        shouldThrow<IllegalArgumentException> { RelayConfig.fromEnv(mapOf(RelayConfig.PORT to "70000")) }
        shouldThrow<IllegalArgumentException> { RelayConfig.fromEnv(mapOf(RelayConfig.MAX_OBJECT_BYTES to "0")) }
        shouldThrow<IllegalArgumentException> { RelayConfig.fromEnv(mapOf(RelayConfig.MAX_NAMESPACE_BYTES to "x")) }
            .message shouldBe "MSUITE_RELAY_MAX_NAMESPACE_BYTES is not a number"
    }
})
