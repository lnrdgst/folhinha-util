package com.folhinhautil.app.widget

import android.content.*
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.Preferences
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.*
import androidx.glance.semantics.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.folhinhautil.app.MainActivity
import com.folhinhautil.app.R
import com.folhinhautil.app.data.PersonalEventsRepository
import com.folhinhautil.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal val WidgetSizes = setOf(DpSize(250.dp, 250.dp), DpSize(250.dp, 330.dp), DpSize(360.dp, 440.dp),
    DpSize(400.dp, 440.dp), DpSize(400.dp, 450.dp), DpSize(250.dp, 540.dp), DpSize(500.dp, 250.dp), DpSize(500.dp, 360.dp))

class FolhinhaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes)
    override val stateDefinition = PreferencesGlanceStateDefinition
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // The widget now presents local personal events. Android calendars remain in MainActivity.
        val personalFlow = PersonalEventsRepository(context).events.catch {
            if (it is CancellationException) throw it
            emit(emptyList())
        }
        val initialPersonal = personalFlow.first()
        provideContent {
            val prefs = currentState<Preferences>()
            val personal by personalFlow.collectAsState(initialPersonal)
            val today = LocalDate.now()
            val month = widgetMonth(prefs[ViewedMonth], today)
            WidgetContent(month, today, BrazilianHolidayProvider().forYear(month.year), personal)
        }
    }
}

private val Neutral = Color(0xFFE4EAE5)
private val Red = Color(0xFFFF9894)
private val Green = Color(0xFFB6E4BE)
private val Amber = Color(0xFFECC88B)
private val Aqua = Color(0xFF9ED8CB)
private val Divider = Color(0xFF465C50)
private const val SectionSeparation = 9f
private const val SideSeparation = 17f

internal data class WidgetHolidaySpace(val holiday: Holiday, val dateLabel: String, val dateHeight: Float, val nameHeight: Float) {
    val height get() = dateHeight + nameHeight
}
internal data class WidgetOtherSpace(val holiday: Holiday, val text: String, val height: Float)
internal data class WidgetPersonalSpace(val occurrence: PersonalOccurrence, val titleHeight: Float, val detailHeight: Float, val dateHeight: Float) {
    val height get() = titleHeight + detailHeight + dateHeight
}
internal data class WidgetSpace(
    val sideBySide: Boolean, val padding: Float, val calendarWidth: Float, val holidayWidth: Float,
    val monthLabel: String, val monthTitleHeight: Float, val weekdayHeight: Float, val dayHeight: Float,
    val legendHeight: Float, val legendLineHeight: Float, val legendStacked: Boolean, val legendLabel: String, val legend: MonthLegend, val gapScale: Float, val topMinimumHeight: Float,
    val holidaySectionHeight: Float, val eventTitleHeight: Float,
    val selectedHolidays: List<Holiday>, val holidayGap: Float, val holidaySummary: String?,
    val nationalHolidays: List<WidgetHolidaySpace>, val otherDates: List<WidgetOtherSpace>, val otherDatesInSidebar: Boolean,
    val otherSectionHeight: Float, val otherTitleHeight: Float, val events: List<WidgetPersonalSpace>, val personalSectionHeight: Float, val footerHeight: Float,
)

private fun widgetTypeface(context: Context, bold: Boolean): Typeface {
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        val adjustment = context.resources.configuration.fontWeightAdjustment.takeIf { it in -1000..1000 } ?: 0
        return Typeface.create(Typeface.DEFAULT, ((if (bold) 700 else 400) + adjustment).coerceIn(1, 1000), false)
    }
    return Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
}

