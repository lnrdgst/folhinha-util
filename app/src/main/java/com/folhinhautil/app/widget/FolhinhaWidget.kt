package com.folhinhautil.app.widget

import android.content.*
import android.graphics.Typeface
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
import com.folhinhautil.app.data.CalendarRepository
import com.folhinhautil.app.data.CalendarSnapshot
import com.folhinhautil.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter

internal val WidgetSizes = setOf(DpSize(250.dp, 250.dp), DpSize(250.dp, 330.dp), DpSize(360.dp, 440.dp),
    DpSize(400.dp, 440.dp), DpSize(400.dp, 450.dp), DpSize(250.dp, 540.dp), DpSize(500.dp, 250.dp), DpSize(500.dp, 360.dp))

class FolhinhaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes)
    override val stateDefinition = PreferencesGlanceStateDefinition
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        reconcileWidgetMonth(context, id)
        WidgetRefresh.schedule(context)
        val personalFlow = PersonalEventsRepository(context).events.catch {
            if (it is CancellationException) throw it
            emit(emptyList())
        }
        val initialPersonal = personalFlow.first()
        provideContent {
            val prefs = currentState<Preferences>()
            val personal by personalFlow.collectAsState(initialPersonal)
            val today by produceState(LocalDate.now()) {
                while (true) { delay(30_000); value = LocalDate.now() }
            }
            val month = resolvedWidgetMonth(prefs[ViewedMonth], prefs[CurrentMonthAnchor], today)
            val settings = prefs.widgetPreferences()
            val revision = prefs[ClockRevision]
            val snapshot by produceState(CalendarSnapshot(), month, settings.categories, revision) {
                value = if (SidebarCategory.CALENDAR in settings.categories) CalendarRepository(context).load(month) else CalendarSnapshot()
            }
            val message = if (!snapshot.permissionGranted) "Autorize o acesso à agenda no aplicativo" else snapshot.error
            WidgetContent(month, today, BrazilianHolidayProvider().forYear(month.year), personal,
                preferences = settings, appointments = snapshot.monthEvents, calendarMessage = message)
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
    val sidebarSections: List<SidebarSection>, val moonSize: Float, val dayFontSize: Float,
)

internal fun widgetTypeface(context: Context, bold: Boolean): Typeface {
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        val adjustment = context.resources.configuration.fontWeightAdjustment.takeIf { it in -1000..1000 } ?: 0
        return Typeface.create(Typeface.DEFAULT, ((if (bold) 700 else 400) + adjustment).coerceIn(1, 1000), false)
    }
    return Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
}

internal fun widgetDayLabel(date: LocalDate?, holidays: List<Holiday>): String {
    if (date == null) return ""
    val entries = holidays.filter { it.date == date }
    val marker = if (entries.isEmpty()) "" else if (entries.any { it.kind == HolidayKind.NATIONAL }) "•" else "·"
    return "${date.dayOfMonth}$marker"
}

@Composable
internal fun WidgetContent(month: YearMonth, today: LocalDate, holidays: List<Holiday>, personal: List<PersonalEvent> = emptyList(),
    holidayProvider: HolidayProvider = BrazilianHolidayProvider(), preferences: WidgetPreferences = WidgetPreferences(),
    appointments: List<Appointment> = emptyList(), calendarMessage: String? = null) {
    val space = widgetSpace(LocalContext.current, LocalSize.current, month, today, holidays, personal, holidayProvider,
        preferences, appointments, calendarMessage)
    Column(GlanceModifier.fillMaxSize().background(Color(0xFF101513)).padding(space.padding.dp).clickable(actionStartActivity<MainActivity>())) {
        if (space.sideBySide) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) { WidgetCalendar(month, today, holidays, space) }
                Spacer(GlanceModifier.width(8.dp))
                Box(GlanceModifier.width(1.dp).fillMaxHeight().background(Divider)) {}
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.width(space.holidayWidth.dp)) { WidgetSidebar(space) }
            }
        } else {
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) { WidgetCalendar(month, today, holidays, space) }
                if (space.sidebarSections.isNotEmpty()) { WidgetSeparator(); WidgetSidebar(space) }
            }
        }
        if (space.events.isNotEmpty()) {
            WidgetSeparator()
            WidgetPersonalEvents(space)
        }
    }
}

