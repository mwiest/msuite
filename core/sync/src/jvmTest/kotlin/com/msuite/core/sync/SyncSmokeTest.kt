package com.msuite.core.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Smoke test proving the kotest stack builds and runs on the jvm target.
 * The real property-based merge/HLC simulator (OQ-7) replaces this at step 4.
 */
class SyncSmokeTest : StringSpec({
    "kotest runs on the jvm target" {
        (1 + 1) shouldBe 2
    }
})
