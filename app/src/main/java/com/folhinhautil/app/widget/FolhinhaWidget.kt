package com.folhinhautil.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.folhinhautil.app.MainActivity
import com.folhinhautil.app.data.CalendarRepository
import com.folhinhautil.app.data.CalendarSnapshot
import com.folhinhautil.app.domain.*
import kotlinx.coroutines.*
import java.time.*
import kotlin.math.ceil
import kotlin.math.floor

// LocalSize in Responsive mode is one of these bounds, not the launcher's full empty area.
internal val WidgetSizes = setOf(
    DpSize(250.dp, 250.dp),
    DpSize(250.dp, 330.dp),
    DpSize(360.dp, 440.dp),
    DpSize(250.dp, 540.dp),
    DpSize(500.dp, 250.dp),
    DpSize(500.dp, 360.dp),
)

class FolhinhaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val snapshot = CalendarRepository(context).load(month)
        val holidays = if (month.year in FirstYear..LastYear) BrazilianHolidayProvider().forYear(month.year) else emptyList()
        provideContent {
            WidgetContent(month, today, holidays, snapshot)
        }
    }
}

private val Neutral = Color(0xFFE4EAE5)
private val Red = Color(0xFFFF9894)
private val Green = Color(0xFFB6E4BE)
private val Amber = Color(0xFFECC88B)
private val Aqua = Color(0xFF9ED8CB)
private val Divider = Color(0xFF465C50)

private const val MonthTitleGap = 6f
private const val WeekdayGap = 2f
private const val LegendGap = 2f
private const val CalendarGaps = MonthTitleGap + WeekdayGap + LegendGap
private const val SectionGap = 4f
private const val DividerHeight = 1f
private const val SectionSeparation = 2f * SectionGap + DividerHeight
private const val AgendaTitleGap = 3f
private const val EventGap = 5f
private const val HolidayLegend = "• Feriado"
private const val OtherDatesLegend = "• Outras datas"

internal data class WidgetEventSpace(val event: Appointment, val dateHeight: Float, val titleHeight: Float) {
    val height: Float get() = dateHeight + titleHeight
}

internal data class WidgetSpace(
    val horizontal: Boolean,
    val padding: Float,
    val monthTitleHeight: Float,
    val weekdayHeight: Float,
    val dayHeight: Float,
    val legendHeight: Float,
    val titleGap: Float,
    val weekGap: Float,
    val legendGap: Float,
    val monthHeight: Float,
    val calendarSectionHeight: Float,
    val agendaTitleHeight: Float,
    val events: List<WidgetEventSpace>,
    val message: String?,
    val messageHeight: Float,
) {
    val showAgenda: Boolean get() = events.isNotEmpty() || message != null
}

/** Measure with the device's SP conversion (including Android's nonlinear font scaling).
 * Extra padding covers TextView/StaticLayout rounding differences. No event data is logged. */
