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
import com.folhinhautil.app.data.*
import com.folhinhautil.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal val WidgetSizes = setOf(DpSize(250.dp, 250.dp), DpSize(250.dp, 330.dp), DpSize(360.dp, 440.dp),
    DpSize(250.dp, 540.dp), DpSize(500.dp, 250.dp), DpSize(500.dp, 360.dp))

class FolhinhaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes)
    override val stateDefinition = PreferencesGlanceStateDefinition
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Google queries and their refresh mechanism remain based on the current date.
        val snapshot = CalendarRepository(context).load(YearMonth.now())
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
            WidgetContent(month, today, BrazilianHolidayProvider().forYear(month.year), snapshot, personal)
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
private const val GroupGap = 6f
private const val ItemGap = 4f

internal data class WidgetHolidaySpace(val holiday: Holiday, val text: String, val height: Float)
internal data class WidgetEventSpace(val item: WidgetNextEvent, val titleHeight: Float, val detailHeight: Float, val dateHeight: Float = 0f) {
    val height get() = titleHeight + detailHeight + dateHeight
}
internal data class WidgetSpace(
    val horizontal: Boolean, val padding: Float, val monthLabel: String, val monthTitleHeight: Float,
    val weekdayHeight: Float, val dayHeight: Float, val legendHeight: Float, val legendLabel: String, val gapScale: Float,
    val monthHeight: Float, val holidayTitleHeight: Float, val eventTitleHeight: Float,
    val nationalHolidays: List<WidgetHolidaySpace>, val events: List<WidgetEventSpace>, val footerHeight: Float,
) { val showAgenda get() = nationalHolidays.isNotEmpty() || events.isNotEmpty() }

private fun widgetTypeface(context: Context, bold: Boolean): Typeface {
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        val adjustment = context.resources.configuration.fontWeightAdjustment.takeIf { it in -1000..1000 } ?: 0
        return Typeface.create(Typeface.DEFAULT, ((if (bold) 700 else 400) + adjustment).coerceIn(1, 1000), false)
    }
    return Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
}
internal fun widgetSpace(context: Context, size: DpSize, month: YearMonth, snapshot: CalendarSnapshot,
    today: LocalDate, holidays: List<Holiday>, personal: List<PersonalEvent> = emptyList()): WidgetSpace {
    val density = context.resources.displayMetrics.density
    fun textHeight(text: String, sp: Float, width: Float, lines: Int, bold: Boolean = false): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
            typeface = widgetTypeface(context, bold)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, floor(width * density).toInt().coerceAtLeast(1))
            .setIncludePad(true).setMaxLines(lines).build()
        return ceil(layout.height + 2f * density) / density
    }
    val horizontal = size.width >= 500.dp
    var padding = 12f
    var width = 0f
    var label = month.label()
    var heading = 0f
    var weekday = 0f
    var day = 0f
    var legend = 0f
    var textTotal = 0f
    fun measureMonth() {
        width = size.width.value - 2 * padding - if (horizontal) 226f else 0f
        label = if (width < 300f) month.format(DateTimeFormatter.ofPattern("MMM yyyy", BrazilianLocale))
            .replaceFirstChar { it.titlecase(BrazilianLocale) } else month.label()
        heading = maxOf(32f, textHeight(label, 17f, width - 64f, 2, true))
        weekday = WeekLabels.maxOf { textHeight(it, 9f, width / 7, 1) }
        day = maxOf(ceil(25 * density) / density, (1..month.lengthOfMonth()).maxOf {
            textHeight(widgetDayLabel(month.atDay(it), holidays), 13f, width / 7, 2, month.atDay(it) == today)
        })
        legend = maxOf(textHeight("• Feriado", 9f, width, 1), textHeight("• Outras datas", 9f, width, 1))
        textTotal = heading + weekday + monthGrid(month).size * day + legend
    }
    measureMonth()
    if (textTotal > size.height.value - padding * 2) {
        padding = ((size.height.value - textTotal) / 2).coerceIn(0f, 12f)
        measureMonth()
    }
    val legendPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9f, context.resources.displayMetrics)
        typeface = widgetTypeface(context, false)
    }
    val fullLegend = "• Pontos facultativos e outras datas"
    val legendLabel = if (legendPaint.measureText("• Feriado") + legendPaint.measureText(fullLegend) +
        12f * density <= width * density) fullLegend else "• Outras datas"
    val gapScale = ((size.height.value - 2 * padding - textTotal) / 10f).coerceIn(0f, 1f)
    val monthHeight = textTotal + 10 * gapScale
    val footerWidth = if (horizontal) 210f else width
    val holidayHeading = textHeight("Próximos feriados", 14f, footerWidth, 2, true)
    val eventHeading = textHeight("Próximos eventos", 14f, footerWidth, 2, true)
    val enabled = horizontal || size.height >= 330.dp
    var remaining = if (!enabled) 0f else size.height.value - 2 * padding - 1f -
        if (horizontal) 0f else monthHeight + SectionSeparation
    val national = futureNationalHolidays(today)
    val holidayRows = mutableListOf<WidgetHolidaySpace>()
    var used = 0f
    for (holiday in national.take(2)) {
        val text = "${holiday.date.format(DateTimeFormatter.ofPattern("dd/MM"))} · ${holiday.name}"
        val height = textHeight(text, 11f, footerWidth, 2)
        val cost = height + if (holidayRows.isEmpty()) holidayHeading + 3f else ItemGap
        if (cost > remaining) break
        holidayRows += WidgetHolidaySpace(holiday, text, height)
        used += cost; remaining -= cost
    }
    val eventRows = mutableListOf<WidgetEventSpace>()
    for (item in nextWidgetEvents(personal, snapshot.upcoming, today, ZoneId.systemDefault(), national)) {
        val measured = when (item) {
            is WidgetNextEvent.Calendar -> WidgetEventSpace(item,
                textHeight(item.title, 13f, footerWidth, 2), textHeight(item.appointment.description(ZoneId.systemDefault()), 10f, footerWidth, 3))
            is WidgetNextEvent.Personal -> WidgetEventSpace(item, textHeight(item.title, 13f, footerWidth, 2),
                textHeight(item.occurrence.countdown, 11f, footerWidth, 1), textHeight(formatPersonalDate(item.occurrence.date), 10f, footerWidth, 1))
        }
        val cost = measured.height + if (eventRows.isEmpty()) eventHeading + 3f +
            (if (holidayRows.isEmpty()) 0f else GroupGap) else ItemGap
        if (cost > remaining) continue
        eventRows += measured; used += cost; remaining -= cost
    }
    return WidgetSpace(horizontal, padding, label, heading, weekday, day, legend, legendLabel, gapScale, monthHeight,
        holidayHeading, eventHeading, holidayRows, eventRows, ceil(used * density) / density)
}

