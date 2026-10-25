package com.folhinhautil.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** The viewed month always starts on its first day, even when today is later. */
@Suppress("UNUSED_PARAMETER")
fun widgetHolidayReference(month: YearMonth, today: LocalDate): LocalDate = month.atDay(1)

fun widgetNationalHolidays(month: YearMonth, today: LocalDate,
    provider: HolidayProvider = BrazilianHolidayProvider()): List<Holiday> {
    require(month.year in FirstYear..LastYear)
    val reference = widgetHolidayReference(month, today)
    return (month.year..LastYear).asSequence().flatMap { year ->
        provider.forYear(year).filter { it.date.year == year }.sortedBy { it.date }.asSequence()
    }.filter { it.kind == HolidayKind.NATIONAL && it.date >= reference }
        .distinctBy { it.date to it.name }.take(3).toList()
}

/** The last two dates of each non-national group in the viewed month, displayed chronologically. */
fun widgetOtherDates(month: YearMonth, provider: HolidayProvider = BrazilianHolidayProvider()): List<Holiday> {
    val dates = provider.forYear(month.year).filter { YearMonth.from(it.date) == month }
    val optional = dates.filter { it.kind == HolidayKind.OPTIONAL }.sortedByDescending { it.date }.take(2)
    val other = dates.filter { it.kind != HolidayKind.NATIONAL && it.kind != HolidayKind.OPTIONAL }
        .sortedByDescending { it.date }.take(2)
    return (optional + other).sortedWith(compareBy<Holiday> { it.date }.thenBy { it.name })
}

data class MonthLegend(val national: Boolean, val other: Boolean)
fun monthLegend(month: YearMonth, holidays: List<Holiday>): MonthLegend {
    val dates = holidays.filter { YearMonth.from(it.date) == month }
    return MonthLegend(dates.any { it.kind == HolidayKind.NATIONAL }, dates.any { it.kind != HolidayKind.NATIONAL })
}
