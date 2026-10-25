package com.folhinhautil.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.math.abs

class MoonPhasesTests {
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")
    @Test fun principalEventsMatchOfficialReferences() {
        val rows = javaClass.getResourceAsStream("/astronomy/moon-phase-references.csv")!!.bufferedReader().use { it.readLines() }
            .filter { !it.startsWith("#") && !it.startsWith("phase,") && it.isNotBlank() }
        assertTrue(rows.size >= 340)
        var maximumError = 0L
        val cache = mutableMapOf<Int,List<MoonEvent>>()
        for (line in rows) {
            val (code, timestamp) = line.split(',')
            val phase = MoonPhase.valueOf(code)
            val expected = Instant.parse(timestamp)
            val actual = cache.getOrPut(expected.atZone(ZoneOffset.UTC).year) { MoonPhases.principalEvents(expected.atZone(ZoneOffset.UTC).year) }
                .filter { it.phase == phase }.minBy { abs(Duration.between(it.instant,expected).toMillis()) }
            val error = abs(Duration.between(actual.instant,expected).toMillis())
            maximumError = maxOf(maximumError,error)
            assertTrue("$line: error ${error/1000.0}s",error <= 120_000)
        }
        println("USNO/NASA references=${rows.size}; maximum absolute error=${maximumError/1000.0}s")
    }
    @Test fun allEightPhasesAndTransitionsArePresent() {
        val days = MoonPhases.forMonth(YearMonth.of(2026,10),saoPaulo)
        assertEquals(MoonPhase.entries.toSet(),days.values.map { it.phase }.toSet())
        assertEquals(MoonPhase.WAXING_CRESCENT,days.getValue(LocalDate.of(2026,10,17)).phase)
        assertEquals(MoonPhase.FIRST_QUARTER,days.getValue(LocalDate.of(2026,10,18)).phase)
        assertEquals(MoonPhase.WAXING_GIBBOUS,days.getValue(LocalDate.of(2026,10,19)).phase)
        assertEquals(MoonPhase.FULL,days.getValue(LocalDate.of(2026,10,26)).phase)
        assertEquals(MoonPhase.WANING_GIBBOUS,days.getValue(LocalDate.of(2026,10,27)).phase)
    }
    @Test fun eventDatesFollowLocalMidnightRatherThanUtcDate() {
        val first = LocalDate.of(2026,4,1)
        val second = first.plusDays(1)
        assertEquals(MoonPhase.FULL,MoonPhases.forDate(first,saoPaulo).phase)
        assertNull(MoonPhases.forDate(first,ZoneOffset.UTC).principal)
        assertEquals(MoonPhase.FULL,MoonPhases.forDate(second,ZoneOffset.UTC).phase)
        assertEquals(MoonPhase.WANING_GIBBOUS,MoonPhases.forDate(second,saoPaulo).phase)
        assertEquals(MoonPhase.FULL,MoonPhases.forDate(LocalDate.of(2026,6,30),ZoneOffset.ofHours(14)).phase)
        assertEquals(MoonPhase.FULL,MoonPhases.forDate(LocalDate.of(2026,6,29),ZoneOffset.UTC).phase)
    }
    @Test fun yearBoundaryAndLeapDaysHaveNoMissingData() {
        for (date in listOf(LocalDate.of(2003,1,1),LocalDate.of(2026,12,31),LocalDate.of(2027,1,1),LocalDate.of(2024,2,29),LocalDate.of(2028,2,29),LocalDate.of(2099,12,31))) {
            assertEquals(date,MoonPhases.forDate(date,saoPaulo).date)
        }
        assertEquals(29,MoonPhases.forMonth(YearMonth.of(2024,2),saoPaulo).size)
        assertEquals(28,MoonPhases.forMonth(YearMonth.of(2027,2),saoPaulo).size)
    }
    @Test fun everySupportedMonthAndDstCalendarContainsExactlyItsDays() {
        for (zone in listOf(saoPaulo,ZoneId.of("America/New_York"))) for (year in FirstYear..LastYear) for (month in 1..12) {
            val period = YearMonth.of(year,month)
            val days = MoonPhases.forMonth(period,zone)
            assertEquals(period.lengthOfMonth(),days.size)
            assertEquals((1..period.lengthOfMonth()).toList(),days.keys.map { it.dayOfMonth })
            assertTrue(days.values.all { day -> day.principal == null || day.principal.instant.atZone(zone).toLocalDate() == day.date })
        }
    }
    @Test fun realLunationsAreNotFixedLengthAndEventsStayChronological() {
        val events = MoonPhases.principalEvents(2026)
        assertEquals(events.distinctBy { it.instant },events)
        assertTrue(events.zipWithNext().all { (a,b) -> a.instant < b.instant })
        val lengths = events.filter { it.phase == MoonPhase.NEW }.zipWithNext().map { (a,b) -> Duration.between(a.instant,b.instant).toMinutes() }
        assertTrue(lengths.distinct().size > 5)
    }
    @Test fun moonDataDoesNotMutateHolidayCalendar() {
        val provider = BrazilianHolidayProvider()
        val original = provider.forYear(2026)
        MoonPhases.forMonth(YearMonth.of(2026,10),saoPaulo)
        assertEquals(original,provider.forYear(2026))
        assertTrue(LocalDate.of(2026,10,25).isWeekend())
    }
}