private fun widgetDayLabel(date: LocalDate?, holidays: List<Holiday>): String {
    if (date == null) return ""
    val entries = holidays.filter { it.date == date }
    val marker = if (entries.isEmpty()) "" else if (entries.any { it.kind == HolidayKind.NATIONAL }) "•" else "·"
    return "${date.dayOfMonth}$marker"
}

@Composable
internal fun WidgetContent(month: YearMonth, today: LocalDate, holidays: List<Holiday>, snapshot: CalendarSnapshot,
    personal: List<PersonalEvent> = emptyList()) {
    val space = widgetSpace(LocalContext.current, LocalSize.current, month, snapshot, today, holidays, personal)
    Column(GlanceModifier.fillMaxSize().background(Color(0xFF101513)).padding(space.padding.dp).clickable(actionStartActivity<MainActivity>())) {
        if (space.horizontal) {
            Row(GlanceModifier.fillMaxSize()) {
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) { WidgetMonth(month, today, holidays, space) }
                Spacer(GlanceModifier.width(16.dp))
                Column(GlanceModifier.width(210.dp)) { if (space.showAgenda) WidgetFooter(space) }
            }
        } else {
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) { WidgetMonth(month, today, holidays, space) }
            if (space.showAgenda) {
                Spacer(GlanceModifier.height(4.dp))
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Divider)) {}
                Spacer(GlanceModifier.height(4.dp))
                WidgetFooter(space)
            }
        }
    }
}

