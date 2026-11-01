package com.folhinhautil.app.widget

import android.content.Context
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.compose.ui.unit.*
import com.folhinhautil.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal data class SidebarRow(val title: String, val detail: String?, val titleHeight: Float,
    val detailHeight: Float, val holiday: Holiday? = null) {
    val height get() = titleHeight + detailHeight
}
internal data class SidebarSection(val category: SidebarCategory, val rows: List<SidebarRow>, val headingHeight: Float) {
    val height get() = headingHeight + (if (headingHeight > 0) 4f else 0f) +
        rows.sumOf { it.height.toDouble() }.toFloat() + (rows.size - 1).coerceAtLeast(0) * 4f
}

internal fun widgetSpace(context: Context, size: DpSize, month: YearMonth, today: LocalDate,
    holidays: List<Holiday>, personal: List<PersonalEvent> = emptyList(),
    holidayProvider: HolidayProvider = BrazilianHolidayProvider(), preferences: WidgetPreferences = WidgetPreferences(),
    appointments: List<Appointment> = emptyList(), calendarMessage: String? = null): WidgetSpace {
    val density = context.resources.displayMetrics.density
    fun paint(sp: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
        typeface = widgetTypeface(context, bold)
    }
    fun height(text: String, sp: Float, width: Float, lines: Int = Int.MAX_VALUE, bold: Boolean = false): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint(sp, bold),
            floor((width - 2f).coerceAtLeast(1f) * density).toInt().coerceAtLeast(1))
            .setIncludePad(true).setMaxLines(lines).build()
        return ceil(layout.height + 2f * density) / density
    }
    fun width(text: String, sp: Float, bold: Boolean = false) = paint(sp, bold).measureText(text) / density
    val categories = preferences.categories.filter { it.available }.distinct().take(2)
    val label = if (size.width < 300.dp) month.format(DateTimeFormatter.ofPattern("MMM yyyy", BrazilianLocale))
        .replaceFirstChar { it.titlecase(BrazilianLocale) } else month.label()
    val weeks = monthGrid(month).size
    val legendKinds = monthLegend(month, holidays)
    val legendLine = height("• Outras datas", 9f, size.width.value, 1)
    val weekday = WeekLabels.maxOf { height(it, 9f, size.width.value / 7, 1) }
    val baseHeading = maxOf(32f, height(label, 17f, size.width.value - 64f, 2, true))
    val reservedLegend = if (legendKinds.national || legendKinds.other || SidebarCategory.HOLIDAYS in categories) legendLine else 0f
    fun numberHeight(sp: Float) = (1..month.lengthOfMonth()).maxOf {
        height(widgetDayLabel(month.atDay(it), holidays), sp, size.width.value / 7, 1, month.atDay(it) == today)
    }
    var dayFont = 13f
    // Only the smallest six-week layouts need a modest reduction with enlarged system fonts.
    // Keep all lunar glyphs below their own number, including at 1.5x accessibility scale.
    if (preferences.showMoon) while (dayFont > 11f &&
        baseHeading + weekday + weeks * maxOf(25f, numberHeight(dayFont) + 4f) + reservedLegend > size.height.value) dayFont -= .5f
    val numbers = numberHeight(dayFont)
    val minimumCellWidth = maxOf(WeekLabels.maxOf { width(it, 9f) },
        (1..month.lengthOfMonth()).maxOf { width(widgetDayLabel(month.atDay(it), holidays), dayFont, month.atDay(it) == today) }) + 2f
    val moonSize = if (!preferences.showMoon) 0f else
        ((size.height.value - baseHeading - weekday - reservedLegend) / weeks - numbers).coerceIn(4f, 8f)
    val day = maxOf(ceil(25 * density) / density, numbers + moonSize)
    val minimum = baseHeading + weekday + weeks * day + reservedLegend
    val padding = ((size.height.value - minimum) / 2).coerceIn(0f, 12f)
    val innerWidth = size.width.value - padding * 2
    val available = size.height.value - padding * 2
    val national = if (SidebarCategory.HOLIDAYS in categories) widgetNationalHolidays(month, today, holidayProvider) else emptyList()
    // Preserve the catalog's classifications and selection, capping presentation at three.
    val other = if (SidebarCategory.OTHER_DATES in categories) widgetOtherDates(month, holidayProvider).take(3) else emptyList()
    val duplicateDates = national + other
    val calendar = widgetAppointments(appointments, month, ZoneId.systemDefault(), duplicateDates)
    val eventHeading = height("Próximos eventos pessoais", 14f, innerWidth, 2, true)
    val allEvents = upcomingPersonalEvents(personal, today).take(2).map { occurrence ->
        WidgetPersonalSpace(occurrence, height(occurrence.event.title, 13f, innerWidth, 2),
            height(occurrence.countdown, 11f, innerWidth, 1), height(formatPersonalDate(occurrence.date), 10f, innerWidth, 1))
    }
    fun personalHeight(count: Int) = if (count == 0) 0f else eventHeading + 6f +
        allEvents.take(count).sumOf { it.height.toDouble() }.toFloat() + (count - 1) * 9f
    fun sections(sideWidth: Float): List<SidebarSection> = categories.mapNotNull { category ->
        val rows = when (category) {
            SidebarCategory.HOLIDAYS -> national.map { holiday ->
                val date = holiday.date.format(DateTimeFormatter.ofPattern(if (holiday.date.year != month.year) "dd/MM/yyyy" else "dd/MM"))
                SidebarRow(holiday.name, date, height(holiday.name, 11f, sideWidth), height(date, 10f, sideWidth, 1), holiday)
            }
            SidebarCategory.OTHER_DATES -> other.map { holiday ->
                val text = "${holiday.date.format(DateTimeFormatter.ofPattern("dd/MM"))} · ${holiday.name}"
                SidebarRow(text, null, height(text, 11f, sideWidth), 0f, holiday)
            }
            SidebarCategory.CALENDAR -> calendar.map { event ->
                val detail = event.description(ZoneId.systemDefault())
                SidebarRow(event.title, detail, height(event.title, 11f, sideWidth), height(detail, 10f, sideWidth))
            }.ifEmpty {
                val text = calendarMessage ?: "Sem compromissos neste mês"
                listOf(SidebarRow(text, null, height(text, 11f, sideWidth), 0f))
            }
            SidebarCategory.WEATHER -> emptyList()
        }
        if (rows.isEmpty()) null else SidebarSection(category, rows,
            if (category == SidebarCategory.HOLIDAYS) 0f else height(category.label, 10f, sideWidth, bold = true))
    }
    data class Fit(val lateral: Boolean, val calendarWidth: Float, val sideWidth: Float,
        val heading: Float, val stacked: Boolean, val legend: Float, val gap: Float, val calendarHeight: Float,
        val sections: List<SidebarSection>, val personalCount: Int, val top: Float) {
        val records get() = sections.sumOf { it.rows.size }
    }
    val fits = mutableListOf<Fit>()
    for (lateral in listOf(true, false)) {
        val sideWidth = if (lateral) (innerWidth * .36f).coerceIn(110f, 180f) else innerWidth
        val calendarWidth = if (lateral) innerWidth - sideWidth - 17f else innerWidth
        if (lateral && (categories.isEmpty() || size.width < 340.dp || calendarWidth < 7 * minimumCellWidth)) continue
        val heading = maxOf(32f, height(label, 17f, calendarWidth - 64f, 2, true))
        val stacked = (legendKinds.national || national.isNotEmpty()) && legendKinds.other && width(if (national.isEmpty()) "• Feriado" else "3 feriados · amplie", 9f) + 6f + width("• Outras datas", 9f) > calendarWidth - 2f
        val legend = if (legendKinds.national || legendKinds.other || national.isNotEmpty()) legendLine * if (stacked) 2 else 1 else 0f
        val gap = ((available - heading - weekday - weeks * day - legend) / 10f).coerceIn(0f, 1f)
        val calendarHeight = heading + weekday + weeks * day + legend + (8f + if (legend > 0f) 2f else 0f) * gap
        val full = sections(sideWidth)
        val counts = full.map { section -> if (section.category == SidebarCategory.HOLIDAYS) listOf(0, section.rows.size) else (0..section.rows.size).toList() }
        for (first in counts.getOrElse(0) { listOf(0) }) for (second in counts.getOrElse(1) { listOf(0) }) {
            val selected = full.mapIndexedNotNull { index, section ->
                val count = if (index == 0) first else second
                if (count == 0) null else section.copy(rows = section.rows.take(count))
            }
            val sideHeight = selected.sumOf { it.height.toDouble() }.toFloat() + (selected.size - 1).coerceAtLeast(0) * 9f
            val top = if (lateral) maxOf(calendarHeight, sideHeight) else calendarHeight + if (selected.isEmpty()) 0f else 9f + sideHeight
            for (personalCount in 0..allEvents.size) if (top + personalHeight(personalCount) + (if (personalCount > 0) 9f else 0f) <= available + .01f)
                fits += Fit(lateral, calendarWidth, sideWidth, heading, stacked, legend, gap, calendarHeight, selected, personalCount, top)
        }
    }
    // Preserve the personal footer first, then represent both selected categories, then add records.
    val fit = fits.maxWithOrNull(compareBy<Fit> { it.personalCount }.thenBy { it.sections.size }
        .thenBy { it.records }.thenBy { if (it.lateral && it.sections.isNotEmpty()) 1 else 0 })
    val lateral = fit?.let { it.lateral && it.sections.isNotEmpty() } ?: false
    val selectedSections = fit?.sections.orEmpty()
    val nationalRows = selectedSections.firstOrNull { it.category == SidebarCategory.HOLIDAYS }?.rows.orEmpty().map {
        WidgetHolidaySpace(it.holiday!!, it.detail!!, it.detailHeight, it.titleHeight)
    }
    val otherSection = selectedSections.firstOrNull { it.category == SidebarCategory.OTHER_DATES }
    val otherRows = otherSection?.rows.orEmpty().map { WidgetOtherSpace(it.holiday!!, it.title, it.titleHeight) }
    val summary = if (national.isNotEmpty() && nationalRows.isEmpty()) "${national.size} feriados · amplie" else null
    val events = allEvents.take(fit?.personalCount ?: 0)
    val legendHeight = maxOf(fit?.legend ?: (if (legendKinds.national || legendKinds.other) legendLine else 0f), if (summary != null) legendLine else 0f)
    return WidgetSpace(lateral, padding, if (lateral) fit!!.calendarWidth else innerWidth,
        if (lateral) fit!!.sideWidth else innerWidth, label, fit?.heading ?: baseHeading, weekday, day,
        legendHeight, legendLine, fit?.stacked ?: false, "• Outras datas", legendKinds, fit?.gap ?: 0f,
        fit?.top ?: minimum, nationalRows.sumOf { it.height.toDouble() }.toFloat() + (nationalRows.size - 1).coerceAtLeast(0) * 4f,
        eventHeading, national, 4f, summary, nationalRows, otherRows, lateral && otherRows.isNotEmpty(),
        otherSection?.height ?: 0f, otherSection?.headingHeight ?: 0f, events, personalHeight(events.size),
        ceil(personalHeight(events.size) * density) / density, selectedSections, moonSize, dayFont)
}