internal fun widgetSpace(context: Context, size: DpSize, month: YearMonth, today: LocalDate,
    holidays: List<Holiday>, personal: List<PersonalEvent> = emptyList(),
    holidayProvider: HolidayProvider = BrazilianHolidayProvider()): WidgetSpace {
    val density = context.resources.displayMetrics.density
    fun paint(sp: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
        typeface = widgetTypeface(context, bold)
    }
    fun height(text: String, sp: Float, width: Float, lines: Int = Int.MAX_VALUE, bold: Boolean = false): Float {
        // RemoteViews rounds each weighted child independently; avoid a one-pixel wrapping cliff.
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint(sp, bold), floor((width - 2f).coerceAtLeast(1f) * density).toInt().coerceAtLeast(1))
            .setIncludePad(true).setMaxLines(lines).build()
        return ceil(layout.height + 2f * density) / density
    }
    fun width(text: String, sp: Float, bold: Boolean = false) = paint(sp, bold).measureText(text) / density
    val label = if (size.width < 300.dp) month.format(DateTimeFormatter.ofPattern("MMM yyyy", BrazilianLocale))
        .replaceFirstChar { it.titlecase(BrazilianLocale) } else month.label()
    var padding = 12f
    var innerWidth = size.width.value - padding * 2
    val weeks = monthGrid(month).size
    val day = maxOf(ceil(25 * density) / density, (1..month.lengthOfMonth()).maxOf {
        height(widgetDayLabel(month.atDay(it), holidays), 13f, innerWidth / 7, 1, month.atDay(it) == today)
    })
    val weekday = WeekLabels.maxOf { height(it, 9f, innerWidth / 7, 1) }
    val legendKinds = monthLegend(month, holidays)
    val legendLine = height("• Outras datas", 9f, innerWidth, 1)
    var legend = if (legendKinds.national || legendKinds.other) legendLine else 0f
    var legendStacked = false
    val baseHeading = maxOf(32f, height(label, 17f, innerWidth - 64f, 2, true))
    val textMinimum = baseHeading + weekday + weeks * day + legend
    if (textMinimum > size.height.value - 2 * padding) {
        padding = ((size.height.value - textMinimum) / 2).coerceIn(0f, 12f)
        innerWidth = size.width.value - padding * 2
    }
    var available = size.height.value - 2 * padding
    var gapScale = ((available - textMinimum) / (if (legend > 0f) 10f else 8f)).coerceIn(0f, 1f)
    var gridMinimum = weekday + 2f * gapScale + weeks * day
    val fullLegend = "• Pontos facultativos e outras datas"
    var legendLabel = if ((if (legendKinds.national) width("• Feriado", 9f) + 6f else 0f) + width(fullLegend, 9f) + 12f <= innerWidth) fullLegend else "• Outras datas"
    val candidates = widgetNationalHolidays(month, today, holidayProvider)
    val desiredHolidayWidth = (innerWidth * .34f).coerceIn(110f, 170f)
    val sideCalendarWidth = innerWidth - desiredHolidayWidth - SideSeparation
    val minimumCellWidth = maxOf(WeekLabels.maxOf { width(it, 9f) },
        (1..month.lengthOfMonth()).maxOf { width(widgetDayLabel(month.atDay(it), holidays), 13f, month.atDay(it) == today) }) + 2f
    var sideBySide = candidates.isNotEmpty() && size.width >= 340.dp && sideCalendarWidth >= 7 * minimumCellWidth
    var calendarWidth = if (sideBySide) sideCalendarWidth else innerWidth
    var heading = maxOf(32f, height(label, 17f, calendarWidth - 64f, 2, true))
    var holidayWidth = if (sideBySide) desiredHolidayWidth else innerWidth
    var holidayGap = if (candidates.size > 2) 4f else 10f
    var holidayRows = emptyList<WidgetHolidaySpace>()
    var holidaySummary: String? = null
    fun holidayHeight(rows: List<WidgetHolidaySpace>): Float =
        if (rows.isEmpty()) 0f else rows.sumOf { it.height.toDouble() }.toFloat() + (rows.size - 1) * holidayGap
    fun calendarHeight() = heading + (6f + if (legend > 0f) 2f else 0f) * gapScale + gridMinimum + legend
    fun configureLegend() {
        legendLabel = if (sideBySide) "• Outras datas" else if (
            (if (legendKinds.national) width("• Feriado", 9f) + 6f else 0f) + width(fullLegend, 9f) + 12f <= calendarWidth) fullLegend else "• Outras datas"
        legendStacked = sideBySide && legendKinds.national && legendKinds.other &&
            width("• Feriado", 9f) + 6f + width(legendLabel, 9f) > calendarWidth - 2f
        legend = if (!legendKinds.national && !legendKinds.other) 0f else legendLine * if (legendStacked) 2 else 1
    }
    configureLegend()
    // Each side owns its full height: the right column starts beside the month header.
    fun topHeight(rows: List<WidgetHolidaySpace>): Float = if (sideBySide)
        maxOf(calendarHeight(), holidayHeight(rows))
        else calendarHeight() + if (rows.isEmpty()) 0f else SectionSeparation + holidayHeight(rows)
    fun measureHolidays() = candidates.map { holiday ->
        val dateLabel = holiday.date.format(DateTimeFormatter.ofPattern(if (holiday.date.year != month.year) "dd/MM/yyyy" else "dd/MM"))
        WidgetHolidaySpace(holiday, dateLabel, height(dateLabel, 10f, holidayWidth, 1), height(holiday.name, 11f, holidayWidth))
    }
    val gaps = if (candidates.size > 2) listOf(4f, 2f, 0f) else listOf(10f, 4f, 2f, 0f)
    fun fitCompleteList(): Boolean {
        val rows = measureHolidays()
        for (gap in gaps) {
            holidayGap = gap
            if (topHeight(rows) <= available) { holidayRows = rows; return true }
        }
        return false
    }
    var fits = fitCompleteList()
    // First compact the gaps, then widen the sidebar without shrinking the calendar numbers.
    if (!fits && sideBySide) {
        calendarWidth = 7 * minimumCellWidth
        holidayWidth = innerWidth - calendarWidth - SideSeparation
        heading = maxOf(32f, height(label, 17f, calendarWidth - 64f, 2, true))
        configureLegend()
        fits = fitCompleteList()
    }
    if (!fits) {
        sideBySide = false; calendarWidth = innerWidth; holidayWidth = innerWidth
        heading = baseHeading
        configureLegend()
        fits = fitCompleteList()
    }
    if (!fits) {
        // Keep the complete selection. On very small widgets, reuse the legend strip for an
        // explicit summary instead of silently dropping names or reducing the calendar font.
        holidaySummary = "${candidates.size} feriados · amplie"
        holidayRows = emptyList()
        sideBySide = false
        legend = maxOf(legend, height(holidaySummary, 9f, innerWidth, 1))
        val minimum = baseHeading + weekday + weeks * day + legend
        padding = minOf(padding, ((size.height.value - minimum) / 2).coerceIn(0f, 12f))
        innerWidth = size.width.value - padding * 2
        available = size.height.value - padding * 2
        gapScale = ((available - minimum) / 10f).coerceIn(0f, 1f)
        gridMinimum = weekday + 2f * gapScale + weeks * day
        calendarWidth = innerWidth; holidayWidth = innerWidth
        heading = maxOf(32f, height(label, 17f, calendarWidth - 64f, 2, true))
        val remainingLegendWidth = innerWidth - width(holidaySummary, 9f) - 12f
        legendLabel = if (width(fullLegend, 9f) <= remainingLegendWidth) fullLegend
            else if (width("• Outras datas", 9f) <= remainingLegendWidth) "• Outras datas" else "• Outras"
    }
    val otherCandidates = widgetOtherDates(month)
    fun otherRows(sidebar: Boolean) = otherCandidates.map { holiday ->
        val text = "${holiday.date.format(DateTimeFormatter.ofPattern("dd/MM"))} · ${holiday.name}"
        WidgetOtherSpace(holiday, text, height(text, 11f, if (sidebar) holidayWidth else innerWidth))
    }
    val eventHeading = height("Próximos eventos pessoais", 14f, innerWidth, 2, true)
    val allEvents = upcomingPersonalEvents(personal, today).take(2).map { occurrence ->
        WidgetPersonalSpace(occurrence, height(occurrence.event.title, 13f, innerWidth, 2),
            height(occurrence.countdown, 11f, innerWidth, 1), height(formatPersonalDate(occurrence.date), 10f, innerWidth, 1))
    }
    fun otherTitleHeight(sidebar: Boolean) = height("Pontos facultativos e outras datas", 10f, if (sidebar) holidayWidth else innerWidth, bold = true)
    fun otherHeight(count: Int, sidebar: Boolean) = if (count == 0) 0f else
        otherTitleHeight(sidebar) + 4f + otherRows(sidebar).take(count).sumOf { it.height.toDouble() }.toFloat() + (count - 1) * 4f
    fun personalHeight(count: Int) = if (count == 0) 0f else eventHeading + 6f +
        allEvents.take(count).sumOf { it.height.toDouble() }.toFloat() + (count - 1) * SectionSeparation
    data class Placement(val other: Int, val personal: Int, val sidebar: Boolean)
    fun topHeight(placement: Placement): Float {
        if (!placement.sidebar || placement.other == 0) return topHeight(holidayRows)
        return maxOf(calendarHeight(), holidayHeight(holidayRows) + SectionSeparation + otherHeight(placement.other, true))
    }
    fun footerHeight(placement: Placement): Float = personalHeight(placement.personal) +
        (if (placement.sidebar) 0f else otherHeight(placement.other, false)) +
        (if (!placement.sidebar && placement.other > 0 && placement.personal > 0) SectionSeparation else 0f)
    fun hasFooter(placement: Placement) = placement.personal > 0 || (!placement.sidebar && placement.other > 0)
    val placements = listOf(sideBySide && holidayRows.isNotEmpty()).flatMap { sidebar ->
        (0..otherCandidates.size).flatMap { other -> (0..allEvents.size).map { Placement(other, it, sidebar) } }
    }
    // On lateral layouts, other dates always stay on the right. Only small stacked variants use a footer.
    val placement = placements.filter { topHeight(it) + footerHeight(it) + (if (hasFooter(it)) SectionSeparation else 0f) <= available }
        .maxWithOrNull(compareBy<Placement> {
            if ((otherCandidates.isEmpty() || it.other > 0) && (allEvents.isEmpty() || it.personal > 0)) 1 else 0
        }.thenBy { it.other }.thenBy { it.personal }) ?: Placement(0,0,false)
    val other = otherRows(placement.sidebar).take(placement.other)
    val events = allEvents.take(placement.personal)
    return WidgetSpace(sideBySide, padding, calendarWidth, holidayWidth, label, heading, weekday, day,
        legend, legendLine, legendStacked, legendLabel, legendKinds, gapScale, topHeight(placement), holidayHeight(holidayRows), eventHeading,
        candidates, holidayGap, holidaySummary, holidayRows, other, placement.sidebar && other.isNotEmpty(), otherHeight(placement.other, placement.sidebar), if (other.isEmpty()) 0f else otherTitleHeight(placement.sidebar),
        events, personalHeight(placement.personal), ceil(footerHeight(placement) * density) / density)

}

