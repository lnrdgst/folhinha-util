package com.folhinhautil.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class WidgetHolidaysTests {
    private val today = LocalDate.of(2026, 10, 25)
    private fun dates(month: YearMonth, date: LocalDate = today) = widgetNationalHolidays(month, date).map { it.date }
    @Test fun octoberKeepsPastHolidayAndCompletesThree() {
        val month = YearMonth.of(2026, 10)
        assertEquals(month.atDay(1), widgetHolidayReference(month, today))
        assertEquals(listOf(LocalDate.of(2026,10,12), LocalDate.of(2026,11,2), LocalDate.of(2026,11,15)), dates(month))
    }
    @Test fun novemberAlwaysStartsAtFirstDay() {
        val expected = listOf(2,15,20).map { LocalDate.of(2026,11,it) }
        for (day in listOf(1,3,16,21,30)) assertEquals(expected, dates(YearMonth.of(2026,11), LocalDate.of(2026,11,day)))
    }
    @Test fun januarySkipsMonthsWithoutNationalHolidays() {
        assertEquals(listOf(LocalDate.of(2027,1,1), LocalDate.of(2027,4,21), LocalDate.of(2027,5,1)), dates(YearMonth.of(2027,1)))
    }
    @Test fun decemberContinuesAcrossYear() {
        assertEquals(listOf(LocalDate.of(2026,12,25), LocalDate.of(2027,1,1), LocalDate.of(2027,4,21)), dates(YearMonth.of(2026,12)))
    }
    @Test fun optionalCommemorativeAndLocalDatesAreExcluded() {
        val result = widgetNationalHolidays(YearMonth.of(2026,2), today)
        assertEquals(listOf(LocalDate.of(2026,4,21), LocalDate.of(2026,5,1), LocalDate.of(2026,9,7)), result.map { it.date })
        assertTrue(result.all { it.kind == HolidayKind.NATIONAL })
    }
    @Test fun threeOnlyOrderedUniqueAndExistingProviderPreserved() {
        val provider = object : HolidayProvider {
            override fun forYear(year: Int) = (1..5).reversed().flatMap { day ->
                val holiday = Holiday(LocalDate.of(year,8,day), "Feriado $day", HolidayKind.NATIONAL, "fixture")
                listOf(holiday, holiday.copy(source = "duplicate source"))
            }
        }
        val result = widgetNationalHolidays(YearMonth.of(2026,8), today, provider)
        assertEquals(listOf(1,2,3), result.map { it.date.dayOfMonth })
        assertEquals(result.distinctBy { it.date to it.name }, result)
    }
    @Test fun sparseProviderContinuesUntilThirdHolidayNotOnlyNextYear() {
        val requested = mutableListOf<Int>()
        val provider = object : HolidayProvider {
            override fun forYear(year: Int): List<Holiday> {
                requested += year
                return if (year in listOf(2026,2028,2030)) listOf(Holiday(LocalDate.of(year,12,25), "Fixture", HolidayKind.NATIONAL, "fixture")) else emptyList()
            }
        }
        assertEquals(listOf(2026,2028,2030), widgetNationalHolidays(YearMonth.of(2026,12), today, provider).map { it.date.year })
        assertEquals((2026..2030).toList(), requested)
    }
    @Test fun catalogBoundaryIsNotExtrapolated() {
        assertEquals(listOf(LocalDate.of(2099,12,25)), dates(YearMonth.of(2099,12), LocalDate.of(2099,12,26)))
        assertEquals(LocalDate.of(2003,1,1), dates(YearMonth.of(FirstYear,1)).first())
    }
    @Test fun allMonthsHaveThreeUnlessCatalogIsExhausted() {
        for (year in FirstYear..LastYear) for (month in 1..12) {
            val period = YearMonth.of(year,month)
            val result = widgetNationalHolidays(period,today)
            assertEquals(if (year == LastYear && month == 12) 1 else 3, result.size)
            assertTrue(result.all { it.date >= period.atDay(1) && it.kind == HolidayKind.NATIONAL })
            assertEquals(result.sortedBy { it.date }, result)
        }
    }
    @Test fun navigationDoesNotChangePersonalCountdown() {
        val event = PersonalEvent("fixture", "Validação", LocalDate.of(2026,12,27))
        for (month in listOf(YearMonth.of(2026,8), YearMonth.of(2026,10), YearMonth.of(2027,1))) {
            assertEquals(3,widgetNationalHolidays(month,today).size)
            assertEquals(63L,upcomingPersonalEvents(listOf(event),today).single().daysRemaining)
        }
    }

    @Test fun optionalAndOtherDatesFollowMonthInAscendingOrder() {
        assertEquals(listOf(LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 31)),
            widgetOtherDates(YearMonth.of(2026, 12)).map { it.date })
        val october = widgetOtherDates(YearMonth.of(2026, 10))
        assertEquals(listOf(LocalDate.of(2026, 10, 28)), october.map { it.date })
        assertEquals(HolidayKind.OPTIONAL, october.single().kind)
        assertTrue(widgetOtherDates(YearMonth.of(2026, 11)).isEmpty())
    }
    @Test fun aprilRetainsCommemorativeAndReligiousCategories() {
        val result = widgetOtherDates(YearMonth.of(2026, 4))
        assertEquals(listOf(3, 5, 20), result.map { it.date.dayOfMonth })
        assertEquals(listOf(HolidayKind.LOCAL_RELIGIOUS, HolidayKind.COMMEMORATIVE, HolidayKind.OPTIONAL), result.map { it.kind })
        assertTrue(result.none { it.kind == HolidayKind.NATIONAL })
    }
    @Test fun selectionHasSeparateLimitOfTwoForEachGroup() {
        val provider = object : HolidayProvider {
            override fun forYear(year: Int) = (1..3).flatMap { offset ->
                listOf(Holiday(LocalDate.of(year, 6, offset), "Facultativo $offset", HolidayKind.OPTIONAL, "fixture"),
                    Holiday(LocalDate.of(year, 6, offset + 10), "Comemoração $offset", HolidayKind.COMMEMORATIVE, "fixture"))
            } + Holiday(LocalDate.of(year, 6, 30), "Nacional", HolidayKind.NATIONAL, "fixture")
        }
        val result = widgetOtherDates(YearMonth.of(2026, 6), provider)
        assertEquals(listOf(2, 3, 12, 13), result.map { it.date.dayOfMonth })
        assertEquals(2, result.count { it.kind == HolidayKind.OPTIONAL })
        assertEquals(2, result.count { it.kind != HolidayKind.OPTIONAL })
    }


    @Test fun legendOnlyDescribesIndicatorsPresentInDisplayedMonth() {
        val holidays = BrazilianHolidayProvider().forYear(2026)
        assertEquals(MonthLegend(true, true), monthLegend(YearMonth.of(2026, 10), holidays))
        assertEquals(MonthLegend(true, false), monthLegend(YearMonth.of(2026, 11), holidays))
        assertEquals(MonthLegend(false, true), monthLegend(YearMonth.of(2026, 6), holidays))
        assertEquals(MonthLegend(false, false), monthLegend(YearMonth.of(2026, 8), holidays))
    }



}
