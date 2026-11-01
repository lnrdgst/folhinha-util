package com.folhinhautil.app.widget

import androidx.datastore.preferences.core.*
import com.folhinhautil.app.domain.*
import java.text.Normalizer
import java.time.*

enum class SidebarCategory(val label: String, val available: Boolean = true) {
    HOLIDAYS("Feriados nacionais"), OTHER_DATES("Pontos facultativos e outras datas"),
    CALENDAR("Compromissos Android/Google"), WEATHER("Previsão do tempo", false)
}

internal val SidebarSelection = stringPreferencesKey("sidebar_categories")
internal val ShowMoon = booleanPreferencesKey("show_moon")
internal val CurrentMonthAnchor = stringPreferencesKey("current_month_anchor")

data class WidgetPreferences(
    val categories: List<SidebarCategory> = listOf(SidebarCategory.HOLIDAYS, SidebarCategory.OTHER_DATES),
    val showMoon: Boolean = true,
) {
    companion object {
        fun decode(value: String?, moon: Boolean = true): WidgetPreferences = WidgetPreferences(
            if (value == null) WidgetPreferences().categories else value.split(',').mapNotNull { token ->
                SidebarCategory.entries.firstOrNull { it.name == token && it.available }
            }.distinct().take(2), moon)
    }
    fun encode() = categories.filter { it.available }.distinct().take(2).joinToString(",") { it.name }
}

internal fun Preferences.widgetPreferences() = WidgetPreferences.decode(this[SidebarSelection], this[ShowMoon] ?: true)

/** A manual month belongs to the civil month in which it was chosen. Missing anchors migrate once. */
internal fun resolvedWidgetMonth(viewed: String?, anchor: String?, today: LocalDate): YearMonth =
    if (anchor != YearMonth.from(today).toString()) widgetMonth(null, today) else widgetMonth(viewed, today)

/** Instances are already expanded by CalendarRepository. Keep only dates in this civil month. */
internal fun widgetAppointments(events: List<Appointment>, month: YearMonth, zone: ZoneId,
    holidays: List<Holiday> = emptyList()): List<Appointment> {
    fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase(BrazilianLocale).replace(Regex("[^a-z0-9]+"), " ").trim()
    return events.filter { event ->
        // Include spanning events only when they actually occur on a day of this month.
        (1..month.lengthOfMonth()).any { event.occursOn(month.atDay(it), zone) }
    }.filterNot { event -> event.allDay && holidays.any {
        it.date == event.startDate(zone) && normalized(it.name) == normalized(event.title)
    } }.distinctBy { Triple(it.eventId, it.begin, it.end) }
        .sortedWith(compareBy<Appointment> { it.effectiveStart(zone) }.thenBy { it.eventId }).take(3)
}