private fun widgetDayLabel(date: LocalDate?, holidays: List<Holiday>): String {
    if (date == null) return ""
    val entries = holidays.filter { it.date == date }
    val marker = if (entries.isEmpty()) "" else if (entries.any { it.kind == HolidayKind.NATIONAL }) "•" else "·"
    return "${date.dayOfMonth}$marker"
}

@Composable
internal fun WidgetContent(month: YearMonth, today: LocalDate, holidays: List<Holiday>, personal: List<PersonalEvent> = emptyList(),
    holidayProvider: HolidayProvider = BrazilianHolidayProvider()) {
    val space = widgetSpace(LocalContext.current, LocalSize.current, month, today, holidays, personal, holidayProvider)
    Column(GlanceModifier.fillMaxSize().background(Color(0xFF101513)).padding(space.padding.dp).clickable(actionStartActivity<MainActivity>())) {
        if (space.sideBySide) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) { WidgetCalendar(month, today, holidays, space) }
                Spacer(GlanceModifier.width(8.dp))
                Box(GlanceModifier.width(1.dp).fillMaxHeight().background(Divider)) {}
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.width(space.holidayWidth.dp)) {
                    WidgetHolidays(space)
                    if (space.otherDatesInSidebar) { WidgetSeparator(); WidgetOtherDates(space) }
                }
            }
        } else {
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) { WidgetCalendar(month, today, holidays, space) }
                if (space.nationalHolidays.isNotEmpty()) { WidgetSeparator(); WidgetHolidays(space) }
            }
        }
        if ((!space.otherDatesInSidebar && space.otherDates.isNotEmpty()) || space.events.isNotEmpty()) {
            WidgetSeparator()
            Column(GlanceModifier.fillMaxWidth().height(space.footerHeight.dp)) {
                if (!space.otherDatesInSidebar && space.otherDates.isNotEmpty()) WidgetOtherDates(space)
                if (!space.otherDatesInSidebar && space.otherDates.isNotEmpty() && space.events.isNotEmpty()) WidgetSeparator()
                if (space.events.isNotEmpty()) WidgetPersonalEvents(space)
            }
        }
    }
}

