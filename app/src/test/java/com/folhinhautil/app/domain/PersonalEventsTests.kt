package com.folhinhautil.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PersonalEventsTests {
    private val today = LocalDate.of(2026, 10, 24)
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun personal(date: LocalDate, annual: Boolean = false, id: String = "one") =
        PersonalEvent(id, "Evento de validação", date, annual)

    @Test fun countdown() {
        assertEquals(64L, nextPersonalOccurrence(personal(LocalDate.of(2026, 12, 27)), today)!!.daysRemaining)
        assertEquals("É hoje!", nextPersonalOccurrence(personal(today), today)!!.countdown)
        assertEquals("1 dia restante", nextPersonalOccurrence(personal(today.plusDays(1)), today)!!.countdown)
        assertNull(nextPersonalOccurrence(personal(today.minusDays(1)), today))
    }
    @Test fun annualRollover() {
        val event = personal(LocalDate.of(2020, 10, 24), true)
        assertEquals(today, nextPersonalOccurrence(event, today)!!.date)
        assertEquals(LocalDate.of(2027, 10, 24), nextPersonalOccurrence(event, today.plusDays(1))!!.date)
        assertEquals(LocalDate.of(2028, 2, 29),
            nextPersonalOccurrence(personal(LocalDate.of(2028, 2, 29), true), today)!!.date)
    }
    @Test fun leapDayPolicy() {
        val event = personal(LocalDate.of(2024, 2, 29), true)
        assertEquals(LocalDate.of(2027, 2, 28), nextPersonalOccurrence(event, LocalDate.of(2027, 1, 1))!!.date)
        assertEquals("É hoje!", nextPersonalOccurrence(event, LocalDate.of(2027, 2, 28))!!.countdown)
        assertEquals(LocalDate.of(2028, 2, 29), nextPersonalOccurrence(event, LocalDate.of(2027, 3, 1))!!.date)
        assertEquals(1L, nextPersonalOccurrence(event, LocalDate.of(2028, 2, 28))!!.daysRemaining)
    }
    @Test fun localDateAndDaylightSaving() {
        assertEquals(1L, nextPersonalOccurrence(personal(LocalDate.of(2027, 1, 1)), LocalDate.of(2026, 12, 31))!!.daysRemaining)
        val before = LocalDate.of(2026, 3, 8)
        val ny = ZoneId.of("America/New_York")
        assertEquals(23L, Duration.between(before.atStartOfDay(ny), before.plusDays(1).atStartOfDay(ny)).toHours())
        assertEquals(1L, nextPersonalOccurrence(personal(before.plusDays(1)), before)!!.daysRemaining)
        val instant = Instant.parse("2026-10-25T01:00:00Z")
        assertEquals(1L, nextPersonalOccurrence(personal(today.plusDays(1)), instant.atZone(zone).toLocalDate())!!.daysRemaining)
        assertEquals(0L, nextPersonalOccurrence(personal(today.plusDays(1)), instant.atZone(ZoneOffset.UTC).toLocalDate())!!.daysRemaining)
    }
    @Test fun strictDates() {
        for (bad in listOf("29/02/2026", "31/04/2026", "1/1/2026", "")) assertNull(parsePersonalDate(bad))
        assertEquals(LocalDate.of(2024, 2, 29), parsePersonalDate("29/02/2024"))
        assertEquals("24/10/2026", formatPersonalDate(today))
    }
    @Test fun upcomingPersonalOrder() {
        val events = listOf(personal(today.plusDays(5), id="later"), personal(today.minusDays(1), id="past"),
            personal(today, id="now"), personal(today.minusYears(1).plusDays(2), true, "annual"))
        assertEquals(listOf("now", "annual", "later"), upcomingPersonalEvents(events, today).map { it.event.id })
    }
    @Test fun monthNavigation() {
        assertEquals(YearMonth.of(2027, 1), moveWidgetMonth(YearMonth.of(2026, 12), 1))
        assertEquals(YearMonth.of(2026, 12), moveWidgetMonth(YearMonth.of(2027, 1), -1))
        assertEquals(YearMonth.of(2026, 10), widgetMonth(null, today))
        assertEquals(YearMonth.of(2027, 1), widgetMonth("2027-01", today))
    }
    @Test fun monthBoundsAndInvalidState() {
        assertEquals(YearMonth.of(FirstYear, 1), moveWidgetMonth(YearMonth.of(FirstYear, 1), -1))
        assertEquals(YearMonth.of(LastYear, 12), moveWidgetMonth(YearMonth.of(LastYear, 12), 1))
        for (bad in listOf("invalid", "2026-13", "2002-12", "2100-01"))
            assertEquals(YearMonth.from(today), widgetMonth(bad, today))
        assertEquals(YearMonth.of(LastYear, 12), widgetMonth(null, LocalDate.of(2101, 1, 1)))
    }
    @Test fun onlyNationalHolidaysTodayAndNextYear() {
        val holidays = futureNationalHolidays(LocalDate.of(2026, 12, 25))
        assertEquals(LocalDate.of(2026, 12, 25), holidays.first().date)
        assertEquals(LocalDate.of(2027, 1, 1), holidays[1].date)
        assertTrue(holidays.all { it.kind == HolidayKind.NATIONAL })
        assertTrue(futureNationalHolidays(LocalDate.of(2099, 12, 26)).isEmpty())
    }
    @Test fun october28RemainsOptionalAndCommemorativeIsDistinct() {
        val days = BrazilianHolidayProvider().forYear(2026)
        assertEquals(HolidayKind.OPTIONAL, days.single { it.date == LocalDate.of(2026, 10, 28) }.kind)
        assertEquals(HolidayKind.COMMEMORATIVE, days.single { it.name == "Páscoa" }.kind)
        assertFalse(futureNationalHolidays(today).any { it.date == LocalDate.of(2026, 10, 28) })
    }
    private fun appointment(date: LocalDate, title: String = "Agenda de validação", allDay: Boolean = false): Appointment {
        val begin = if (allDay) date.atStartOfDay(ZoneOffset.UTC).toInstant() else date.atTime(16, 0).atZone(zone).toInstant()
        return Appointment(1, 1, title, begin, begin.plusSeconds(if (allDay) 86400 else 3600), allDay)
    }
    @Test fun reservesEachSourceAndOrders() {
        val personal = listOf(personal(today.plusDays(4)), personal(today.plusDays(5), id="two"))
        val calendar = listOf(appointment(today.plusDays(1)), appointment(today.plusDays(2)))
        val result = nextWidgetEvents(personal, calendar, today, zone, emptyList())
        assertEquals(2, result.size)
        assertTrue(result[0] is WidgetNextEvent.Calendar); assertTrue(result[1] is WidgetNextEvent.Personal)
        assertEquals(2, nextWidgetEvents(personal, emptyList(), today, zone, emptyList()).size)
        assertEquals(2, nextWidgetEvents(emptyList(), calendar, today, zone, emptyList()).size)
    }
    @Test fun exactAllDayHolidayCopiesDoNotConsumeSlots() {
        val date = LocalDate.of(2026, 11, 2)
        val holidays = futureNationalHolidays(today)
        val events = listOf(appointment(date, "Finados", true), appointment(date, "Reunião"),
            appointment(LocalDate.of(2026, 10, 28), "Dia do Servidor Público federal", true))
        val result = nextWidgetEvents(emptyList(), events, today, zone, holidays)
        assertEquals(2, result.size); assertTrue(result.none { it.title == "Finados" })
        assertTrue(result.any { it.title == "Dia do Servidor Público federal" })
        assertTrue(nextWidgetEvents(emptyList(), listOf(appointment(date, "Finados")), today, zone, holidays).isNotEmpty())
    }
}
