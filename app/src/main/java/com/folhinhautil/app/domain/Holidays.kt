package com.folhinhautil.app.domain

import java.time.LocalDate

enum class HolidayKind(val label: String) {
    NATIONAL("Feriado nacional"), OPTIONAL("Ponto facultativo federal"),
    COMMEMORATIVE("Data comemorativa"), LOCAL_RELIGIOUS("Feriado religioso: depende de lei municipal")
}
data class Holiday(val date: LocalDate, val name: String, val kind: HolidayKind, val source: String)
interface HolidayProvider { fun forYear(year: Int): List<Holiday> }

/** Recurring federal laws from 2003 onward; annual optional days are never projected to other years. */
class BrazilianHolidayProvider : HolidayProvider {
    override fun forYear(year: Int): List<Holiday> {
        require(year in FirstYear..LastYear)
        val result = mutableListOf<Holiday>()
        fun add(month: Int, day: Int, name: String, kind: HolidayKind, source: String) {
            result += Holiday(LocalDate.of(year, month, day), name, kind, source)
        }
        val law662 = "Lei 662/1949, redação da Lei 10.607/2002"
        listOf(Triple(1, 1, "Confraternização Universal"), Triple(4, 21, "Tiradentes"),
            Triple(5, 1, "Dia do Trabalho"), Triple(9, 7, "Independência do Brasil"),
            Triple(11, 2, "Finados"), Triple(11, 15, "Proclamação da República"), Triple(12, 25, "Natal"))
            .forEach { (m, d, name) -> add(m, d, name, HolidayKind.NATIONAL, law662) }
        add(10, 12, "Nossa Senhora Aparecida", HolidayKind.NATIONAL, "Lei 6.802/1980")
        add(11, 20, "Dia Nacional de Zumbi e da Consciência Negra",
            if (year >= 2024) HolidayKind.NATIONAL else HolidayKind.COMMEMORATIVE,
            if (year >= 2024) "Lei 14.759/2023" else "Lei 12.519/2011 (desde 2011)")
        if (year < 2011) result.removeAll { it.date.monthValue == 11 && it.date.dayOfMonth == 20 }
        val easter = easterSunday(year)
        result += Holiday(easter, "Páscoa", HolidayKind.COMMEMORATIVE, "Data religiosa; não é feriado civil nacional")
        result += Holiday(easter.minusDays(2), "Sexta-feira da Paixão", HolidayKind.LOCAL_RELIGIOUS,
            "Lei 9.093/1995, art. 2º. Calendário federal de 2026: Portaria MGI 11.460/2025")
        if (year == 2026) {
            val source = "Portaria MGI 11.460/2025; âmbito da administração pública federal"
            listOf(Triple(2,16,"Carnaval"), Triple(2,17,"Carnaval"), Triple(2,18,"Quarta-feira de Cinzas (até 14h)"),
                Triple(4,20,"Ponto facultativo federal"), Triple(6,4,"Corpus Christi"),
                Triple(6,5,"Ponto facultativo federal"), Triple(10,28,"Dia do Servidor Público federal"),
                Triple(12,24,"Véspera de Natal (após 13h)"), Triple(12,31,"Véspera de Ano Novo (após 13h)"))
                .forEach { (m,d,name) -> add(m,d,name,HolidayKind.OPTIONAL,source) }
        }
        return result.sortedBy { it.date }
    }
}

// Gregorian Meeus/Jones/Butcher algorithm.
fun easterSunday(year: Int): LocalDate {
    val a = year % 19; val b = year / 100; val c = year % 100
    val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val n = h + l - 7 * m + 114
    return LocalDate.of(year, n / 31, n % 31 + 1)
}
