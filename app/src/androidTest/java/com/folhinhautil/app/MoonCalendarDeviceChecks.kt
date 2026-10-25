package com.folhinhautil.app

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.folhinhautil.app.domain.*
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

/** Operates only the debug fixture activity; no real calendar/personal store is opened. */
fun runMoonCalendarChecks(test: Instrumentation, progress: (String) -> Unit): Int {
    var cases = 0
    fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        add(root)
        for (index in 0 until root.childCount) root.getChild(index)?.let { addAll(nodes(it)) }
    }
    fun awaitNodes(predicate: (List<AccessibilityNodeInfo>) -> Boolean): List<AccessibilityNodeInfo> {
        val deadline = System.nanoTime() + 5_000_000_000
        var lastPackage = "none"
        var fixtureText = false
        var descriptions = ""
        var visibleTexts = ""
        while (System.nanoTime() < deadline) {
            test.waitForIdleSync()
            // Refresh framework accessibility cache after Compose changes the selected date.
            if (android.os.Build.VERSION.SDK_INT >= 33) test.uiAutomation.clearCache()
            test.uiAutomation.rootInActiveWindow?.let { root -> nodes(root).let { items ->
                lastPackage = root.packageName?.toString().orEmpty()
                fixtureText = items.any { it.text?.toString() == "Validação lunar — dados sintéticos" }
                if (fixtureText) visibleTexts = items.mapNotNull { it.text?.toString() }.joinToString(" | ")
                if (fixtureText) descriptions = items.mapNotNull { it.contentDescription?.toString() }.take(3).joinToString(" | ")
                if (predicate(items)) return items
            } }
            Thread.sleep(50)
        }
        error("Synthetic lunar UI did not reach expected state; package=$lastPackage; fixture=$fixtureText; descriptions=$descriptions; texts=$visibleTexts")
    }
    fun scrollToEdge(action: Int) {
        repeat(8) {
            if (android.os.Build.VERSION.SDK_INT >= 33) test.uiAutomation.clearCache()
            val root = test.uiAutomation.rootInActiveWindow ?: return
            val scroll = nodes(root).firstOrNull { it.isScrollable } ?: return
            if (!scroll.performAction(action)) return
            test.waitForIdleSync()
            Thread.sleep(100)
        }
    }
    val month = YearMonth.of(2026,10)
    val zone = ZoneId.systemDefault()
    val moonDays = MoonPhases.forMonth(month,zone)
    for (scale in listOf(1f,1.3f,1.5f)) {
        progress("synthetic lunar calendar; font=$scale; grid")
        val activity = test.startActivitySync(Intent(test.targetContext,MoonCalendarPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("month",month.toString()).putExtra("fontScale",scale))
        try {
            val grid = awaitNodes { rows -> rows.any { it.contentDescription?.toString()?.startsWith("1 de outubro de 2026,") == true } }
            for (day in 1..month.lengthOfMonth()) {
                progress("synthetic lunar calendar; font=$scale; grid day=$day")
                val date = month.atDay(day)
                val label = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",BrazilianLocale))
                check(grid.any { it.contentDescription?.toString()?.startsWith(label + ",") == true &&
                    it.contentDescription.toString().contains(moonDays.getValue(date).phase.label) }) { "Missing date or lunar accessibility label: $day" }
                cases++
            }
            for (day in listOf(10,12,18,24,26)) {
                progress("synthetic lunar calendar; font=$scale; select day=$day")
                scrollToEdge(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                val date = month.atDay(day)
                val label = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",BrazilianLocale))
                val cell = awaitNodes { it.any { n -> n.contentDescription?.toString()?.startsWith(label + ",") == true } }
                    .first { it.contentDescription?.toString()?.startsWith(label + ",") == true }
                val bounds = Rect().also { cell.getBoundsInScreen(it) }
                check(bounds.width() > 0 && bounds.height() > 0) { "Empty cell bounds: $bounds" }
                // Compose may expose the label as a non-clickable leaf; activate its clickable date container.
                var clickTarget = cell
                while (!clickTarget.isClickable) clickTarget = clickTarget.parent ?: error("Date has no clickable container")
                check(clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Date container rejected accessibility click" }
                test.waitForIdleSync()
                Thread.sleep(100)
                scrollToEdge(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                val phase = moonDays.getValue(date).phase.label
                val content = awaitNodes { it.any { node -> node.text?.toString() == phase } }
                if (day == 12) {
                    check(content.any { it.text?.toString() == "Nossa Senhora Aparecida" })
                    check(content.any { it.text?.toString() == "Compromisso de validação" })
                }
                if (day == 24) check(content.none { it.text?.toString() == "Compromisso de validação" })
                if (day == 26) {
                    val bitmap = test.uiAutomation.takeScreenshot() ?: error("Cannot capture synthetic lunar fixture")
                    File(test.targetContext.cacheDir,"moon-samsung-$scale.png").outputStream().use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
                    }
                    bitmap.recycle()
                }
                cases++
            }
        } catch (failure: Throwable) {
            if (test.uiAutomation.rootInActiveWindow?.let { root -> root.packageName?.toString() == test.targetContext.packageName && nodes(root).any { it.text?.toString() == "Validação lunar — dados sintéticos" } } == true) {
                test.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(test.targetContext.cacheDir,"moon-synthetic-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle()
                }
            }
            throw failure
        } finally { test.runOnMainSync { activity.finish() } }
    }
    return cases
}