internal fun widgetSpace(context: Context, size: DpSize, month: YearMonth, snapshot: CalendarSnapshot,
    today: LocalDate, holidays: List<Holiday>): WidgetSpace {
    val density = context.resources.displayMetrics.density
    fun textHeight(text: String, sp: Float, width: Float, lines: Int, bold: Boolean = false): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, floor(width * density).toInt().coerceAtLeast(1))
            .setIncludePad(true).setMaxLines(lines).build()
        // Round in pixels, not whole dp: repeated dp rounding accumulates over six week rows.
        return ceil(layout.height + 2f * density) / density
    }
    val horizontal = size.width >= 500.dp
    var padding = 12f
    var monthWidth = 0f
    var monthTitle = 0f
    var weekday = 0f
    var day = 0f
    var legend = 0f
    var calendarTextHeight = 0f
    fun measureCalendar() {
        monthWidth = size.width.value - 2f * padding - if (horizontal) 226f else 0f
        monthTitle = textHeight(month.label(), 17f, monthWidth, 2, true)
        weekday = WeekLabels.maxOf { textHeight(it, 9f, monthWidth / 7f, 1) }
        day = maxOf(ceil(25f * density) / density, (1..month.lengthOfMonth()).maxOf { number ->
            val date = month.atDay(number)
            textHeight(widgetDayLabel(date, holidays), 13f, monthWidth / 7f, 2, date == today)
        })
        legend = maxOf(textHeight(HolidayLegend, 9f, monthWidth, 1), textHeight(OtherDatesLegend, 9f, monthWidth, 1))
        calendarTextHeight = monthTitle + weekday + monthGrid(month).size * day + legend
    }
    measureCalendar()
    if (calendarTextHeight > size.height.value - 2f * padding) {
        // Six weeks and a large font may need even the outer empty margin in a small widget.
        padding = ((size.height.value - calendarTextHeight) / 2f).coerceIn(0f, 12f)
        measureCalendar()
    }
    val agendaWidth = if (horizontal) 210f else monthWidth
    // Reclaim only empty spacing if a larger font makes the compact month tight.
    val gapScale = ((size.height.value - 2f * padding - calendarTextHeight) / CalendarGaps).coerceIn(0f, 1f)
    val monthHeight = calendarTextHeight + CalendarGaps * gapScale
    val heading = textHeight("Próximos compromissos", 14f, agendaWidth, 2, true)
    val contentHeight = size.height.value - 2f * padding
    val available = contentHeight - if (horizontal) 0f else monthHeight + SectionSeparation
    val agendaEnabled = horizontal || size.height >= 330.dp
    var remaining = available - heading - AgendaTitleGap
    val events = mutableListOf<WidgetEventSpace>()
    if (agendaEnabled) {
        for (event in snapshot.upcoming.take(3)) {
            val space = WidgetEventSpace(event,
                textHeight(event.description(ZoneId.systemDefault()), 10f, agendaWidth, 3),
                textHeight(event.title, 13f, agendaWidth, 2))
            val gap = if (events.isEmpty()) 0f else EventGap
            if (space.height + gap > remaining) break
            events += space
            remaining -= space.height + gap
        }
    }
    val candidate = when {
        !snapshot.permissionGranted -> "Abra o app para permitir a leitura da agenda."
        snapshot.error != null -> snapshot.error
        snapshot.calendars.isEmpty() -> "Nenhum calendário sincronizado. Abra o app."
        snapshot.selectedIds.isEmpty() -> "Escolha suas agendas no app."
        snapshot.upcoming.isEmpty() -> "Sem compromissos nos próximos 90 dias."
        else -> null
    }
    val messageHeight = candidate?.let { textHeight(it, 12f, agendaWidth, 3) } ?: 0f
    val message = candidate?.takeIf { agendaEnabled && messageHeight <= available - heading - AgendaTitleGap }
    val visibleEvents = if (candidate == null) events else emptyList()
    val agendaMinHeight = heading + AgendaTitleGap + if (message != null) messageHeight else
        visibleEvents.sumOf { it.height.toDouble() }.toFloat() + (visibleEvents.size - 1).coerceAtLeast(0) * EventGap
    val showAgenda = visibleEvents.isNotEmpty() || message != null
    // Share surplus between the sections, rather than leaving it below a wrap-content stack.
    // Minimum text heights always win for six weeks or an enlarged font.
    val calendarSectionHeight = if (horizontal || !showAgenda) contentHeight else
        monthHeight + (contentHeight - monthHeight - SectionSeparation - agendaMinHeight).coerceAtLeast(0f) * 0.35f
    return WidgetSpace(horizontal, padding, monthTitle, weekday, day, legend,
        MonthTitleGap * gapScale, WeekdayGap * gapScale, LegendGap * gapScale, monthHeight, calendarSectionHeight, heading,
        visibleEvents, message, messageHeight)
}

private fun widgetDayLabel(date: LocalDate?, holidays: List<Holiday>): String {
    if (date == null) return ""
    val entries = holidays.filter { it.date == date }
    val marker = if (entries.isEmpty()) "" else if (entries.any { it.kind == HolidayKind.NATIONAL }) "•" else "·"
    return "${date.dayOfMonth}$marker"
}