@Composable
private fun WidgetMonth(month: YearMonth, today: LocalDate, holidays: List<Holiday>, space: WidgetSpace) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth().height(space.monthTitleHeight.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Spacer(GlanceModifier.height((6f * space.gapScale).dp))
        Row(GlanceModifier.fillMaxWidth()) {
            WeekLabels.forEachIndexed { index, label -> Text(label, GlanceModifier.defaultWeight().height(space.weekdayHeight.dp),
                style = TextStyle(color = ColorProvider(if (index == 0 || index == 6) Red else Neutral), fontSize = 9.sp, textAlign = TextAlign.Center), maxLines = 1) }
        }
        Spacer(GlanceModifier.height((2f * space.gapScale).dp))
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
            monthGrid(month).forEach { week -> Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                week.forEach { date ->
                    val entries = holidays.filter { it.date == date }
                    val national = entries.any { it.kind == HolidayKind.NATIONAL }
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().background(if (date == today) Color(0xFF34513C) else Color(0xFF101513)), contentAlignment = Alignment.Center) {
                        Text(widgetDayLabel(date, holidays), style = TextStyle(color = ColorProvider(if (date?.isWeekend() == true || national) Red
                            else if (entries.isNotEmpty()) Amber else Neutral), fontSize = 13.sp,
                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center), maxLines = 2)
                    }
                }
            } }
        }
        Spacer(GlanceModifier.height((2f * space.gapScale).dp))
        Row(GlanceModifier.fillMaxWidth().height(space.legendHeight.dp)) {
            Text("• Feriado", GlanceModifier.height(space.legendHeight.dp), style = TextStyle(color = ColorProvider(Red), fontSize = 9.sp), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            Text(space.legendLabel, GlanceModifier.defaultWeight().height(space.legendHeight.dp), style = TextStyle(color = ColorProvider(Amber), fontSize = 9.sp), maxLines = 1)
        }
    }
}

@Composable
private fun WidgetFooter(space: WidgetSpace) {
    Column(GlanceModifier.fillMaxWidth().height(space.footerHeight.dp)) {
        if (space.nationalHolidays.isNotEmpty()) Column(GlanceModifier.fillMaxWidth()) {
            Text("Próximos feriados", GlanceModifier.height(space.holidayTitleHeight.dp), style = TextStyle(color = ColorProvider(Green), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            Spacer(GlanceModifier.height(3.dp))
            Column { space.nationalHolidays.forEachIndexed { index, item ->
                if (index > 0) Spacer(GlanceModifier.height(ItemGap.dp))
                Text(item.text, GlanceModifier.height(item.height.dp), style = TextStyle(color = ColorProvider(Red), fontSize = 11.sp), maxLines = 2)
            } }
        }
        if (space.nationalHolidays.isNotEmpty() && space.events.isNotEmpty()) Spacer(GlanceModifier.height(GroupGap.dp))
        if (space.events.isNotEmpty()) Column(GlanceModifier.fillMaxWidth()) {
            Text("Próximos eventos", GlanceModifier.height(space.eventTitleHeight.dp), style = TextStyle(color = ColorProvider(Green), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            Spacer(GlanceModifier.height(3.dp))
            Column(GlanceModifier.fillMaxWidth()) { space.events.forEachIndexed { index, item ->
                if (index > 0) Spacer(GlanceModifier.height(ItemGap.dp))
                Column(GlanceModifier.fillMaxWidth()) { when (val value = item.item) {
                    is WidgetNextEvent.Calendar -> {
                        Text(value.appointment.description(ZoneId.systemDefault()), GlanceModifier.height(item.detailHeight.dp), style = TextStyle(color = ColorProvider(Aqua), fontSize = 10.sp), maxLines = 3)
                        Text(value.title, GlanceModifier.height(item.titleHeight.dp), style = TextStyle(color = ColorProvider(Neutral), fontSize = 13.sp), maxLines = 2)
                    }
                    is WidgetNextEvent.Personal -> {
                        Text(value.title, GlanceModifier.height(item.titleHeight.dp), style = TextStyle(color = ColorProvider(Neutral), fontSize = 13.sp), maxLines = 2)
                        Text(value.occurrence.countdown, GlanceModifier.height(item.detailHeight.dp), style = TextStyle(color = ColorProvider(Amber), fontSize = 11.sp), maxLines = 1)
                        Text(formatPersonalDate(value.occurrence.date), GlanceModifier.height(item.dateHeight.dp), style = TextStyle(color = ColorProvider(Aqua), fontSize = 10.sp), maxLines = 1)
                    }
                } }
            } }
        }
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