@Composable
private fun ColumnScope.WidgetCalendar(month: YearMonth, today: LocalDate, holidays: List<Holiday>, space: WidgetSpace) {
    WidgetHeader(month, space)
    Spacer(GlanceModifier.height((6 * space.gapScale).dp))
    Column(GlanceModifier.fillMaxWidth().defaultWeight()) { WidgetGrid(month, today, holidays, space) }
    if (space.legendHeight > 0) Spacer(GlanceModifier.height((2 * space.gapScale).dp))
    WidgetLegend(space)
}

@Composable
private fun WidgetHeader(month: YearMonth, space: WidgetSpace) {
    Row(GlanceModifier.width(space.calendarWidth.dp).height(space.monthTitleHeight.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(32.dp).fillMaxHeight().then(if (month > YearMonth.of(FirstYear, 1))
            GlanceModifier.clickable(actionRunCallback<NavigateMonthAction>(actionParametersOf(MonthDelta to -1))) else GlanceModifier), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_month_previous), "Mês anterior", GlanceModifier.size(20.dp))
        }
        Text(space.monthLabel, GlanceModifier.defaultWeight().fillMaxHeight()
            .clickable(actionRunCallback<NavigateMonthAction>(actionParametersOf(MonthDelta to 0)))
            .semantics { contentDescription = "${month.label()}. Voltar ao mês atual" },
            style = TextStyle(color = ColorProvider(Green), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 2)
        Box(GlanceModifier.width(32.dp).fillMaxHeight().then(if (month < YearMonth.of(LastYear, 12))
            GlanceModifier.clickable(actionRunCallback<NavigateMonthAction>(actionParametersOf(MonthDelta to 1))) else GlanceModifier), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_month_next), "Próximo mês", GlanceModifier.size(20.dp))
        }
    }
}

