package com.msuite.core.sync.testing

import com.msuite.core.sync.BackendPlug
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * The append-only contract every plug must keep (decision 0003; invariant 12 in docs/testing.md).
 * It lives here so every plug can run it: the in-memory plug now, the folder plug (step 8) and the
 * relay plug (step 9) later. The plug is built with a seed so it lists keys jumbled: nothing in
 * the contract owes or implies an order.
 */
// The numbers below are the contract's literal bytes, not calculations.
@Suppress("MagicNumber")
suspend fun assertPlugContract(plug: BackendPlug) {
    plug.get("missing").shouldBeNull()
    plug.list("ns/") shouldBe emptyList<String>()

    plug.put("ns/a", byteArrayOf(1))
    plug.put("ns/b", byteArrayOf(2))
    plug.put("other/c", byteArrayOf(3))
    plug.get("ns/a")?.toList() shouldBe listOf<Byte>(1)
    plug.get("ns/b")?.toList() shouldBe listOf<Byte>(2)
    plug.list("ns/") shouldContainExactlyInAnyOrder listOf("ns/a", "ns/b")
    plug.list("other/") shouldContainExactlyInAnyOrder listOf("other/c")

    // Create-only: an existing key is never clobbered.
    plug.put("ns/a", byteArrayOf(9))
    plug.get("ns/a")?.toList() shouldBe listOf<Byte>(1)

    // A plug owns its bytes: neither the array passed in nor the one returned can change the store.
    val passed = byteArrayOf(4)
    plug.put("ns/d", passed)
    passed[0] = 5
    plug.get("ns/d")?.toList() shouldBe listOf<Byte>(4)
    plug.get("ns/d")!![0] = 6
    plug.get("ns/d")?.toList() shouldBe listOf<Byte>(4)
}
