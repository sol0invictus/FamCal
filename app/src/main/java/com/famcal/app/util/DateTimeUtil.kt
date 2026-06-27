package com.famcal.app.util

import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/** Conversions between Firebase [Timestamp] and java.time types (in the device zone). */

fun Timestamp.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(toDate().toInstant(), ZoneId.systemDefault())

fun Timestamp.toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()

fun LocalDateTime.toTimestamp(): Timestamp =
    Timestamp(Date.from(atZone(ZoneId.systemDefault()).toInstant()))

fun LocalDate.toTimestamp(): Timestamp = atStartOfDay().toTimestamp()

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val monthTitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

fun LocalDateTime.formatTime(): String = format(timeFormatter)
fun LocalDate.formatDate(): String = format(dateFormatter)
fun java.time.YearMonth.formatTitle(): String = format(monthTitleFormatter)