@Composable
private fun WidgetGrid(month: YearMonth, today: LocalDate, holidays: List<Holiday>, space: WidgetSpace) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth()) {
            WeekLabels.forEachIndexed { index, label -> Text(label, GlanceModifier.defaultWeight().height(space.weekdayHeight.dp),
                style = TextStyle(color = ColorProvider(if (index == 0 || index == 6) Red else Neutral), fontSize = 9.sp, textAlign = TextAlign.Center), maxLines = 1) }
        }
        Spacer(GlanceModifier.height((2 * space.gapScale).dp))
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
            monthGrid(month).forEach { week -> Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                week.forEach { date ->
                    val entries = holidays.filter { it.date == date }
                    val national = entries.any { it.kind == HolidayKind.NATIONAL }
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().background(if (date == today) Color(0xFF34513C) else Color(0xFF101513)), contentAlignment = Alignment.Center) {
                        Text(widgetDayLabel(date, holidays), style = TextStyle(color = ColorProvider(if (date?.isWeekend() == true || national) Red
                            else if (entries.isNotEmpty()) Amber else Neutral), fontSize = 13.sp,
                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center), maxLines = 1)
                    }
                }
            } }
        }
    }
}

@Composable
private fun WidgetLegend(space: WidgetSpace) {
    if (space.legendHeight <= 0) return
    if (space.legendStacked) Column(GlanceModifier.fillMaxWidth().height(space.legendHeight.dp)) {
        WidgetNationalLegend(space)
        WidgetOtherLegend(space, GlanceModifier.fillMaxWidth())
    } else Row(GlanceModifier.fillMaxWidth().height(space.legendHeight.dp)) {
        WidgetNationalLegend(space)
        if ((space.legend.national || space.holidaySummary != null) && space.legend.other) Spacer(GlanceModifier.width(6.dp))
        WidgetOtherLegend(space, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun WidgetNationalLegend(space: WidgetSpace) {
    if (space.holidaySummary != null) {
        val description = space.selectedHolidays.joinToString("; ") { "${formatPersonalDate(it.date)}: ${it.name}" } +
            ". Amplie o widget para ver a lista completa de feriados."
        Text(space.holidaySummary, GlanceModifier.height(space.legendLineHeight.dp).semantics { contentDescription = description },
            style = TextStyle(color = ColorProvider(Red), fontSize = 9.sp), maxLines = 1)
    } else if (space.legend.national) Text("• Feriado", GlanceModifier.height(space.legendLineHeight.dp),
        style = TextStyle(color = ColorProvider(Red), fontSize = 9.sp), maxLines = 1)
}

@Composable
private fun WidgetOtherLegend(space: WidgetSpace, modifier: GlanceModifier) {
    if (space.legend.other) Text(space.legendLabel, modifier.height(space.legendLineHeight.dp),
        style = TextStyle(color = ColorProvider(Amber), fontSize = 9.sp), maxLines = 1)
}

@Composable
private fun WidgetHolidays(space: WidgetSpace) {
    Column(GlanceModifier.fillMaxWidth().height(space.holidaySectionHeight.dp)) {
        WidgetHolidayRows(space.nationalHolidays, space.holidayGap)
    }
}

/** At most seven direct children at every level, regardless of the selected holiday count. */
@Composable
private fun WidgetHolidayRows(rows: List<WidgetHolidaySpace>, gap: Float) {
    Column(GlanceModifier.fillMaxWidth()) {
        if (rows.size <= 4) rows.forEachIndexed { index, item ->
            if (index > 0) Spacer(GlanceModifier.height(gap.dp))
            Column(GlanceModifier.fillMaxWidth()) {
                Text(item.dateLabel, GlanceModifier.height(item.dateHeight.dp), style = TextStyle(color = ColorProvider(Red), fontSize = 10.sp), maxLines = 1)
                Text(item.holiday.name, GlanceModifier.height(item.nameHeight.dp).semantics { contentDescription = "${item.holiday.name}, ${item.holiday.kind.label}" }, style = TextStyle(color = ColorProvider(Neutral), fontSize = 11.sp))
            }
        } else rows.chunked((rows.size + 3) / 4).forEachIndexed { index, group ->
            if (index > 0) Spacer(GlanceModifier.height(gap.dp))
            WidgetHolidayRows(group, gap)
        }
    }
}

@Composable
private fun WidgetOtherDates(space: WidgetSpace) {
    Column(GlanceModifier.fillMaxWidth().height(space.otherSectionHeight.dp)) {
        Text("Pontos facultativos e outras datas", GlanceModifier.fillMaxWidth().height(space.otherTitleHeight.dp),
            style = TextStyle(color = ColorProvider(Amber), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(4.dp))
        Column(GlanceModifier.fillMaxWidth()) { space.otherDates.forEachIndexed { index, item ->
            if (index > 0) Spacer(GlanceModifier.height(4.dp))
            Text(item.text, GlanceModifier.fillMaxWidth().height(item.height.dp).semantics {
                contentDescription = "${item.text}, ${item.holiday.kind.label}"
            }, style = TextStyle(color = ColorProvider(Amber), fontSize = 11.sp))
        } }
    }
}

@Composable
private fun WidgetPersonalEvents(space: WidgetSpace) {
    Column(GlanceModifier.fillMaxWidth().height(space.personalSectionHeight.dp)) {
        Text("Próximos eventos pessoais", GlanceModifier.height(space.eventTitleHeight.dp), style = TextStyle(color = ColorProvider(Green), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 2)
        Spacer(GlanceModifier.height(6.dp))
        Column(GlanceModifier.fillMaxWidth()) { space.events.forEachIndexed { index, item ->
            if (index > 0) WidgetSeparator()
            Column(GlanceModifier.fillMaxWidth()) {
                Text(item.occurrence.event.title, GlanceModifier.height(item.titleHeight.dp), style = TextStyle(color = ColorProvider(Neutral), fontSize = 13.sp), maxLines = 2)
                Text(item.occurrence.countdown, GlanceModifier.height(item.detailHeight.dp), style = TextStyle(color = ColorProvider(Aqua), fontSize = 11.sp), maxLines = 1)
                Text(formatPersonalDate(item.occurrence.date), GlanceModifier.height(item.dateHeight.dp), style = TextStyle(color = ColorProvider(Neutral), fontSize = 10.sp), maxLines = 1)
            }
        } }
    }
}

/** An explicit container keeps separators from adding three children to their caller. */
@Composable
private fun WidgetSeparator() {
    Column(GlanceModifier.fillMaxWidth().height(SectionSeparation.dp)) {
        Spacer(GlanceModifier.height(4.dp))
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Divider)) {}
        Spacer(GlanceModifier.height(4.dp))
    }
}

class FolhinhaWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = FolhinhaWidget() }
class ClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) { refreshWidgetsForClock(context) } }
            catch (_: RuntimeException) { }
            finally { pending.finish() }
        }
    }
}
