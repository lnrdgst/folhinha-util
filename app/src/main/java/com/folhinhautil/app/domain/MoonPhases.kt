package com.folhinhautil.app.domain

import java.time.*
import kotlin.math.*

/** Eight calendar phases. Primary phases name their local civil day, not a multi-day window. */
enum class MoonPhase(val label: String) {
    NEW("Lua nova"), WAXING_CRESCENT("Lua crescente"), FIRST_QUARTER("Quarto crescente"),
    WAXING_GIBBOUS("Lua gibosa crescente"), FULL("Lua cheia"),
    WANING_GIBBOUS("Lua gibosa minguante"), LAST_QUARTER("Quarto minguante"), WANING_CRESCENT("Lua minguante")
}
data class MoonEvent(val phase: MoonPhase, val instant: Instant)
data class MoonDay(val date: LocalDate, val phase: MoonPhase, val principal: MoonEvent? = null)

/** Meeus, Astronomical Algorithms, 2nd ed., chapter 49 (periodic and planetary corrections).
 * TT is converted to approximate UT using Espenak/Meeus Delta T polynomials (NASA).
 * Offline; no illumination estimate. See docs/LUA.md for daily naming and accuracy limits.
 * Formula implementation informed by Sonia Keys' MIT-licensed meeus/moonphase; see docs/licenses/meeus-MIT.txt.
 */
object MoonPhases {
    private const val Epoch = 2451550.09766
    private const val SynodicMonth = 29.530588861
    private val principalPhases = listOf(MoonPhase.NEW, MoonPhase.FIRST_QUARTER, MoonPhase.FULL, MoonPhase.LAST_QUARTER)
    private val newCoefficients = doubleArrayOf(-.40720,.17241,.01608,.01039,.00739,-.00514,.00208,-.00111,-.00057,.00056,-.00042,.00042,.00038,-.00024,-.00017,-.00007,.00004,.00004,.00003,.00003,-.00003,.00003,-.00002,-.00002,.00002)
    private val fullCoefficients = doubleArrayOf(-.40614,.17302,.01614,.01043,.00734,-.00515,.00209,-.00111,-.00057,.00056,-.00042,.00042,.00038,-.00024,-.00017,-.00007,.00004,.00004,.00003,.00003,-.00003,.00003,-.00002,-.00002,.00002)
    private fun julian(instant: Instant) = 2440587.5 + instant.epochSecond / 86400.0 + instant.nano / 86400e9
    private fun instant(jd: Double) = Instant.ofEpochMilli(((jd - 2440587.5) * 86400000).roundToLong())
    private fun radians(degrees: Double) = Math.toRadians(degrees % 360.0)

