package com.msuite.todo.domain

import kotlinx.datetime.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/** Material's date picker speaks UTC midnight epoch millis; due dates have no time zone (decision 0052). */
fun LocalDate.toPickerMillis(): Long = toEpochDays() * MILLIS_PER_DAY

fun dueDateFromPickerMillis(millis: Long): LocalDate = LocalDate.fromEpochDays(millis.floorDiv(MILLIS_PER_DAY))
