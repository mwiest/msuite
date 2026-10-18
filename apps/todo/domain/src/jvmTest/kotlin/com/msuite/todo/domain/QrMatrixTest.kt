package com.msuite.todo.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class QrMatrixTest : StringSpec({
    "modules are addressed by column and row" {
        val matrix = QrMatrix(2, booleanArrayOf(true, false, false, true))

        matrix.isDark(0, 0) shouldBe true
        matrix.isDark(1, 0) shouldBe false
        matrix.isDark(0, 1) shouldBe false
        matrix.isDark(1, 1) shouldBe true
    }

    "a module count that isn't square is refused" {
        shouldThrow<IllegalArgumentException> { QrMatrix(3, BooleanArray(8)) }
    }
})
