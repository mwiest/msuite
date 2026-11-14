package com.msuite.todo.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

private val berlin = TimeZone.of("Europe/Berlin")

private fun at(utc: String, is24Hour: Boolean) = Instant.parse(utc).toLocalStamp(berlin, is24Hour)

class LocalStampTest : StringSpec({
    "an instant reads as the local date and a zero-padded 24 h time" {
        at("2026-11-13T21:56:47.135563Z", is24Hour = true) shouldBe
            LocalStamp(LocalDate(2026, 11, 13), "22:56", half = null)
    }

    "the local date can differ from the UTC one" {
        at("2026-11-13T23:05:00Z", is24Hour = true) shouldBe LocalStamp(LocalDate(2026, 11, 14), "00:05", half = null)
    }

    "a 12 h clock counts 12, 1 to 11, with the half of the day" {
        at("2026-11-13T21:56:00Z", is24Hour = false) shouldBe
            LocalStamp(LocalDate(2026, 11, 13), "10:56", DayHalf.PM)
        at("2026-11-13T23:05:00Z", is24Hour = false) shouldBe LocalStamp(LocalDate(2026, 11, 14), "12:05", DayHalf.AM)
        at("2026-11-14T11:00:00Z", is24Hour = false) shouldBe LocalStamp(LocalDate(2026, 11, 14), "12:00", DayHalf.PM)
        at("2026-11-14T08:07:00Z", is24Hour = false) shouldBe LocalStamp(LocalDate(2026, 11, 14), "9:07", DayHalf.AM)
    }
})
