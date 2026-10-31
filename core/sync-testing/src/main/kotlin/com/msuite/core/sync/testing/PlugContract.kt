package com.msuite.core.sync.testing

import com.msuite.core.sync.BackendPlug
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * The append-only contract every plug must keep (decision 0003; invariant 12 in docs/testing.md).
 * It lives here so every plug can run it: the in-memory plug, the folder plug and the relay plug.
 * The plug is built with a seed so it lists keys jumbled: nothing in the contract owes or implies
 * an order.
 *
 * [key] maps the contract's logical names to backend keys, for a plug that needs a real object-key
 * grammar (the relay). [namespace] and [otherNamespace] are the prefixes [key]'s results live
 * under. [contentAddressed] is for a plug whose backend checks the key's hash, where a repeated
 * put must carry the same bytes (the relay).
 */
// The numbers below are the contract's literal bytes, not calculations.
@Suppress("MagicNumber")
suspend fun assertPlugContract(
    plug: BackendPlug,
    key: (String) -> String = { it },
    namespace: String = "ns/",
    otherNamespace: String = "other/",
    contentAddressed: Boolean = false,
) {
    val missing = key("missing")
    val first = key("ns/a")
    val second = key("ns/b")
    val other = key("other/c")
    val fourth = key("ns/d")

    plug.get(missing).shouldBeNull()
    plug.list(namespace) shouldBe emptyList<String>()

    plug.put(first, byteArrayOf(1))
    plug.put(second, byteArrayOf(2))
    plug.put(other, byteArrayOf(3))
    plug.get(first)?.toList() shouldBe listOf<Byte>(1)
    plug.get(second)?.toList() shouldBe listOf<Byte>(2)
    plug.list(namespace) shouldContainExactlyInAnyOrder listOf(first, second)
    plug.list(otherNamespace) shouldContainExactlyInAnyOrder listOf(other)

    // Create-only: an existing key is never clobbered. A content-addressed backend refuses new
    // bytes under an existing key, so it repeats the same bytes instead.
    plug.put(first, if (contentAddressed) byteArrayOf(1) else byteArrayOf(9))
    plug.get(first)?.toList() shouldBe listOf<Byte>(1)

    // A plug owns its bytes: neither the array passed in nor the one returned can change the store.
    val passed = byteArrayOf(4)
    plug.put(fourth, passed)
    passed[0] = 5
    plug.get(fourth)?.toList() shouldBe listOf<Byte>(4)
    plug.get(fourth)!![0] = 6
    plug.get(fourth)?.toList() shouldBe listOf<Byte>(4)
}
