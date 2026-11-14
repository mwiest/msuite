package com.msuite.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.msuite.todo.domain.DayHalf
import com.msuite.todo.domain.toLocalStamp
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.sync_time
import com.msuite.todo.resources.time_24h
import com.msuite.todo.resources.time_am
import com.msuite.todo.resources.time_pm
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** Whether the device shows a 24 h clock; `TodoApp` provides the shell's answer. */
val LocalIs24HourTime = staticCompositionLocalOf<() -> Boolean> { { true } }

/** [instant] in this device's time zone and clock setting, as the language writes a date and time. */
@Composable
fun syncTimeText(instant: Instant): String {
    val stamp = instant.toLocalStamp(TimeZone.currentSystemDefault(), LocalIs24HourTime.current())
    val time = when (stamp.half) {
        null -> Res.string.time_24h
        DayHalf.AM -> Res.string.time_am
        DayHalf.PM -> Res.string.time_pm
    }
    return stringResource(Res.string.sync_time, dueDateText(stamp.date), stringResource(time, stamp.time))
}