    fun forMonth(month: YearMonth, zone: ZoneId): Map<LocalDate, MoonDay> {
        require(month.year in FirstYear..LastYear)
        val events = surrounding(month.atDay(1).atStartOfDay(zone).toInstant(), month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant())
        return (1..month.lengthOfMonth()).associate { number ->
            val date = month.atDay(number)
            val begin = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val principal = events.firstOrNull { it.instant >= begin && it.instant < end }
            // Between real phase events, use the corresponding intermediate phase.
            val preceding = events.last { it.instant <= date.atTime(12, 0).atZone(zone).toInstant() }
            val intermediate = when (preceding.phase) {
                MoonPhase.NEW -> MoonPhase.WAXING_CRESCENT
                MoonPhase.FIRST_QUARTER -> MoonPhase.WAXING_GIBBOUS
                MoonPhase.FULL -> MoonPhase.WANING_GIBBOUS
                MoonPhase.LAST_QUARTER -> MoonPhase.WANING_CRESCENT
                else -> error("Not a principal event")
            }
            date to MoonDay(date, principal?.phase ?: intermediate, principal)
        }
    }
    fun forDate(date: LocalDate, zone: ZoneId): MoonDay = forMonth(YearMonth.from(date), zone).getValue(date)
    fun principalEvents(year: Int): List<MoonEvent> {
        require(year in FirstYear..LastYear)
        val begin = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        return surrounding(begin, end).filter { it.instant >= begin && it.instant < end }
    }
    private fun surrounding(begin: Instant, end: Instant): List<MoonEvent> {
        val first = floor((julian(begin) - Epoch) / SynodicMonth).toInt() - 2
        val last = ceil((julian(end) - Epoch) / SynodicMonth).toInt() + 2
        return (first..last).flatMap { lunation -> principalPhases.mapIndexed { quarter, phase ->
            MoonEvent(phase, truePhase(lunation + quarter / 4.0, quarter))
        } }.sortedBy { it.instant }
    }
    private fun truePhase(k: Double, quarter: Int): Instant {
        val t = k / 1236.85; val t2 = t*t; val t3 = t2*t; val t4 = t3*t
        val mean = Epoch + SynodicMonth*k + .00015437*t2 - .000000150*t3 + .00000000073*t4
        val e = 1 - .002516*t - .0000074*t2
        val m = radians(2.5534 + 29.10535670*k - .0000014*t2 - .00000011*t3)
        val mp = radians(201.5643 + 385.81693528*k + .0107582*t2 + .00001238*t3 - .000000058*t4)
        val f = radians(160.7108 + 390.67050284*k - .0016118*t2 - .00000227*t3 + .000000011*t4)
        val omega = radians(124.7746 - 1.56375588*k + .0020672*t2 + .00000215*t3)
        val correction = if (quarter == 0 || quarter == 2) {
            val coefficients = if (quarter == 0) newCoefficients else fullCoefficients
            val angles = doubleArrayOf(mp,m,2*mp,2*f,mp-m,mp+m,2*m,mp-2*f,mp+2*f,2*mp+m,3*mp,m+2*f,m-2*f,2*mp-m,omega,mp+2*m,2*(mp-f),3*m,mp+m-2*f,2*(mp+f),mp+m+2*f,mp-m+2*f,mp-m-2*f,3*mp+m,4*mp)
            val factors = doubleArrayOf(1.0,e,1.0,1.0,e,e,e*e,1.0,1.0,e,1.0,e,e,e,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0)
            coefficients.indices.sumOf { coefficients[it] * factors[it] * sin(angles[it]) }
        } else {
            val common = -.62801*sin(mp) + .17172*e*sin(m) - .01183*e*sin(mp+m) + .00862*sin(2*mp) + .00804*sin(2*f) +
                .00454*e*sin(mp-m) + .00204*e*e*sin(2*m) - .00180*sin(mp-2*f) - .00070*sin(mp+2*f) - .00040*sin(3*mp) -
                .00034*sin(2*mp-m) + .00032*e*sin(m+2*f) + .00032*e*sin(m-2*f) - .00028*e*e*sin(mp+2*m) +
                .00027*e*sin(2*mp+m) - .00017*sin(omega) - .00005*sin(mp-m-2*f) + .00004*sin(2*mp+2*f) -
                .00004*sin(mp+m+2*f) + .00004*sin(mp-2*m) + .00003*sin(mp+m-2*f) + .00003*sin(3*m) +
                .00002*sin(2*mp-2*f) + .00002*sin(mp-m+2*f) - .00002*sin(3*mp+m)
            val w = .00306 - .00038*e*cos(m) + .00026*cos(mp) - .00002*cos(mp-m) + .00002*cos(mp+m) + .00002*cos(2*f)
            common + if (quarter == 1) w else -w
        }
        val planetaryAngles = doubleArrayOf(299.77+.107408*k-.009173*t2,251.88+.016321*k,251.83+26.651886*k,
            349.42+36.412478*k,84.66+18.206239*k,141.74+53.303771*k,207.14+2.453732*k,
            154.84+7.306860*k,34.52+27.261239*k,207.19+.121824*k,291.34+1.844379*k,
            161.72+24.198154*k,239.56+25.513099*k,331.55+3.592518*k)
        val planetaryCoefficients = doubleArrayOf(.000325,.000165,.000164,.000126,.000110,.000062,.000060,.000056,.000047,.000042,.000040,.000037,.000035,.000023)
        val jde = mean + correction + planetaryAngles.indices.sumOf { planetaryCoefficients[it] * sin(radians(planetaryAngles[it])) }
        val approximateDate = instant(jde).atZone(ZoneOffset.UTC).toLocalDate()
        val decimalYear = approximateDate.year + (approximateDate.dayOfYear - .5) / approximateDate.lengthOfYear()
        return instant(jde - deltaT(decimalYear) / 86400)
    }
    // NASA/Espenak-Meeus model. UT1 is used as approximate UTC; future Delta T is uncertain.
    private fun deltaT(year: Double): Double {
        val t = year - 2000
        return when {
            year < 2005 -> 63.86 + .3345*t - .060374*t*t + .0017275*t*t*t + .000651814*t.pow(4) + .00002373599*t.pow(5)
            year < 2050 -> 62.92 + .32217*t + .005589*t*t
            else -> -20 + 32*((year - 1820)/100).pow(2) - .5628*(2150 - year)
        }
    }
}
