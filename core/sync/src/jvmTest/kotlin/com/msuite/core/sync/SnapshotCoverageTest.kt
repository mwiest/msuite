package com.msuite.core.sync

import com.msuite.core.model.DeviceId
import com.msuite.core.model.Hlc
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private const val APP = "todo"
private const val GROUP = "0123456789ABCDEFGHIJKLMNOP"
private val HASH = "0".repeat(52)

private fun key(type: BatchType, writer: Long = 1, lo: Long, hi: Long): String =
    ObjectKeyPrefix(APP, GROUP, type, DeviceId(writer), Hlc.of(lo, 0), Hlc.of(hi, 0))
        .withHash(HASH)
        .text()

class SnapshotCoverageTest : StringSpec({
    val snapshot = key(BatchType.Snapshot, lo = 10, hi = 20)

    "a delta inside the same writer's snapshot span is covered" {
        isCoveredBySnapshot(key(BatchType.Delta, lo = 12, hi = 15), setOf(snapshot)) shouldBe true
        isCoveredBySnapshot(key(BatchType.Delta, lo = 10, hi = 20), setOf(snapshot)) shouldBe true
    }

    "a delta outside the span is not covered" {
        isCoveredBySnapshot(key(BatchType.Delta, lo = 9, hi = 12), setOf(snapshot)) shouldBe false
        isCoveredBySnapshot(key(BatchType.Delta, lo = 18, hi = 21), setOf(snapshot)) shouldBe false
    }

    "another writer's snapshot, or another delta, does not cover" {
        isCoveredBySnapshot(
            key(BatchType.Delta, writer = 2, lo = 12, hi = 15),
            setOf(snapshot),
        ) shouldBe false
        isCoveredBySnapshot(
            key(BatchType.Delta, lo = 12, hi = 15),
            setOf(key(BatchType.Delta, lo = 10, hi = 20)),
        ) shouldBe false
    }

    "a snapshot is never covered, and a non-key is ignored" {
        isCoveredBySnapshot(key(BatchType.Snapshot, lo = 12, hi = 15), setOf(snapshot)) shouldBe false
        isCoveredBySnapshot("not a key", setOf(snapshot)) shouldBe false
        isCoveredBySnapshot(key(BatchType.Delta, lo = 12, hi = 15), setOf("not a key")) shouldBe false
    }
})