@Composable
private fun WidgetSidebar(space: WidgetSpace) {
    Column(GlanceModifier.fillMaxWidth()) {
        space.sidebarSections.forEachIndexed { index, section ->
            if (index > 0) WidgetSeparator()
            val color = if (section.category == SidebarCategory.OTHER_DATES) Amber else if (section.category == SidebarCategory.HOLIDAYS) Red else Aqua
            Column(GlanceModifier.fillMaxWidth().height(section.height.dp)) {
                if (section.headingHeight > 0) {
                    Text(section.category.label, GlanceModifier.fillMaxWidth().height(section.headingHeight.dp),
                        style = TextStyle(color = ColorProvider(color), fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(4.dp))
                }
                Column(GlanceModifier.fillMaxWidth()) {
                    section.rows.forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) Spacer(GlanceModifier.height(4.dp))
                        Column(GlanceModifier.fillMaxWidth()) {
                            if (section.category == SidebarCategory.HOLIDAYS && row.detail != null)
                                Text(row.detail, GlanceModifier.fillMaxWidth().height(row.detailHeight.dp), style = TextStyle(color = ColorProvider(color), fontSize = 10.sp))
                            Text(row.title, GlanceModifier.fillMaxWidth().height(row.titleHeight.dp).semantics {
                                contentDescription = row.holiday?.let { it.name + ", " + it.kind.label } ?: row.title
                            }, style = TextStyle(color = ColorProvider(if (section.category == SidebarCategory.OTHER_DATES) Amber else Neutral), fontSize = 11.sp))
                            if (section.category != SidebarCategory.HOLIDAYS && row.detail != null)
                                Text(row.detail, GlanceModifier.fillMaxWidth().height(row.detailHeight.dp), style = TextStyle(color = ColorProvider(Aqua), fontSize = 10.sp))
                        }
                    }
                }
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
    val moon = if (space.moonSize > 0) MoonPhases.forMonth(month, ZoneId.systemDefault()) else emptyMap()
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(widgetDayLabel(date, holidays), style = TextStyle(color = ColorProvider(if (date?.isWeekend() == true || national) Red
                            else if (entries.isNotEmpty()) Amber else Neutral), fontSize = space.dayFontSize.sp,
                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center), maxLines = 1)
                        if (space.moonSize > 0) {
                            val phase = moon[date]?.principal?.phase
                            val icon = when (phase) {
                                MoonPhase.NEW -> R.drawable.ic_moon_new
                                MoonPhase.FIRST_QUARTER -> R.drawable.ic_moon_first
                                MoonPhase.FULL -> R.drawable.ic_moon_full
                                MoonPhase.LAST_QUARTER -> R.drawable.ic_moon_last
                                else -> null
                            }
                            if (icon != null) Image(ImageProvider(icon), phase!!.label, GlanceModifier.size(space.moonSize.dp))
                            else Spacer(GlanceModifier.height(space.moonSize.dp))
                        }
                        }
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

class FolhinhaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = FolhinhaWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); WidgetRefresh.schedule(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); WidgetRefresh.cancel(context) }
}
class ClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, WidgetRefresh.Action)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) { refreshWidgetsForClock(context) } }
            catch (_: RuntimeException) { }
            finally {
                try {
                    if (GlanceAppWidgetManager(context).getGlanceIds(FolhinhaWidget::class.java).isNotEmpty()) WidgetRefresh.schedule(context)
                } finally { pending.finish() }
            }
        }
    }
}
