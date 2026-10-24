package com.folhinhautil.app.widget

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.folhinhautil.app.data.CalendarRepository
import com.folhinhautil.app.data.CalendarSnapshot
import com.folhinhautil.app.domain.*
import kotlinx.coroutines.runBlocking
import java.time.*
import kotlin.math.roundToInt

/** Framework instrumentation: renders real Glance RemoteViews without new test dependencies.
 * Fixtures never enter CalendarContract. Failures report only case numbers and exception classes. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetLayoutInstrumentation : Instrumentation() {
    private var checkDevice = false
    private var cases = 0
    private var caseDescription = "initialization"

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        checkDevice = arguments?.getString("checkDeviceWidget") == "true"
        start()
    }

    override fun onStart() {
        val total = if (checkDevice) 2 else 1
        var failures = 0
        fun test(index: Int, name: String, body: () -> Unit) {
            fun status(code: Int, extra: String = "") {
                sendStatus(code, Bundle().apply {
                    putString("class", this@WidgetLayoutInstrumentation.javaClass.name); putString("test", name)
                    putInt("numtests", total); putInt("current", index)
                    putString("stream", extra)
                    if (code < 0) putString("stack", extra)
                })
            }
            status(1)
            try { body(); status(0) }
            catch (failure: Throwable) {
                failures++
                status(-2, "case=$cases; $caseDescription; failure=${failure.javaClass.simpleName}; line=${failure.stackTrace.firstOrNull { it.className == javaClass.name }?.lineNumber}")
            }
        }
        test(1, "remoteViewsLayoutMatrix") { renderMatrix() }
        if (checkDevice) test(2, "selectedCalendarsAtSamsungSize") { runBlocking { checkSamsungData() } }
        finish(Activity.RESULT_OK, Bundle().apply {
            putString("stream", "\nRendered cases: $cases; failures: $failures\n")
        })
    }

    private fun renderMatrix() = runBlocking {
        val samsung = DpSize(401.33.dp, 453.87.dp)
        val bound = WidgetSizes.filter { it.width <= samsung.width && it.height <= samsung.height }
            .maxBy { it.width.value * it.height.value }
        check(bound == DpSize(360.dp, 440.dp))
        val sizes = WidgetSizes + samsung
        for (scale in listOf(1f, 1.15f, 1.3f, 1.5f)) {
            val context = targetContext.createConfigurationContext(Configuration(targetContext.resources.configuration).apply { fontScale = scale })
            for (month in listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 10), YearMonth.of(2026, 8))) {
                val holidays = BrazilianHolidayProvider().forYear(month.year)
                for (count in 0..3) for (size in sizes) {
                    cases++
                    caseDescription = "font=$scale; weeks=${monthGrid(month).size}; fixtures=$count; size=${size.width.value}x${size.height.value}"
                    val snapshot = fixture(month, count)
                    val space = widgetSpace(context, size, month, snapshot, month.atDay(10), holidays)
                    caseDescription += "; calendar=${space.monthHeight}; day=${space.dayHeight}"
                    val view = render(context, size, month, holidays, snapshot)
                    val texts = textViews(view)
                    check(texts.any { it.text.toString() == month.label() })
                    check(WeekLabels.all { label -> texts.any { it.text.toString() == label } })
                    for (day in 1..month.lengthOfMonth()) {
                        val entries = holidays.filter { it.date == month.atDay(day) }
                        val marker = if (entries.isEmpty()) "" else if (entries.any { it.kind == HolidayKind.NATIONAL }) "•" else "·"
                        check(texts.any { it.text.toString() == "$day$marker" })
                    }
                    val holidayLegend = texts.single { it.text.toString() == "• Feriado" }
                    val otherLegend = texts.single { it.text.toString() == "• Outras datas" }
                    check(holidayLegend.currentTextColor == 0xFFFF9894.toInt())
                    check(otherLegend.currentTextColor == 0xFFECC88B.toInt())
                    check(listOf(holidayLegend, otherLegend).all { text ->
                        text.layout.lineCount == 1 && text.layout.getEllipsisCount(0) == 0
                    })
                    val holidayBounds = bounds(view, holidayLegend)
                    val otherBounds = bounds(view, otherLegend)
                    check(holidayBounds.top == otherBounds.top && holidayBounds.bottom == otherBounds.bottom)
                    check(texts.any { it.text.toString() == "Próximos compromissos" } == space.showAgenda)
                    for (event in snapshot.upcoming) {
                        val visible = space.events.any { it.event == event }
                        check(texts.any { it.text.toString() == event.title } == visible)
                        if (visible) check(texts.any { it.text.toString() == event.description(ZoneId.systemDefault()) })
                    }
                    if (space.message != null) check(texts.any { it.text.toString() == space.message })
                    assertBounds(view, texts)
                    assertDistributedLayout(context, view, texts, space, snapshot)
                    if (size == samsung && scale == 1f) check(space.events.size == count)
                    if (size == DpSize(250.dp, 250.dp) && scale == 1.15f && month == YearMonth.of(2026, 10) && count == 3) {
                        saveFixturePreview(view, "widget-distribution-compact.png")
                    }
                    if (size == bound) {
                        // A responsive composition sees its declared bound; the launcher can be taller.
                        cases++
                        caseDescription += "; launcherViewport=401.33x453.87"
                        val hosted = render(context, size, month, holidays, snapshot, viewport = samsung)
                        val hostedTexts = textViews(hosted)
                        assertBounds(hosted, hostedTexts)
                        assertDistributedLayout(context, hosted, hostedTexts, space, snapshot)
                        // Only synthetic fixtures are exported, never personal calendar content.
                        if (scale == 1.15f && month == YearMonth.of(2026, 10) && count == 3) {
                            saveFixturePreview(hosted, "widget-distribution-samsung.png")
                        }
                        if (scale == 1.5f && month == YearMonth.of(2026, 8) && count == 3) {
                            saveFixturePreview(hosted, "widget-distribution-six-weeks-large-font.png")
                        }
                    }
                }
            }
        }
        // Real Glance translation must not silently drop any children in any responsive variant.
        val reader = java.io.BufferedReader(java.io.InputStreamReader(java.io.FileInputStream(
            uiAutomation.executeShellCommand("logcat -d -t 20000 --pid=${android.os.Process.myPid()} -v brief").fileDescriptor)))
        reader.use { check(it.lineSequence().none { line -> line.contains("Truncated Column container") || line.contains("Truncated Row container") }) }
    }

    private fun fixture(month: YearMonth, count: Int): CalendarSnapshot {
        val zone = ZoneId.systemDefault()
        val timed = month.atDay(10).atTime(16, 0).atZone(zone).toInstant()
        val allDay = month.atDay(11).atStartOfDay(ZoneOffset.UTC).toInstant()
        val events = listOf(
            Appointment(1, 1, "Compromisso de validação", timed, timed.plusSeconds(3600), false),
            Appointment(2, 1, "Dia inteiro de validação", allDay, allDay.plusSeconds(86400), true),
            // Another occurrence of the same event ID, already expanded by the repository in production.
            Appointment(1, 1, "Outra ocorrência de validação", timed.plusSeconds(7 * 86400), timed.plusSeconds(7 * 86400 + 3600), false),
        )
        return CalendarSnapshot(true, listOf(CalendarInfo(1, "Validação", "", 0)), setOf(1), upcoming = events.take(count))
    }

    private suspend fun checkSamsungData() {
        cases++
        caseDescription = "device, existing calendar selection"
        val snapshot = CalendarRepository(targetContext).load()
        check(snapshot.permissionGranted && snapshot.error == null)
        val zone = ZoneId.systemDefault()
        // Only a boolean result leaves this test. No personal event text enters diagnostics.
        val target = snapshot.upcoming.firstOrNull {
            it.title == "Teste" && !it.allDay && it.begin.atZone(zone).toLocalDate() == LocalDate.of(2026, 10, 26) &&
                it.begin.atZone(zone).toLocalTime() == LocalTime.of(16, 0) && it.end.atZone(zone).toLocalTime() == LocalTime.of(17, 0)
        }
        check(target != null)
        for (size in listOf(DpSize(360.dp, 440.dp), DpSize(401.33.dp, 453.87.dp))) {
            val month = YearMonth.now()
            val space = widgetSpace(targetContext, size, month, snapshot, LocalDate.now(), BrazilianHolidayProvider().forYear(month.year))
            check(space.events.any { it.event == target })
            val view = render(targetContext, size, month, BrazilianHolidayProvider().forYear(month.year), snapshot, LocalDate.now())
            val texts = textViews(view)
            check(texts.any { it.text.toString() == target.title })
            check(texts.any { it.text.toString() == target.description(zone) })
            assertBounds(view, texts)
        }
    }

    private suspend fun render(context: Context, size: DpSize, month: YearMonth, holidays: List<Holiday>, snapshot: CalendarSnapshot,
        today: LocalDate = month.atDay(10), viewport: DpSize = size): View {
        val result = GlanceRemoteViews().compose(context, size) { WidgetContent(month, today, holidays, snapshot) }
        var outcome: Result<View>? = null
        runOnMainSync {
            outcome = runCatching {
                // Preserve the RemoteViews root LayoutParams and let the framework host measure it.
                // Measuring the inflated child with EXACTLY can hide wrap-content/weight failures.
                val root = LayoutHost(context).apply { show(result.remoteViews) }
                val density = context.resources.displayMetrics.density
                val width = (viewport.width.value * density).roundToInt()
                val height = (viewport.height.value * density).roundToInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                root
            }
        }
        return outcome!!.getOrThrow()
    }

    private class LayoutHost(context: Context) : AppWidgetHostView(context) {
        fun show(views: RemoteViews) {
            val child = views.apply(context, this)
            prepareView(child)
            addView(child)
        }
    }

    private fun textViews(view: View): List<TextView> = buildList {
        if (view is TextView && view.visibility == View.VISIBLE && view.width > 0 && view.height > 0 && view.text.isNotEmpty()) add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(textViews(view.getChildAt(index)))
    }

    private fun assertBounds(root: View, texts: List<TextView>) {
        val bounds = texts.map { bounds(root, it) }
        caseDescription += "; bottom=${bounds.maxOfOrNull { it.bottom }}; root=${root.height}"
        check(bounds.all { it.left >= 0 && it.top >= 0 && it.right <= root.width + 1 && it.bottom <= root.height + 1 })
        for (first in bounds.indices) for (second in first + 1 until bounds.size) check(!Rect.intersects(bounds[first], bounds[second]))
        check(texts.all { it.layout == null || it.layout.height + it.compoundPaddingTop + it.compoundPaddingBottom <= it.height + 2 })
    }

    private fun bounds(root: View, text: TextView): Rect = Rect(0, 0, text.width, text.height).also {
        (root as ViewGroup).offsetDescendantRectToMyCoords(text, it)
    }

    private fun dividerViews(view: View): List<View> = buildList {
        if ((view.background as? ColorDrawable)?.color == 0xFF465C50.toInt()) add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(dividerViews(view.getChildAt(index)))
    }

    private fun assertDistributedLayout(context: Context, root: View, texts: List<TextView>, space: WidgetSpace, snapshot: CalendarSnapshot) {
        val density = context.resources.displayMetrics.density
        val padding = (space.padding * density).roundToInt()
        val legendBounds = bounds(root, texts.single { it.text.toString() == "• Feriado" })
        val dividers = dividerViews(root)
        if (space.horizontal || !space.showAgenda) {
            check(dividers.isEmpty())
            check(kotlin.math.abs(root.height - padding - legendBounds.bottom) <= 2)
        }
        if (!space.showAgenda) return
        val heading = texts.single { it.text.toString() == "Próximos compromissos" }
        val headingBounds = bounds(root, heading)
        if (!space.horizontal) {
            val line = dividers.single()
            val lineBounds = Rect(0, 0, line.width, line.height).also { (root as ViewGroup).offsetDescendantRectToMyCoords(line, it) }
            check(lineBounds.width() >= root.width - 2 * padding - 2)
            check(lineBounds.height() in 1..(2f * density).roundToInt())
            check(lineBounds.top - legendBounds.bottom in 0..(6f * density).roundToInt())
            check(headingBounds.top - lineBounds.bottom in 0..(6f * density).roundToInt())
            check(kotlin.math.abs(legendBounds.bottom - padding - (space.calendarSectionHeight * density).roundToInt()) <= 2)
        }
        var previousBottom = headingBounds.bottom
        val eventGaps = mutableListOf<Int>()
        for ((index, item) in space.events.withIndex()) {
            val date = texts.first { it.text.toString() == item.event.description(ZoneId.systemDefault()) }
            val title = texts.single { it.text.toString() == item.event.title }
            val dateBounds = bounds(root, date)
            val titleBounds = bounds(root, title)
            check(date.currentTextColor == 0xFF9ED8CB.toInt())
            check(date.textSize < title.textSize)
            val gap = dateBounds.top - previousBottom
            if (index == 0) check(gap in 0..(4f * density).roundToInt())
            else eventGaps += gap
            check(titleBounds.top - dateBounds.bottom in 0..1)
            previousBottom = titleBounds.bottom
        }
        if (space.events.size == 3) {
            check(eventGaps.all { it >= (5f * density).roundToInt() - 2 })
            check(kotlin.math.abs(eventGaps[0] - eventGaps[1]) <= 2)
            // The last complete title must reach the lower region, rather than leaving a blank block.
            check(kotlin.math.abs(root.height - padding - previousBottom) <= 2)
        } else {
            check(eventGaps.all { it in 0..(6f * density).roundToInt() })
        }
        if (snapshot.upcoming.isEmpty()) {
            val empty = texts.single { it.text.toString() == space.message }
            check(bounds(root, empty).top - previousBottom in 0..(4f * density).roundToInt())
        }
    }

    private fun saveFixturePreview(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        runOnMainSync { view.draw(Canvas(bitmap)) }
        val directory = targetContext.cacheDir
        java.io.File(directory, name).outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
