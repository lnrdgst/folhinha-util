package com.folhinhautil.app.domain

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val BrazilianLocale: Locale = Locale.forLanguageTag("pt-BR")
val WeekLabels = listOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB")
const val FirstYear = 2003
const val LastYear = 2099

fun monthGrid(month: YearMonth): List<List<LocalDate?>> {
    val offset = month.atDay(1).dayOfWeek.value % 7
    val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    return List(count) { index ->
        (index - offset + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
    }.chunked(7)
}

fun LocalDate.isWeekend() = dayOfWeek == DayOfWeek.SUNDAY || dayOfWeek == DayOfWeek.SATURDAY
fun YearMonth.label(): String = format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", BrazilianLocale))
    .replaceFirstChar { it.titlecase(BrazilianLocale) }

data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int)

data class Appointment(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Instant,
    val end: Instant,
    val allDay: Boolean,
) {
    // CalendarContract stores all-day boundaries at midnight UTC, regardless of device zone.
    fun startDate(zone: ZoneId): LocalDate = begin.atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
    fun endDateExclusive(zone: ZoneId): LocalDate = end.atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
    fun effectiveStart(zone: ZoneId): Instant = if (allDay) startDate(zone).atStartOfDay(zone).toInstant() else begin
    fun effectiveEnd(zone: ZoneId): Instant = if (allDay) endDateExclusive(zone).atStartOfDay(zone).toInstant() else end
    fun occursOn(date: LocalDate, zone: ZoneId): Boolean = if (allDay) {
        date >= startDate(zone) && date < endDateExclusive(zone)
    } else {
        begin < date.plusDays(1).atStartOfDay(zone).toInstant() &&
            (end > date.atStartOfDay(zone).toInstant() || (end == begin && startDate(zone) == date))
    }
    fun description(zone: ZoneId): String {
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM", BrazilianLocale)
        val first = startDate(zone).format(dateFormat)
        return if (allDay) {
            val last = endDateExclusive(zone).minusDays(1)
            if (last > startDate(zone)) "$first a ${last.format(dateFormat)} · Dia inteiro" else "$first · Dia inteiro"
        } else {
            val timeFormat = DateTimeFormatter.ofPattern("HH:mm", BrazilianLocale)
            val endDate = end.atZone(zone).toLocalDate()
            val endLabel = (if (endDate != startDate(zone)) "${endDate.format(dateFormat)} " else "") + end.atZone(zone).format(timeFormat)
            "$first · ${begin.atZone(zone).format(timeFormat)}–$endLabel"
        }
    }
}

fun upcomingAppointments(events: List<Appointment>, now: Instant, zone: ZoneId): List<Appointment> = events
    .filter { it.effectiveEnd(zone) > now || it.effectiveStart(zone) >= now }
    .sortedWith(compareBy<Appointment> { it.effectiveStart(zone) }.thenBy { it.eventId })
