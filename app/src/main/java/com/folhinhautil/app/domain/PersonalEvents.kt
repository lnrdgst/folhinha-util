package com.folhinhautil.app.domain

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import java.text.Normalizer

data class PersonalEvent(val id: String, val title: String, val date: LocalDate, val annual: Boolean = false) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank() && title.length <= 120)
    }
}

data class PersonalOccurrence(val event: PersonalEvent, val date: LocalDate, val daysRemaining: Long) {
    val countdown: String get() = when (daysRemaining) {
        0L -> "É hoje!"
        1L -> "1 dia restante"
        else -> "$daysRemaining dias restantes"
    }
}

/** Annual Feb 29 occurrences use Feb 28 in non-leap years, never before the original date. */
fun nextPersonalOccurrence(event: PersonalEvent, today: LocalDate): PersonalOccurrence? {
    val date = if (!event.annual) event.date.takeIf { it >= today } ?: return null else {
        val monthDay = MonthDay.from(event.date)
        var year = maxOf(today.year, event.date.year)
        var candidate = monthDay.atYear(year)
        if (candidate < today) candidate = monthDay.atYear(++year)
        candidate
    }
    return PersonalOccurrence(event, date, ChronoUnit.DAYS.between(today, date))
}

fun upcomingPersonalEvents(events: List<PersonalEvent>, today: LocalDate): List<PersonalOccurrence> =
    events.mapNotNull { nextPersonalOccurrence(it, today) }.sortedWith(compareBy<PersonalOccurrence> { it.date }.thenBy { it.event.id })

private val PersonalDateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu", BrazilianLocale).withResolverStyle(ResolverStyle.STRICT)
fun formatPersonalDate(date: LocalDate): String = date.format(PersonalDateFormat)
fun parsePersonalDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim(), PersonalDateFormat) }.getOrNull()

fun widgetMonth(stored: String?, today: LocalDate): YearMonth = runCatching { YearMonth.parse(stored) }.getOrNull()
    ?.takeIf { it.year in FirstYear..LastYear }
    ?: YearMonth.from(today).coerceIn(YearMonth.of(FirstYear, 1), YearMonth.of(LastYear, 12))

fun moveWidgetMonth(current: YearMonth, delta: Int): YearMonth = current.plusMonths(delta.coerceIn(-1, 1).toLong())
    .coerceIn(YearMonth.of(FirstYear, 1), YearMonth.of(LastYear, 12))

fun futureNationalHolidays(today: LocalDate, provider: HolidayProvider = BrazilianHolidayProvider()): List<Holiday> =
    (maxOf(today.year, FirstYear)..minOf(today.year + 1, LastYear)).flatMap(provider::forYear)
        .filter { it.kind == HolidayKind.NATIONAL && it.date >= today }.sortedBy { it.date }

sealed interface WidgetNextEvent {
    val title: String
    data class Personal(val occurrence: PersonalOccurrence) : WidgetNextEvent { override val title get() = occurrence.event.title }
    data class Calendar(val appointment: Appointment) : WidgetNextEvent { override val title get() = appointment.title }
}

/** Presentation-only de-duplication of exact, all-day national-holiday copies from synced agendas. */
fun nextWidgetEvents(personal: List<PersonalEvent>, appointments: List<Appointment>, today: LocalDate,
    zone: ZoneId, nationalHolidays: List<Holiday>): List<WidgetNextEvent> {
    fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase(BrazilianLocale).replace(Regex("[^a-z0-9]+"), " ").trim()
    val personalNext = upcomingPersonalEvents(personal, today).take(2).map { WidgetNextEvent.Personal(it) }
    val calendarNext = upcomingAppointments(appointments, today.atStartOfDay(zone).toInstant(), zone)
        .filterNot { event -> event.allDay && nationalHolidays.any {
            it.date == event.startDate(zone) && normalized(it.name) == normalized(event.title)
        } }.take(2).map { WidgetNextEvent.Calendar(it) }
    val candidates = if (personalNext.isNotEmpty() && calendarNext.isNotEmpty())
        listOf(personalNext.first(), calendarNext.first()) else personalNext + calendarNext
    return candidates.sortedBy { when (it) {
        is WidgetNextEvent.Personal -> it.occurrence.date.atStartOfDay(zone).toInstant()
        is WidgetNextEvent.Calendar -> it.appointment.effectiveStart(zone)
    } }.take(2)
}
