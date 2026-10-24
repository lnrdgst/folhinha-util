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
    private var cases = 0
    private var description = "initialization"
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        checkDevice = arguments?.getString("checkDeviceWidget") == "true"
        start()
    }
    override fun onStart() {
        val total = if (checkDevice) 4 else 3
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
                status(-2, "case=$cases; $description; failure=${failure.javaClass.simpleName}; line=${failure.stackTrace.firstOrNull { it.className == javaClass.name }?.lineNumber}")
            }
        }
        test(1, "personalCrudAndDurableStorage") { runBlocking { checkPersistence() } }
        test(2, "independentWidgetMonthState") { runBlocking { checkNavigation() } }
        test(3, "remoteViewsLayoutMatrix") { runBlocking { renderMatrix() } }
        if (checkDevice) test(4, "selectedCalendarsAtSamsungSize") { runBlocking { checkSamsungData() } }
        finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "\nRendered cases: $cases; failures: $failures\n") })
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
        // New manager/widget objects read the same persisted state.
        val reopened = constructor.newInstance(1_000_000_001) as androidx.glance.GlanceId
        check(getAppWidgetState(isolated, PreferencesGlanceStateDefinition, reopened)[ViewedMonth] == "2027-01")
        changeWidgetMonth(isolated, one, -1, today); check(state(one) == "2026-12")
        changeWidgetMonth(isolated, one, 0, LocalDate.of(2026, 10, 24)); check(state(one) == "2026-10")
        check(state(two) == "2026-12")
    }

    private suspend fun renderMatrix() {
        val samsung = DpSize(401.33.dp, 453.87.dp)
        val bound = DpSize(360.dp, 440.dp)
        check(WidgetSizes.filter { it.width <= samsung.width && it.height <= samsung.height }
            .maxBy { it.width.value * it.height.value } == bound)
        for (scale in listOf(1f, 1.15f, 1.3f, 1.5f)) {
            val ctx = targetContext.createConfigurationContext(Configuration(targetContext.resources.configuration).apply { fontScale = scale })
            for (month in listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 10), YearMonth.of(2026, 8))) {
                val holidays = BrazilianHolidayProvider().forYear(month.year)
                val today = month.atDay(10)
                for (personalCount in 0..2) for (calendarCount in 0..2) for (size in WidgetSizes + samsung) {
                    val snapshot = fixture(month, calendarCount)
                    val personal = listOf(PersonalEvent("today", "Evento pessoal de validação", today),
                        PersonalEvent("annual", "Ocorrência anual de validação", today.plusDays(3).minusYears(1), true)).take(personalCount)
                    cases++
                    description = "font=$scale; weeks=${monthGrid(month).size}; p=$personalCount; c=$calendarCount; size=${size.width.value}x${size.height.value}"
                    val space = widgetSpace(ctx, size, month, snapshot, today, holidays, personal)
                    val root = render(ctx, size, month, holidays, snapshot, today, personal)
                    assertLayout(ctx, root, month, holidays, snapshot, personal, space)
                    if (size == samsung && scale == 1f && month.monthValue == 10 && personalCount > 0 && calendarCount > 0) {
                        check(space.nationalHolidays.size == 2)
                        check(space.events.size == 2)
                        check(space.events.any { it.item is WidgetNextEvent.Personal })
                        check(space.events.any { it.item is WidgetNextEvent.Calendar })
                    }
                    if (size == bound) {
                        cases++
                        val hosted = render(ctx, size, month, holidays, snapshot, today, personal, samsung)
                        assertLayout(ctx, hosted, month, holidays, snapshot, personal, space)
                        if (month.monthValue == 10 && scale == 1f && personalCount == 1 && calendarCount == 1)
                            saveFixturePreview(hosted, "widget-evolution-samsung.png")
                        if (month.monthValue == 8 && scale == 1.5f && personalCount == 2 && calendarCount == 2)
                            saveFixturePreview(hosted, "widget-evolution-six-weeks.png")
                    }
                    if (size == DpSize(250.dp, 250.dp) && scale == 1.5f && month.monthValue == 10 && personalCount == 0 && calendarCount == 0)
                        saveFixturePreview(root, "widget-evolution-compact.png")
                }
            }
        }
        val month = YearMonth.of(2099, 12)
        val today = month.atDay(26)
        val snapshot = fixture(month, 0)
        val holidays = BrazilianHolidayProvider().forYear(month.year)
        cases++
        val space = widgetSpace(targetContext, samsung, month, snapshot, today, holidays)
        check(!space.showAgenda)
        assertLayout(targetContext, render(targetContext, samsung, month, holidays, snapshot, today), month, holidays, snapshot, emptyList(), space)
        val reader = java.io.BufferedReader(java.io.InputStreamReader(java.io.FileInputStream(
            uiAutomation.executeShellCommand("logcat -d -t 20000 --pid=${android.os.Process.myPid()} -v brief").fileDescriptor)))
        reader.use { check(it.lineSequence().none { line -> line.contains("Truncated Column container") || line.contains("Truncated Row container") }) }
    }

    private fun fixture(month: YearMonth, count: Int): CalendarSnapshot {
        val timed = month.atDay(10).atTime(16, 0).atZone(ZoneId.systemDefault()).toInstant()
        val allDay = month.atDay(11).atStartOfDay(ZoneOffset.UTC).toInstant()
        return CalendarSnapshot(true, listOf(CalendarInfo(1, "Validação", "", 0)), setOf(1), upcoming = listOf(
            Appointment(1, 1, "Compromisso de validação", timed, timed.plusSeconds(3600), false),
            Appointment(2, 1, "Dia inteiro de validação", allDay, allDay.plusSeconds(86400), true)).take(count))
    }

    private suspend fun checkSamsungData() {
        description = "read-only real calendar coexistence"
        val snapshot = CalendarRepository(targetContext).load()
        check(snapshot.permissionGranted && snapshot.error == null)
        val zone = ZoneId.systemDefault()
        val national = futureNationalHolidays(LocalDate.now())
        val personal = listOf(PersonalEvent("device-fixture", "Evento pessoal de validação", LocalDate.now()))
        val target = nextWidgetEvents(emptyList(), snapshot.upcoming, LocalDate.now(), zone, national)
            .filterIsInstance<WidgetNextEvent.Calendar>().firstOrNull()?.appointment
        sendStatus(2, Bundle().apply { putString("stream", "device_future_calendar_present=" + (target != null)) })
        val month = YearMonth.now()
        val holidays = BrazilianHolidayProvider().forYear(month.year)
        for (size in listOf(DpSize(360.dp, 440.dp), DpSize(401.33.dp, 453.87.dp))) {
            cases++
            val space = widgetSpace(targetContext, size, month, snapshot, LocalDate.now(), holidays, personal)
            if (target != null) check(space.events.any { (it.item as? WidgetNextEvent.Calendar)?.appointment == target })
            val root = render(targetContext, size, month, holidays, snapshot, LocalDate.now(), personal)
            assertLayout(targetContext, root, month, holidays, snapshot, personal, space)
        }
    }

    private suspend fun render(ctx: Context, size: DpSize, month: YearMonth, holidays: List<Holiday>, snapshot: CalendarSnapshot,
        today: LocalDate, personal: List<PersonalEvent> = emptyList(), viewport: DpSize = size): View {
        val result = GlanceRemoteViews().compose(ctx, size) { WidgetContent(month, today, holidays, snapshot, personal) }
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
    private fun assertLayout(ctx: Context, root: View, month: YearMonth, holidays: List<Holiday>, snapshot: CalendarSnapshot,
        personal: List<PersonalEvent>, space: WidgetSpace) {
        val all = texts(root)
        check(all.any { it.text.toString() == space.monthLabel })
        check(WeekLabels.all { label -> all.any { it.text.toString() == label } })
        for (day in 1..month.lengthOfMonth()) {
            val entries = holidays.filter { it.date == month.atDay(day) }
            val marker = if (entries.isEmpty()) "" else if (entries.any { it.kind == HolidayKind.NATIONAL }) "•" else "·"
            check(all.any { it.text.toString() == "$day$marker" })
        }
        val redLegend = all.single { it.text.toString() == "• Feriado" }
        val amberLegend = all.single { it.text.toString() == space.legendLabel }
        check(redLegend.currentTextColor == 0xFFFF9894.toInt() && amberLegend.currentTextColor == 0xFFECC88B.toInt())
                val legendFits = listOf(redLegend, amberLegend).all { it.layout.lineCount == 1 && it.layout.getEllipsisCount(0) == 0 }
        if (!legendFits) {
            description += "; legend=" + listOf(redLegend, amberLegend).joinToString { "${it.width}x${it.height}/${it.layout.getEllipsisCount(0)}" }
            if (description.startsWith("font=")) saveFixturePreview(root, "widget-evolution-failure.png")
        }
        check(legendFits)
        if (month == YearMonth.of(2026, 10)) check(all.single { it.text.toString() == "28·" }.currentTextColor == 0xFFECC88B.toInt())
        check(all.any { it.text.toString() == "Próximos feriados" } == space.nationalHolidays.isNotEmpty())
        check(all.any { it.text.toString() == "Próximos eventos" } == space.events.isNotEmpty())
        check(space.nationalHolidays.size <= 2 && space.events.size <= 2)
        space.nationalHolidays.forEach { check(it.holiday.kind == HolidayKind.NATIONAL); check(all.any { text -> text.text.toString() == it.text }) }
        for (title in snapshot.upcoming.map { it.title } + personal.map { it.title })
            check(all.any { it.text.toString() == title } == space.events.any { it.item.title == title })
        space.events.forEach { item ->
            when (val value = item.item) {
                is WidgetNextEvent.Personal -> {
                    check(all.any { it.text.toString() == value.occurrence.countdown })
                    check(all.any { it.text.toString() == formatPersonalDate(value.occurrence.date) })
                }
                is WidgetNextEvent.Calendar -> check(all.any { it.text.toString() == value.appointment.description(ZoneId.systemDefault()) })
            }
        }
        val rects = all.map { bounds(root, it) }
        check(rects.all { it.left >= 0 && it.top >= 0 && it.right <= root.width + 1 && it.bottom <= root.height + 1 })
        for (a in rects.indices) for (b in a + 1 until rects.size) check(!Rect.intersects(rects[a], rects[b]))
        check(all.all { it.layout == null || it.layout.height + it.compoundPaddingTop + it.compoundPaddingBottom <= it.height + 2 })
        val padding = (space.padding * ctx.resources.displayMetrics.density).roundToInt()
        if (!space.horizontal && space.showAgenda) {
            val firstHeading = all.first { it.text.toString() == if (space.nationalHolidays.isNotEmpty()) "Próximos feriados" else "Próximos eventos" }
            check(bounds(root, firstHeading).top > bounds(root, redLegend).bottom)
        }
        // Content reaches the available lower region; the host may exceed LocalSize.
        if (!space.horizontal) check(kotlin.math.abs(root.height - padding - rects.maxOf { it.bottom }) <= 3)
    }
    private fun saveFixturePreview(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        runOnMainSync { view.draw(Canvas(bitmap)) }
        File(targetContext.cacheDir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
