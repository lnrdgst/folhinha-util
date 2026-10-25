package com.folhinhautil.app.widget

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.folhinhautil.app.data.*
import com.folhinhautil.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.*
import kotlin.math.roundToInt

/** Synthetic fixtures use isolated stores, never CalendarContract or the user's personal store.
 * Failure output contains only test identifiers and exception locations. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetLayoutInstrumentation : Instrumentation() {
    private var checkDevice = false
    private var onlyLunar = false
    private var onlyWidget = false
    private var cases = 0
    private var lunarCases = 0
    private var description = "initialization"
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        checkDevice = arguments?.getString("checkDeviceWidget") == "true"
        onlyLunar = arguments?.getString("section") == "moon"
        onlyWidget = arguments?.getString("section") == "widget"
        start()
    }
    override fun onStart() {
        val total = if (onlyLunar) 1 else if (onlyWidget) 3 else if (checkDevice) 5 else 4
        var failures = 0
        fun test(index: Int, name: String, body: () -> Unit) {
            fun status(code: Int, detail: String = "") {
                sendStatus(code, Bundle().apply {
                    putString("class", this@WidgetLayoutInstrumentation.javaClass.name); putString("test", name)
                    putInt("numtests", total); putInt("current", index); putString("stream", detail)
                    if (code < 0) putString("stack", detail)
                })
            }
            status(1)
            try { body(); status(0) }
            catch (failure: Throwable) {
                failures++
                if (description.startsWith("synthetic lunar")) description += "; " + failure.message
                status(-2, "case=$cases; $description; failure=${failure.javaClass.simpleName}; line=${failure.stackTrace.firstOrNull { it.className == javaClass.name }?.lineNumber}")
            }
        }
        if (!onlyLunar) {
        test(1, "personalCrudAndDurableStorage") { runBlocking { checkPersistence() } }
        test(2, "independentWidgetMonthState") { runBlocking { checkNavigation() } }
        test(3, "remoteViewsLayoutMatrix") { runBlocking { renderMatrix() } }
        }
        if (!onlyWidget) test(if (onlyLunar) 1 else 4, "lunarCalendarAndSelectedDay") { lunarCases = com.folhinhautil.app.runMoonCalendarChecks(this) { description = it } }; if (checkDevice) test(5, "selectedCalendarsAndUnchangedPersonalData") { runBlocking { checkSamsungData() } }
        finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "\nRendered widget cases: $cases; lunar checks: $lunarCases; failures: $failures\n") })
    }

    private suspend fun checkPersistence() {
        description = "isolated DataStore CRUD/reopen"
        val file = File(targetContext.cacheDir, "personal-validation.preferences_pb")
        file.delete()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun open() = PersonalEventsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        var repo = open()
        try {
            check(repo.events.first().isEmpty())
            val original = PersonalEvent("fixture", "Validação local", LocalDate.of(2026, 12, 27), true)
            repo.save(original)
            check(repo.events.first() == listOf(original))
            val edited = original.copy(title = "Validação editada", annual = false)
            repo.save(edited)
            check(repo.events.first() == listOf(edited))
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = open()
            check(repo.events.first() == listOf(edited))
            repo.save(original.copy(id = "second"))
            repo.delete(original.id)
            check(repo.events.first().single().id == "second")
            repo.delete("second")
            check(repo.events.first().isEmpty())
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = open()
            check(repo.events.first().isEmpty())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    private suspend fun checkNavigation() {
        description = "isolated per-widget preferences"
        // Glance 1.1.1 has no public fake-ID factory. Reflection is test-only.
        // Cache-backed context keeps all test state separate from launcher widget preferences.
        val isolated = object : android.content.ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(targetContext.cacheDir, "widget-state-validation").apply { mkdirs() }
        }
        val constructor = Class.forName("androidx.glance.appwidget.AppWidgetId").getConstructor(Int::class.javaPrimitiveType)
        val one = constructor.newInstance(1_000_000_001) as androidx.glance.GlanceId
        val two = constructor.newInstance(1_000_000_002) as androidx.glance.GlanceId
        val today = LocalDate.of(2026, 12, 24)
        changeWidgetMonth(isolated, one, 0, today)
        changeWidgetMonth(isolated, two, 0, today)
        changeWidgetMonth(isolated, one, 1, today)
        fun state(id: androidx.glance.GlanceId) = runBlocking {
            getAppWidgetState(isolated, PreferencesGlanceStateDefinition, id)[ViewedMonth]
        }
        check(state(one) == "2027-01")
        check(state(two) == "2026-12")
        // A new identifier object reads the same persisted state.
        val reopened = constructor.newInstance(1_000_000_001) as androidx.glance.GlanceId
        check(getAppWidgetState(isolated, PreferencesGlanceStateDefinition, reopened)[ViewedMonth] == "2027-01")
        changeWidgetMonth(isolated, one, -1, today); check(state(one) == "2026-12")
        changeWidgetMonth(isolated, one, 0, LocalDate.of(2026, 10, 24)); check(state(one) == "2026-10")
        check(state(two) == "2026-12")
    }

    private suspend fun renderMatrix() {
        val samsung = DpSize(401.33.dp, 453.87.dp)
        val bound = DpSize(400.dp, 450.dp)
        check(WidgetSizes.filter { it.width <= samsung.width && it.height <= samsung.height }
            .maxBy { it.width.value * it.height.value } == bound)
        val today = LocalDate.of(2026, 10, 24)
        val fixtures = listOf(PersonalEvent("future", "Aniversário de validação", LocalDate.of(2026, 12, 27)),
            PersonalEvent("annual", "Evento pessoal de validação", today.minusYears(1), true))
        for (scale in listOf(1.3f, 1f, 1.15f, 1.5f)) {
            val ctx = targetContext.createConfigurationContext(Configuration(targetContext.resources.configuration).apply { fontScale = scale })
            for (month in listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 4), YearMonth.of(2026, 8), YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12))) {
                val holidays = BrazilianHolidayProvider().forYear(month.year)
                for (count in 0..2) for (size in WidgetSizes + samsung) {
                    val personal = fixtures.take(count)
                    cases++
                    description = "font=$scale; month=$month; p=$count; size=${size.width.value}x${size.height.value}"
                    val space = widgetSpace(ctx, size, month, today, holidays, personal)
                    val root = render(ctx, size, month, holidays, today, personal)
                    assertLayout(ctx, root, month, today, holidays, personal, space)
                    if (size == samsung && scale <= 1.15f) {
                        check(space.sideBySide)
                        check(space.selectedHolidays.size == widgetNationalHolidays(month, today).size)
                        check(space.holidaySummary == null)
                        if (month.monthValue == 10) check(space.events.size == count)
                        if (count > 0) check(space.events.isNotEmpty())
                    }
                    if (size == bound) {
                        cases++
                        val hosted = render(ctx, size, month, holidays, today, personal, samsung)
                        assertLayout(ctx, hosted, month, today, holidays, personal, space)
                        if (month.monthValue == 10 && scale == 1.15f && count == 2)
                            saveFixturePreview(hosted, "widget-dynamic-samsung.png")
                        if (month.monthValue == 11 && scale == 1.15f && count == 2)
                            saveFixturePreview(hosted, "widget-all-holidays-november.png")
                        if (month.monthValue == 8 && scale == 1.5f && count == 2)
                            saveFixturePreview(hosted, "widget-dynamic-six-weeks.png")
                    }
                    if (size == DpSize(250.dp, 250.dp) && scale == 1.5f && month.monthValue == 10 && count == 0)
                        saveFixturePreview(root, "widget-dynamic-compact.png")
                    if (size == DpSize(500.dp, 360.dp) && scale == 1f && month.monthValue == 12 && count == 2)
                        saveFixturePreview(root, "widget-dynamic-horizontal.png")
                }
            }
            // A long official name must wrap completely, including with Samsung's accessibility bold.
            for (size in listOf(bound, DpSize(250.dp, 330.dp), DpSize(500.dp, 250.dp))) {
                val month = YearMonth.of(2026, 11)
                val date = LocalDate.of(2026, 11, 16)
                val holidays = BrazilianHolidayProvider().forYear(month.year)
                cases++
                description = "long holiday; font=$scale; size=${size.width.value}x${size.height.value}"
                val space = widgetSpace(ctx, size, month, date, holidays, fixtures)
                assertLayout(ctx, render(ctx, size, month, holidays, date, fixtures), month, date, holidays, fixtures, space)
            }

            // Six-week calendar plus complete selections of varying cardinality. No fixture
            // is inserted into the legal catalog or the user's persisted data.
            for (amount in listOf(0, 1, 2, 3, 5, 12)) {
                val month = YearMonth.of(2026, 8)
                val provider = object : HolidayProvider {
                    override fun forYear(year: Int) = BrazilianHolidayProvider().forYear(year) +
                        (1..amount).map { Holiday(LocalDate.of(year, 8, it), "Feriado de validação $it", HolidayKind.NATIONAL, "fixture") }
                }
                val holidays = provider.forYear(month.year)
                for (size in listOf(bound, DpSize(250.dp, 250.dp), DpSize(800.dp, 1200.dp))) {
                    cases++
                    description = "synthetic; font=$scale; count=$amount; size=${size.width.value}x${size.height.value}"
                    val space = widgetSpace(ctx, size, month, today, holidays, fixtures, provider)
                    val root = render(ctx, size, month, holidays, today, fixtures, provider = provider)
                    assertLayout(ctx, root, month, today, holidays, fixtures, space, provider)
                    check(space.selectedHolidays.size == 3)
                    if (size.height == 1200.dp) check(space.holidaySummary == null)
                    if (size == bound && scale == 1.15f && amount == 3)
                        saveFixturePreview(root, "widget-all-holidays-six-weeks.png")
                }
            }
        }
        val month = YearMonth.of(2099, 12)
        val todayAtLimit = month.atDay(26)
        val holidays = BrazilianHolidayProvider().forYear(month.year)
        cases++
        description = "catalog upper limit"
        val space = widgetSpace(targetContext, samsung, month, todayAtLimit, holidays)
        check(space.selectedHolidays.map { it.date } == listOf(month.atDay(25)) && space.events.isEmpty())
        assertLayout(targetContext, render(targetContext, samsung, month, holidays, todayAtLimit), month, todayAtLimit, holidays, emptyList(), space)
        val reader = java.io.BufferedReader(java.io.InputStreamReader(java.io.FileInputStream(
            uiAutomation.executeShellCommand("logcat -d -t 20000 --pid=${android.os.Process.myPid()} -v brief").fileDescriptor)))
        reader.use { check(it.lineSequence().none { line -> line.contains("Truncated Column container") || line.contains("Truncated Row container") }) }
    }

    private suspend fun checkSamsungData() {
        description = "read-only calendar integration and personal data"
        val snapshot = CalendarRepository(targetContext).load()
        check(snapshot.permissionGranted && snapshot.error == null)
        val personal = PersonalEventsRepository(targetContext).events.first()
        sendStatus(2, Bundle().apply { putString("stream", "device_future_calendar_present=" + snapshot.upcoming.isNotEmpty()) })
        val month = YearMonth.now()
        val today = LocalDate.now()
        val holidays = BrazilianHolidayProvider().forYear(month.year)
        for (size in listOf(DpSize(400.dp, 440.dp), DpSize(401.33.dp, 453.87.dp))) {
            cases++
            val space = widgetSpace(targetContext, size, month, today, holidays, personal)
            assertLayout(targetContext, render(targetContext, size, month, holidays, today, personal), month, today, holidays, personal, space)
        }
        check(PersonalEventsRepository(targetContext).events.first() == personal)
    }

    private suspend fun render(ctx: Context, size: DpSize, month: YearMonth, holidays: List<Holiday>,
        today: LocalDate, personal: List<PersonalEvent> = emptyList(), viewport: DpSize = size,
        provider: HolidayProvider = BrazilianHolidayProvider()): View {
        val result = GlanceRemoteViews().compose(ctx, size) { WidgetContent(month, today, holidays, personal, provider) }
        var outcome: Result<View>? = null
        runOnMainSync {
            outcome = runCatching {
                val root = LayoutHost(ctx).apply { show(result.remoteViews) }
                val density = ctx.resources.displayMetrics.density
                root.measure(View.MeasureSpec.makeMeasureSpec((viewport.width.value * density).roundToInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((viewport.height.value * density).roundToInt(), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                root
            }
        }
        return outcome!!.getOrThrow()
    }
    private class LayoutHost(ctx: Context) : AppWidgetHostView(ctx) {
        fun show(views: RemoteViews) { val child = views.apply(context, this); prepareView(child); addView(child) }
    }
    private fun texts(view: View): List<TextView> = buildList {
        if (view is TextView && view.visibility == View.VISIBLE && view.width > 0 && view.height > 0 && view.text.isNotEmpty()) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
    }
    private fun bounds(root: View, child: View) = Rect(0, 0, child.width, child.height).also { (root as ViewGroup).offsetDescendantRectToMyCoords(child, it) }
    private fun assertLayout(ctx: Context, root: View, month: YearMonth, today: LocalDate, holidays: List<Holiday>,
        personal: List<PersonalEvent>, space: WidgetSpace, provider: HolidayProvider = BrazilianHolidayProvider()) {
        val all = texts(root)
        check(all.any { it.text.toString() == space.monthLabel })
        check(WeekLabels.all { label -> all.any { it.text.toString() == label } })
        for (day in 1..month.lengthOfMonth()) {
            val entries = holidays.filter { it.date == month.atDay(day) }
            val marker = if (entries.isEmpty()) "" else if (entries.any { it.kind == HolidayKind.NATIONAL }) "•" else "·"
            check(all.any { it.text.toString() == "$day$marker" })
        }
        val expectedLegend = monthLegend(month, holidays)
        val redLegend = all.singleOrNull { it.text.toString() == "• Feriado" }
        val amberLegend = all.singleOrNull { it.text.toString() == space.legendLabel }
        check((redLegend != null) == (expectedLegend.national && space.holidaySummary == null))
        check((amberLegend != null) == expectedLegend.other)
        if (redLegend != null) check(redLegend.currentTextColor == 0xFFFF9894.toInt())
        if (amberLegend != null) check(amberLegend.currentTextColor == 0xFFECC88B.toInt())
        if (month == YearMonth.of(2026, 10)) check(all.single { it.text.toString() == "28·" }.currentTextColor == 0xFFECC88B.toInt())
        check(all.none { it.text.toString() == "Feriados" })
        check(all.any { it.text.toString() == "Próximos eventos pessoais" } == space.events.isNotEmpty())
        check(all.none { it.text.toString() == "Pontos facultativos e outras datas" })
        check(space.otherDates.map { it.holiday } == widgetOtherDates(month).take(space.otherDates.size))
        check(space.otherDates.count { it.holiday.kind == HolidayKind.OPTIONAL } <= 2)
        check(space.otherDates.count { it.holiday.kind != HolidayKind.OPTIONAL } <= 2)
        space.otherDates.forEach { item ->
            check(item.holiday.kind != HolidayKind.NATIONAL)
            check(all.single { it.text.toString() == item.text }.currentTextColor == 0xFFECC88B.toInt())
        }
        if (space.otherDates.isNotEmpty()) {
            val lastOtherDate = all.single { it.text.toString() == space.otherDates.last().text }
            if (space.events.isNotEmpty()) check(bounds(root, lastOtherDate).bottom <
                bounds(root, all.single { it.text.toString() == "Próximos eventos pessoais" }).top)
        }
        check(space.events.size <= 2)
        val expectedHolidays = widgetNationalHolidays(month, today, provider)
        check(space.selectedHolidays == expectedHolidays)
        if (space.holidaySummary == null) check(space.nationalHolidays.map { it.holiday } == expectedHolidays)
        else {
            check(space.nationalHolidays.isEmpty())
            val summary = all.single { it.text.toString() == space.holidaySummary }
            check(summary.contentDescription.toString().contains("Amplie"))
            check(expectedHolidays.all { summary.contentDescription.toString().contains(it.name) &&
                summary.contentDescription.toString().contains(formatPersonalDate(it.date)) })
        }
        space.nationalHolidays.forEach { item ->
            check(item.holiday.kind == HolidayKind.NATIONAL)
            check(all.any { it.text.toString() == item.holiday.name })
            check(all.any { it.text.toString() == item.dateLabel })
        }
        check(space.events.map { it.occurrence } == upcomingPersonalEvents(personal, today).take(space.events.size))
        for (event in personal)
            check(all.any { it.text.toString() == event.title } == space.events.any { it.occurrence.event == event })
        space.events.forEach { item ->
            check(all.any { it.text.toString() == item.occurrence.countdown })
            check(all.any { it.text.toString() == formatPersonalDate(item.occurrence.date) })
        }
        val rects = all.map { bounds(root, it) }
        check(rects.all { it.left >= 0 && it.top >= 0 && it.right <= root.width + 1 && it.bottom <= root.height + 1 })
        for (a in rects.indices) for (b in a + 1 until rects.size) check(!Rect.intersects(rects[a], rects[b]))
        val personalTitles = space.events.map { it.occurrence.event.title }
        for (text in all) {
            val fits = text.layout == null || text.layout.height + text.compoundPaddingTop + text.compoundPaddingBottom <= text.height + 2
            if (!fits) {
                val role = when (text.text.toString()) {
                    space.monthLabel -> "month-heading"
                    "Feriados" -> "holiday-heading"
                    "Próximos eventos pessoais" -> "personal-heading"
                    space.legendLabel, "• Feriado" -> "legend"
                    in space.nationalHolidays.map { it.holiday.name } -> "holiday-name"
                    else -> "other"
                }
                description += "; role=$role; view=${text.width}x${text.height}; layout=${text.layout.height}; lines=${text.layout.lineCount}; textPx=${text.textSize}; sp17px=${android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,17f,ctx.resources.displayMetrics)}; pad=${text.compoundPaddingLeft},${text.compoundPaddingRight}; rawWidth=${text.paint.measureText(text.text.toString())}; modelHeading=${space.monthTitleHeight}"
                if (description.startsWith("font=") || description.startsWith("long holiday") || description.startsWith("synthetic"))
                    saveFixturePreview(root, "widget-dynamic-failure.png")
            }
            check(fits)
            if (text.text.toString() !in personalTitles)
                check((0 until text.layout.lineCount).all { text.layout.getEllipsisCount(it) == 0 })
            var parent = text.parent as? ViewGroup
            val childBounds = bounds(root, text)
            while (parent != null && parent != root) {
                if (parent.clipChildren) {
                    val parentBounds = bounds(root, parent)
                    check(childBounds.left >= parentBounds.left - 2 && childBounds.top >= parentBounds.top - 2 &&
                        childBounds.right <= parentBounds.right + 2 && childBounds.bottom <= parentBounds.bottom + 2)
                }
                parent = parent.parent as? ViewGroup
            }
        }
        val lines = dividerViews(root)
        val vertical = lines.filter { it.height > it.width }
        check(vertical.size == if (space.sideBySide) 1 else 0)
        val horizontalCount = (if (!space.sideBySide && space.nationalHolidays.isNotEmpty()) 1 else 0) +
            (if (space.events.isNotEmpty()) space.events.size else 0) + (if (space.otherDates.isNotEmpty()) 1 else 0)
        check(lines.size - vertical.size == horizontalCount)
        if (space.sideBySide) {
            val line = bounds(root, vertical.single())
            check(bounds(root, all.first { it.text.toString() == space.nationalHolidays.first().dateLabel }).left > line.right)
            check(bounds(root, all.first { it.text.toString() == WeekLabels.first() }).right < line.left)
            if (space.otherDatesInSidebar) {
                val yellow = bounds(root, all.first { it.text.toString() == space.otherDates.first().text })
                check(yellow.left > line.right)
                check(bounds(root, all.first { it.text.toString() == space.nationalHolidays.last().holiday.name }).bottom < yellow.top)
            }
        }
        val padding = (space.padding * ctx.resources.displayMetrics.density).roundToInt()
        if (space.footerHeight > 0f || space.legendHeight > 0f)
            check(kotlin.math.abs(root.height - padding - rects.maxOf { it.bottom }) <= 3)
    }
    private fun dividerViews(view: View): List<View> = buildList {
        if ((view.background as? ColorDrawable)?.color == 0xFF465C50.toInt()) add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(dividerViews(view.getChildAt(index)))
    }

    private fun saveFixturePreview(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        runOnMainSync { view.draw(Canvas(bitmap)) }
        File(targetContext.cacheDir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