@Composable
internal fun WidgetContent(month: YearMonth, today: LocalDate, holidays: List<Holiday>, snapshot: CalendarSnapshot) {
    val space = widgetSpace(LocalContext.current, LocalSize.current, month, snapshot, today, holidays)
    // Explicit containers matter: a composable function does not create a node on its own.
    Column(GlanceModifier.fillMaxSize().background(Color(0xFF101513)).padding(space.padding.dp)
        .clickable(actionStartActivity<MainActivity>())) {
        if (space.horizontal) {
            Row(GlanceModifier.fillMaxSize()) {
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) { WidgetMonth(month, today, holidays, space) }
                Spacer(GlanceModifier.width(16.dp))
                Column(GlanceModifier.width(210.dp).fillMaxHeight()) {
                    if (space.showAgenda) WidgetAgenda(space, GlanceModifier.fillMaxHeight())
                }
            }
        } else {
            Column(if (space.showAgenda) GlanceModifier.fillMaxWidth().height(space.calendarSectionHeight.dp)
                else GlanceModifier.fillMaxWidth().defaultWeight()) {
                WidgetMonth(month, today, holidays, space)
            }
            if (space.showAgenda) {
                Spacer(GlanceModifier.height(SectionGap.dp))
                Box(GlanceModifier.fillMaxWidth().height(DividerHeight.dp).background(Divider)) {}
                Spacer(GlanceModifier.height(SectionGap.dp))
                WidgetAgenda(space, GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun WidgetMonth(month: YearMonth, today: LocalDate, holidays: List<Holiday>, space: WidgetSpace) {
    // Seven children here; the separate weeks column has at most six rows of seven cells.
    Column(GlanceModifier.fillMaxSize()) {
        Text(month.label(), GlanceModifier.height(space.monthTitleHeight.dp),
            style = TextStyle(color = ColorProvider(Green), fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 2)
        Spacer(GlanceModifier.height(space.titleGap.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            WeekLabels.forEachIndexed { index, label ->
                Text(label, GlanceModifier.defaultWeight().height(space.weekdayHeight.dp),
                    style = TextStyle(color = ColorProvider(if (index == 0 || index == 6) Red else Neutral),
                        fontSize = 9.sp, textAlign = TextAlign.Center), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(space.weekGap.dp))
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
            monthGrid(month).forEach { week ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    week.forEach { date ->
                        val datesHolidays = holidays.filter { it.date == date }
                        val national = datesHolidays.any { it.kind == HolidayKind.NATIONAL }
                        val marker = if (datesHolidays.isEmpty()) "" else if (national) "•" else "·"
                        Box(GlanceModifier.defaultWeight().fillMaxHeight()
                            .background(if (date == today) Color(0xFF34513C) else Color(0xFF101513)), contentAlignment = Alignment.Center) {
                            Text(widgetDayLabel(date, holidays), style = TextStyle(
                                color = ColorProvider(if (date?.isWeekend() == true || national) Red else if (marker.isNotEmpty()) Amber else Neutral),
                                fontSize = 13.sp, fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center), maxLines = 2)
                        }
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(space.legendGap.dp))
        Row(GlanceModifier.fillMaxWidth().height(space.legendHeight.dp)) {
            Text(HolidayLegend, GlanceModifier.height(space.legendHeight.dp),
                style = TextStyle(color = ColorProvider(Red), fontSize = 9.sp), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            Text(OtherDatesLegend, GlanceModifier.defaultWeight().height(space.legendHeight.dp),
                style = TextStyle(color = ColorProvider(Amber), fontSize = 9.sp), maxLines = 1)
        }
    }
}

@Composable
private fun WidgetAgenda(space: WidgetSpace, modifier: GlanceModifier = GlanceModifier) {
    // At most seven children; only the two gaps stretch when three events are visible.
    Column(modifier.fillMaxWidth()) {
        Text("Próximos compromissos", GlanceModifier.height(space.agendaTitleHeight.dp),
            style = TextStyle(color = ColorProvider(Green), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 2)
        Spacer(GlanceModifier.height(AgendaTitleGap.dp))
        if (space.message != null) {
            Text(space.message, GlanceModifier.height(space.messageHeight.dp),
                style = TextStyle(color = ColorProvider(Neutral), fontSize = 12.sp), maxLines = 3)
        } else space.events.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(if (space.events.size == 3) GlanceModifier.defaultWeight() else GlanceModifier.height(EventGap.dp))
            }
            Column(GlanceModifier.fillMaxWidth()) {
                Text(item.event.description(ZoneId.systemDefault()), GlanceModifier.height(item.dateHeight.dp),
                    style = TextStyle(color = ColorProvider(Aqua), fontSize = 10.sp), maxLines = 3)
                Text(item.event.title, GlanceModifier.height(item.titleHeight.dp),
                    style = TextStyle(color = ColorProvider(Neutral), fontSize = 13.sp), maxLines = 2)
            }
        }
    }
}

class FolhinhaWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = FolhinhaWidget() }

class ClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) { FolhinhaWidget().updateAll(context) } }
            catch (_: RuntimeException) { /* The next scheduled update will retry. */ }
            finally { pending.finish() }
        }
    }
}
