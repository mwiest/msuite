package com.msuite.core.relay

import com.msuite.core.model.DeviceId
import com.msuite.core.model.Hlc
import com.msuite.core.sync.BatchType
import com.msuite.core.sync.ObjectKeyPrefix
import com.msuite.core.sync.ProbeKey
import com.msuite.core.sync.groupId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch

private val ROOT = ByteArray(32) { it.toByte() }
private val OTHER_ROOT = ByteArray(32) { (it + 1).toByte() }
private const val GROUP = "0123456789ABCDEFGHIJKLMNOP"
private val HASH = "0".repeat(52)

private fun key(type: BatchType = BatchType.Delta): String =
    ObjectKeyPrefix("todo", GROUP, type, DeviceId(1), Hlc.of(1, 0), Hlc.of(2, 0))
        .withHash(HASH)
        .text()

private val KEY = key()
private val NAME = KEY.substringAfterLast('/')

class RelayApiTest : StringSpec({
    "the relay token is deterministic, app-bound, and not the group id" {
        val token = relayToken(ROOT, "todo")
        token shouldBe relayToken(ROOT, "todo")
        token shouldNotBe relayToken(ROOT, "notes")
        token shouldNotBe groupId(ROOT)
        token shouldNotBe relayToken(OTHER_ROOT, "todo")
        token shouldMatch Regex("[0-9A-V]{52}")
    }

    "an object path round-trips to its key" {
        val path = RelayApi.keyPath(KEY)
        path shouldBe "/v1/$KEY"
        RelayApi.parsePath(path) shouldBe RelayPath("todo", GROUP, NAME)
        RelayApi.parsePath(path)?.key shouldBe KEY
    }

    "a listing path round-trips to its namespace" {
        val namespace = "todo/$GROUP/"
        val path = RelayApi.listingPath(namespace)
        path shouldBe "/v1/todo/$GROUP/"
        RelayApi.parsePath(path) shouldBe RelayPath("todo", GROUP, null)
        RelayApi.parsePath(path)?.namespace shouldBe namespace
        RelayApi.listingPath("todo/$GROUP") shouldBe path
    }

    "a listing body turns names into keys and tolerates blank lines" {
        val other = key(BatchType.Snapshot)
        RelayApi.parseListing("todo/$GROUP/", "$NAME\r\n\n${other.substringAfterLast('/')}\n") shouldContainExactly
            listOf(KEY, other)
        RelayApi.parseListing("todo/$GROUP/", "") shouldBe emptyList()
    }

    "names with a slash, a parent segment, or no key grammar are rejected" {
        shouldThrow<IllegalArgumentException> { RelayApi.keyPath("todo/$GROUP/$NAME/extra") }
        shouldThrow<IllegalArgumentException> { RelayApi.keyPath("todo/$GROUP/..") }
        shouldThrow<IllegalArgumentException> { RelayApi.keyPath("todo/$GROUP/notaname") }
        RelayApi.isValidKey("todo/$GROUP/$NAME") shouldBe true
        RelayApi.isValidKey("todo/$GROUP/..") shouldBe false
    }

    "a probe name is a valid object name besides the 0029 keys" {
        val probe = ProbeKey.random("todo", GROUP)
        RelayApi.isValidKey(probe) shouldBe true
        RelayApi.keyPath(probe) shouldBe "/v1/$probe"
        RelayApi.parseListing("todo/$GROUP/", probe.substringAfterLast('/')) shouldContainExactly listOf(probe)
        RelayApi.isValidKey("todo/$GROUP/probe_xyz") shouldBe false
    }

    "parsePath rejects another version and missing segments" {
        RelayApi.parsePath("/v2/$KEY").shouldBeNull()
        RelayApi.parsePath("/v1/todo/$GROUP") shouldBe RelayPath("todo", GROUP, null)
        RelayApi.parsePath("/v1/todo/$GROUP/") shouldBe RelayPath("todo", GROUP, null)
        RelayApi.parsePath("/v1/todo").shouldBeNull()
        RelayApi.parsePath("/v1/$KEY/extra").shouldBeNull()
        RelayApi.parsePath("/health").shouldBeNull()
    }

    "the authorization header carries a bearer token" {
        RelayApi.authorization("ABC") shouldBe "Bearer ABC"
        RelayApi.tokenFromAuthorization("Bearer ABC") shouldBe "ABC"
        RelayApi.tokenFromAuthorization(null).shouldBeNull()
        RelayApi.tokenFromAuthorization("Basic ABC").shouldBeNull()
    }

    "the status codes are the wire contract" {
        listOf(
            RelayStatus.CREATED,
            RelayStatus.OK,
            RelayStatus.BAD_REQUEST,
            RelayStatus.UNAUTHORIZED,
            RelayStatus.FORBIDDEN,
            RelayStatus.NOT_FOUND,
            RelayStatus.TOO_LARGE,
            RelayStatus.INSUFFICIENT_STORAGE,
        ) shouldContainExactly listOf(201, 200, 400, 401, 403, 404, 413, 507)
    }
})
