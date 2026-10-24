package com.folhinhautil.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CalendarTests {
    @Test fun `domingo inicia a semana e sabado encerra`() {
        val grid = monthGrid(YearMonth.of(2026, 8))
        assertNull(grid[0][0])
        assertEquals(LocalDate.of(2026, 8, 1), grid[0][6])
        assertEquals(LocalDate.of(2026, 8, 2), grid[1][0])
    }
    @Test fun `meses de quatro cinco e seis linhas`() {
        assertEquals(4, monthGrid(YearMonth.of(2026, 2)).size)
        assertEquals(5, monthGrid(YearMonth.of(2026, 4)).size)
        assertEquals(6, monthGrid(YearMonth.of(2026, 8)).size)
    }
    @Test fun `fevereiro bissexto e virada secular`() {
        assertEquals(29, monthGrid(YearMonth.of(2024, 2)).flatten().filterNotNull().size)
        assertEquals(28, monthGrid(YearMonth.of(2100, 2)).flatten().filterNotNull().size)
        assertEquals(29, monthGrid(YearMonth.of(2000, 2)).flatten().filterNotNull().size)
    }
    @Test fun `todos os meses preservam dias e colunas corretos`() {
        for (year in 2003..2099) for (month in 1..12) {
            val ym = YearMonth.of(year, month)
            val grid = monthGrid(ym)
            assertTrue(grid.size in 4..6)
            assertEquals((1..ym.lengthOfMonth()).toList(), grid.flatten().filterNotNull().map { it.dayOfMonth })
            grid.forEach { week -> week.forEachIndexed { column, day ->
                if (day != null) assertEquals(day.dayOfWeek.value % 7, column)
            } }
        }
    }
    @Test fun `somente sabado e domingo sao finais de semana`() {
        val sunday = LocalDate.of(2026, 10, 25)
        assertTrue(sunday.isWeekend())
        assertTrue(sunday.minusDays(1).isWeekend())
        for (day in 1L..5L) assertFalse(sunday.plusDays(day).isWeekend())
    }
    @Test fun `feriados nacionais recorrentes e base legal`() {
        val holidays = BrazilianHolidayProvider().forYear(2026).filter { it.kind == HolidayKind.NATIONAL }
        assertEquals(listOf("2026-01-01", "2026-04-21", "2026-05-01", "2026-09-07", "2026-10-12",
            "2026-11-02", "2026-11-15", "2026-11-20", "2026-12-25"), holidays.map { it.date.toString() })
        assertTrue(holidays.all { it.source.startsWith("Lei") })
    }
    @Test fun `consciencia negra nacional apenas a partir de 2024`() {
        val provider = BrazilianHolidayProvider()
        assertEquals(HolidayKind.COMMEMORATIVE, provider.forYear(2023).single { it.date == LocalDate.of(2023,11,20) }.kind)
        assertEquals(HolidayKind.NATIONAL, provider.forYear(2024).single { it.date == LocalDate.of(2024,11,20) }.kind)
        assertFalse(provider.forYear(2010).any { it.date == LocalDate.of(2010,11,20) })
    }
    @Test fun `pascoa e datas moveis nao se confundem com feriado nacional`() {
        assertEquals(LocalDate.of(2024,3,31), easterSunday(2024))
        assertEquals(LocalDate.of(2026,4,5), easterSunday(2026))
        val holidays = BrazilianHolidayProvider().forYear(2026)
        assertEquals(HolidayKind.OPTIONAL, holidays.single { it.name == "Corpus Christi" }.kind)
        assertEquals(HolidayKind.LOCAL_RELIGIOUS, holidays.single { it.name == "Sexta-feira da Paixão" }.kind)
        assertEquals(LocalDate.of(2026,4,3), holidays.single { it.name == "Sexta-feira da Paixão" }.date)
        assertFalse(BrazilianHolidayProvider().forYear(2027).any { it.kind == HolidayKind.OPTIONAL })
    }
    private fun event(id: Long, start: String, end: String, allDay: Boolean = false) =
        Appointment(id, 1, "Evento de teste", Instant.parse(start), Instant.parse(end), allDay)

    @Test fun `proximos ordenados incluindo ocorrencias em andamento e recorrentes`() {
        val events = listOf(
            event(1,"2026-10-24T17:00:00Z","2026-10-24T18:00:00Z"),
            event(2,"2026-10-24T12:00:00Z","2026-10-24T13:00:00Z"),
            event(1,"2026-10-25T17:00:00Z","2026-10-25T18:00:00Z"),
            event(3,"2026-10-24T13:30:00Z","2026-10-24T15:00:00Z"))
        val result = upcomingAppointments(events, Instant.parse("2026-10-24T14:00:00Z"), ZoneId.of("America/Sao_Paulo"))
        assertEquals(listOf(3L,1L,1L), result.map { it.eventId })
        assertTrue(result[1].begin < result[2].begin)
    }
    @Test fun `dia inteiro mantem data e ordenacao no fuso local`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val allDay = event(1,"2026-10-24T00:00:00Z","2026-10-25T00:00:00Z",true)
        assertEquals(LocalDate.of(2026,10,24), allDay.startDate(zone))
        assertTrue(allDay.occursOn(LocalDate.of(2026,10,24),zone))
        assertFalse(allDay.occursOn(LocalDate.of(2026,10,23),zone))
        assertFalse(allDay.occursOn(LocalDate.of(2026,10,25),zone))
        val timed = event(2,"2026-10-24T02:00:00Z","2026-10-24T04:00:00Z")
        val result = upcomingAppointments(listOf(allDay,timed),Instant.parse("2026-10-24T02:30:00Z"),zone)
        assertEquals(listOf(2L,1L),result.map { it.eventId })
        assertEquals(1,upcomingAppointments(listOf(allDay),Instant.parse("2026-10-25T02:59:00Z"),zone).size)
        assertTrue(upcomingAppointments(listOf(allDay),Instant.parse("2026-10-25T03:00:00Z"),zone).isEmpty())
    }
    @Test fun `dia inteiro de varios dias tem fim exclusivo e evento cruza meia noite`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val allDay = event(1,"2026-10-24T00:00:00Z","2026-10-27T00:00:00Z",true)
        assertTrue(allDay.occursOn(LocalDate.of(2026,10,26),zone))
        assertFalse(allDay.occursOn(LocalDate.of(2026,10,27),zone))
        val timed = event(2,"2026-10-24T14:30:00Z","2026-10-24T16:00:00Z")
        assertTrue(timed.occursOn(LocalDate.of(2026,10,24),zone))
        assertTrue(timed.occursOn(LocalDate.of(2026,10,25),zone))
    }
}
