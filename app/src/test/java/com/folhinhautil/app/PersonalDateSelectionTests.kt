package com.folhinhautil.app

import com.folhinhautil.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.TimeZone

class PersonalDateSelectionTests {
    @Test fun pickerRoundTripDoesNotDependOnDefaultTimezone() {
        val previous = TimeZone.getDefault()
        try {
            for (zone in listOf("America/Sao_Paulo", "Pacific/Kiritimati", "America/Los_Angeles", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (date in listOf(LocalDate.of(2026,12,27), LocalDate.of(1940,1,1), LocalDate.of(1800,5,12),
                    LocalDate.of(2024,2,29), LocalDate.of(2099,12,31), LocalDate.of(1,1,1), LocalDate.of(9999,12,31))) {
                    assertEquals(date, pickerDateFromMillis(date.toPickerDateMillis()))
                    assertEquals(date, parsePersonalDate(formatPersonalDate(pickerDateFromMillis(date.toPickerDateMillis()))))
                }
            }
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun selectedDateIsUtcMidnightRatherThanLocalMidnight() {
        assertEquals(Instant.parse("2026-12-27T00:00:00Z").toEpochMilli(),LocalDate.of(2026,12,27).toPickerDateMillis())
        assertEquals(LocalDate.of(2026,12,27),pickerDateFromMillis(Instant.parse("2026-12-27T00:00:00Z").toEpochMilli()))
    }
    @Test fun selectedLeapBirthdayKeepsAnnualCountdownPolicy() {
        val selected = pickerDateFromMillis(LocalDate.of(2024,2,29).toPickerDateMillis())
        val event = PersonalEvent("fixture","Aniversário",selected,true)
        assertEquals(LocalDate.of(2027,2,28),nextPersonalOccurrence(event,LocalDate.of(2027,2,27))!!.date)
        assertEquals(1L,nextPersonalOccurrence(event,LocalDate.of(2027,2,27))!!.daysRemaining)
    }
}
