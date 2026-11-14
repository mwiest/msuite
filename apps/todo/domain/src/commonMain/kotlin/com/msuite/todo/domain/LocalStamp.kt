package com.msuite.todo.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class DayHalf { AM, PM }

/** An instant as shown: its date in a time zone, the time there, and on a 12 h clock the [half] of the day. */
data class LocalStamp(val date: LocalDate, val time: String, val half: DayHalf?)

private const val HALF_DAY = 12

/** [is24Hour] follows the device setting: `HH:mm`, else `h:mm` with its [DayHalf]. */
fun Instant.toLocalStamp(zone: TimeZone, is24Hour: Boolean): LocalStamp {
    val local = toLocalDateTime(zone)
    val minute = local.minute.toString().padStart(2, '0')
    if (is24Hour) return LocalStamp(local.date, "${local.hour.toString().padStart(2, '0')}:$minute", half = null)
    val hour = (local.hour % HALF_DAY).takeIf { it != 0 } ?: HALF_DAY
    return LocalStamp(local.date, "$hour:$minute", if (local.hour < HALF_DAY) DayHalf.AM else DayHalf.PM)
}
