package com.folhinhautil.app.widget

import com.folhinhautil.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class WidgetPreferencesTests {
    private val november = LocalDate.of(2026, 11, 1)
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test fun octoberRollsIntoNovemberAndMigratesLegacyState() {
        assertEquals(YearMonth.of(2026, 11), resolvedWidgetMonth("2026-10", "2026-10", november))
        assertEquals(YearMonth.of(2026, 11), resolvedWidgetMonth("2026-10", null, november))
    }
    @Test fun manualNavigationSurvivesOnlyUntilNextCivilMonth() {
        assertEquals(YearMonth.of(2027, 2), resolvedWidgetMonth("2027-02", "2026-11", november.plusDays(20)))
        assertEquals(YearMonth.of(2026, 12), resolvedWidgetMonth("2027-02", "2026-11", november.plusMonths(1)))
        assertEquals(YearMonth.of(2027, 3), resolvedWidgetMonth("2027-02", "2026-11", LocalDate.of(2027, 3, 4)))
    }
    @Test fun preferenceRoundTripsPreserveZeroOneTwoAndOrder() {
        for (categories in listOf(emptyList(), listOf(SidebarCategory.CALENDAR),
            listOf(SidebarCategory.OTHER_DATES, SidebarCategory.HOLIDAYS),
            listOf(SidebarCategory.CALENDAR, SidebarCategory.OTHER_DATES))) {
            val settings = WidgetPreferences(categories, false)
            assertEquals(settings, WidgetPreferences.decode(settings.encode(), false))
        }
        assertEquals(WidgetPreferences(), WidgetPreferences.decode(null))
        assertEquals(emptyList<SidebarCategory>(), WidgetPreferences.decode("").categories)
    }
    @Test fun configurationRejectsUnavailableDuplicateAndExcessCategories() {
        assertEquals(listOf(SidebarCategory.CALENDAR, SidebarCategory.HOLIDAYS),
            WidgetPreferences.decode("WEATHER,CALENDAR,CALENDAR,HOLIDAYS,OTHER_DATES,unknown").categories)
        assertFalse(SidebarCategory.WEATHER.available)
    }
    private fun event(id: Long, date: String, allDay: Boolean = false, title: String = "Evento $id") =
        Appointment(id, 1, title, Instant.parse(date), Instant.parse(date).plusSeconds(3600), allDay)

    @Test fun appointmentsAreMonthScopedChronologicalLimitedAndRecurringInstancesSurvive() {
        val month = YearMonth.of(2026, 11)
        val list = listOf(event(1, "2026-10-31T22:00:00Z"), event(2, "2026-12-01T12:00:00Z"),
            event(3, "2026-11-20T12:00:00Z"), event(4, "2026-11-01T12:00:00Z"),
            event(4, "2026-11-08T12:00:00Z"), event(5, "2026-11-25T12:00:00Z"))
        val rows = widgetAppointments(list + list, month, zone)
        assertEquals(listOf(4L, 4L, 3L), rows.map { it.eventId })
        assertTrue(rows.all { YearMonth.from(it.startDate(zone)) == month })
    }
    @Test fun allDayUsesUtcCivilDateAndDeduplicatesCatalogCopies() {
        val month = YearMonth.of(2026, 11)
        val holiday = Holiday(month.atDay(2), "Finados", HolidayKind.NATIONAL, "fixture")
        val allDay = Appointment(1, 1, "FINADOS", Instant.parse("2026-11-02T00:00:00Z"),
            Instant.parse("2026-11-03T00:00:00Z"), true)
        assertTrue(widgetAppointments(listOf(allDay), month, zone, listOf(holiday)).isEmpty())
        assertEquals("02/11 · Dia inteiro", widgetAppointments(listOf(allDay.copy(title = "Viagem")), month, zone).single().description(zone))
        assertTrue(widgetAppointments(listOf(allDay), YearMonth.of(2026, 10), zone).isEmpty())
    }
    @Test fun eventsCrossingMonthBoundaryRequireActualIntersection() {
        val spanning = Appointment(1, 1, "Viagem", Instant.parse("2026-10-31T12:00:00Z"), Instant.parse("2026-11-02T12:00:00Z"), false)
        assertEquals(1, widgetAppointments(listOf(spanning), YearMonth.of(2026, 11), zone).size)
        assertTrue(widgetAppointments(listOf(spanning), YearMonth.of(2026, 12), zone).isEmpty())
    }
}
