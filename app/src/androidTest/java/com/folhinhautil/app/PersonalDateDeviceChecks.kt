package com.folhinhautil.app

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.folhinhautil.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** Exercises the real form, real Material dialog and cache-only persistence, never the user's store. */
fun runPersonalDateChecks(test: Instrumentation, progress: (String) -> Unit): Int {
    fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        add(root)
        for (index in 0 until root.childCount) root.getChild(index)?.let { addAll(nodes(it)) }
    }
    fun awaitNodes(predicate: (List<AccessibilityNodeInfo>) -> Boolean): List<AccessibilityNodeInfo> {
        val deadline = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < deadline) {
            test.waitForIdleSync()
            if (Build.VERSION.SDK_INT >= 33) test.uiAutomation.clearCache()
            test.uiAutomation.rootInActiveWindow?.let { root -> nodes(root).let { if (predicate(it)) return it } }
            Thread.sleep(50)
        }
        error("Date picker fixture did not reach expected UI state")
    }
    fun text(value: String) = awaitNodes { rows -> rows.any { it.text?.toString() == value } }
        .first { it.text?.toString() == value }
    fun click(node: AccessibilityNodeInfo) {
        var target = node
        while (!target.isClickable) target = target.parent ?: error("No clickable fixture control")
        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    var cases = 0
    for (date in listOf(LocalDate.of(2026,12,27), LocalDate.of(1940,12,27), LocalDate.of(2024,2,29))) {
        progress("synthetic date picker; year=${date.year}; edit/cancel/confirm/save")
        val activity = test.startActivitySync(Intent(test.targetContext, PersonalDatePreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("date",date.toString())) as PersonalDatePreviewActivity
        val chosen = date.minusDays(1)
        try {
            click(text("Editar"))
            text(formatPersonalDate(date))
            click(text("Data do evento"))
            awaitNodes { rows -> rows.any { it.isSelected && it.text?.toString() == date.dayOfMonth.toString() } }
            click(text(chosen.dayOfMonth.toString()))
            click(text("Cancelar"))
            text(formatPersonalDate(date))
            cases++
            click(text("Data do evento"))
            awaitNodes { rows -> rows.any { it.isSelected && it.text?.toString() == date.dayOfMonth.toString() } }
            click(text(chosen.dayOfMonth.toString()))
            click(text("Confirmar"))
            text(formatPersonalDate(chosen))
            click(text("Salvar"))
            text(formatPersonalDate(chosen) + " · Anual")
            val saved = runBlocking { activity.fixtureRepository.events.first() }.single()
            check(saved.id == "date-fixture" && saved.title == "Evento de teste" && saved.date == chosen && saved.annual)
            cases++
        } finally { test.runOnMainSync { activity.finish() }; test.waitForIdleSync() }
        val reopened = test.startActivitySync(Intent(test.targetContext, PersonalDatePreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("reset",false)) as PersonalDatePreviewActivity
        try {
            text(formatPersonalDate(chosen) + " · Anual")
            check(runBlocking { reopened.fixtureRepository.events.first() }.single().date == chosen)
            cases++
        } finally { test.runOnMainSync { reopened.finish() }; test.waitForIdleSync() }
    }
    // New-event entry uses the same date control, keeping an independently editable recurrence checkbox.
    progress("synthetic date picker; create event")
    val activity = test.startActivitySync(Intent(test.targetContext, PersonalDatePreviewActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("reset",false)) as PersonalDatePreviewActivity
    try {
        click(text("Cadastrar evento"))
        text(formatPersonalDate(LocalDate.now()))
        click(text("Data do evento"))
        click(text("Cancelar"))
        text(formatPersonalDate(LocalDate.now()))
        click(text("Cancelar"))
        check(runBlocking { activity.fixtureRepository.events.first() }.size == 1)
        cases++
    } finally { test.runOnMainSync { activity.finish() } }
    return cases
}
