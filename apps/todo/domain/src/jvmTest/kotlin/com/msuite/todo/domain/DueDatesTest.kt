package com.msuite.todo.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.datetime.LocalDate

class DueDatesTest : StringSpec({
    "a date is midnight UTC in picker millis" {
        LocalDate(1970, 1, 2).toPickerMillis() shouldBe 86_400_000L
    }

    "any time of a UTC day reads back as that day, also before 1970" {
        dueDateFromPickerMillis(86_400_000L + 86_399_999L) shouldBe LocalDate(1970, 1, 2)
        dueDateFromPickerMillis(-1L) shouldBe LocalDate(1969, 12, 31)
    }

    "dates round-trip through picker millis" {
        checkAll(Arb.long(-1_000_000L..1_000_000L).map(LocalDate::fromEpochDays)) {
            dueDateFromPickerMillis(it.toPickerMillis()) shouldBe it
        }
    }
})
