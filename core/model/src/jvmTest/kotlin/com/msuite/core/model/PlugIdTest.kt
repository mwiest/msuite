package com.msuite.core.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PlugIdTest : StringSpec({
    "a plug ID is a non-blank name" {
        PlugId("folder").id shouldBe "folder"
        shouldThrow<IllegalArgumentException> { PlugId(" ") }
    }
})
