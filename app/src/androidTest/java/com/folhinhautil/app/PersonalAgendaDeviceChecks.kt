package com.folhinhautil.app

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.folhinhautil.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** UI writes are restricted to the existing cache-only fixture. Live navigation is read-only. */
fun runPersonalAgendaChecks(test: Instrumentation, progress: (String) -> Unit): Int {
    var cases = 0
    fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        add(root)
        for (i in 0 until root.childCount) root.getChild(i)?.let { addAll(nodes(it)) }
    }
    fun current(): List<AccessibilityNodeInfo> {
        test.waitForIdleSync()
        if (Build.VERSION.SDK_INT >= 33) test.uiAutomation.clearCache()
        return test.uiAutomation.rootInActiveWindow?.let(::nodes).orEmpty()
    }
    fun awaitNodes(predicate: (List<AccessibilityNodeInfo>) -> Boolean): List<AccessibilityNodeInfo> {
        val deadline = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < deadline) {
            val rows = current()
            if (predicate(rows)) return rows
            Thread.sleep(50)
        }
        error("Minha Agenda fixture did not reach expected UI state")
    }
    fun text(label: String) = awaitNodes { rows -> rows.any { it.text?.toString() == label } }
        .first { it.text?.toString() == label }
    fun click(node: AccessibilityNodeInfo) {
        var target = node
        while (!target.isClickable) target = target.parent ?: error("Fixture control is not clickable")
        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    fun scrollTo(label: String): AccessibilityNodeInfo {
        val deadline = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < deadline) {
            val rows = current()
            rows.firstOrNull { it.text?.toString() == label && it.isVisibleToUser }?.let { return it }
            rows.firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(50)
        }
        error("Minha Agenda fixture item not found: $label")
    }
    fun finish(activity: Activity) {
        val destroyed = CountDownLatch(1)
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) { if (a === activity) destroyed.countDown() }
        }
        activity.application.registerActivityLifecycleCallbacks(callbacks)
        try {
            test.runOnMainSync { activity.finish() }
            check(destroyed.await(5, TimeUnit.SECONDS)) { "Fixture did not close its DataStore" }
        } finally { activity.application.unregisterActivityLifecycleCallbacks(callbacks) }
    }
    fun open(mode: String, reset: Boolean = true) = test.startActivitySync(
        Intent(test.targetContext, PersonalDatePreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("agendaFixture", mode).putExtra("reset", reset)) as PersonalDatePreviewActivity
    fun events(activity: PersonalDatePreviewActivity) = runBlocking { activity.fixtureRepository.events.first() }
    fun title(value: String) {
        val field = awaitNodes { it.any { n -> n.className?.toString() == "android.widget.EditText" } }
            .single { it.className?.toString() == "android.widget.EditText" }
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        awaitNodes { it.any { n -> n.className?.toString() == "android.widget.EditText" && n.text?.contains(value) == true } }
    }
    @Suppress("DEPRECATION")
    fun annual(checked: Boolean) {
        fun checkbox(rows: List<AccessibilityNodeInfo>) = rows.single { it.className?.toString() == "android.widget.CheckBox" }
        val node = checkbox(awaitNodes { it.any { n -> n.className?.toString() == "android.widget.CheckBox" } })
        if (node.isChecked != checked) click(node)
        awaitNodes { rows -> rows.any { it.className?.toString() == "android.widget.CheckBox" && it.isChecked == checked } }
    }
    fun savedTitle(label: String) {
        awaitNodes { rows -> rows.none { it.className?.toString() == "android.widget.EditText" } &&
            rows.any { it.text?.toString() == label } }
    }
    fun preview(name: String) {
        text("Minha Agenda")
        val image = test.uiAutomation.takeScreenshot() ?: error("Cannot capture synthetic agenda fixture")
        File(test.targetContext.cacheDir, name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }

    val today = LocalDate.now()
    progress("synthetic agenda; mixed sections and ordering")
    var activity = open("mixed")
    try {
        text("É hoje! · ${formatPersonalDate(today)}")
        preview("minha-agenda-proximos.png")
        val expected = listOf("Próximos eventos", "Evento de hoje", "Aniversário anual", "Reunião futura",
            "Eventos encerrados", "Registro encerrado")
        val seen = mutableListOf<String>()
        val labels = mutableSetOf<String>()
        val original = events(activity)
        repeat(12) {
            val rows = current()
            rows.filter { it.isVisibleToUser }.sortedBy { node -> Rect().also { node.getBoundsInScreen(it) }.top }.forEach { node ->
                node.text?.toString()?.let { label ->
                    labels += label
                    if (label in expected && label !in seen) seen += label
                }
            }
            if (seen.size != expected.size) rows.firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(50)
        }
        check(seen == expected) { "Agenda order or section separation is incorrect" }
        check(labels.contains("2 dias restantes · ${formatPersonalDate(today.plusDays(2))}"))
        check(labels.contains("5 dias restantes · ${formatPersonalDate(today.plusDays(5))}"))
        check(labels.contains("${formatPersonalDate(original.single { it.annual }.date)} · Anual"))
        check(labels.contains("Evento encerrado"))
        check(events(activity) == original)
        preview("minha-agenda-encerrados.png")
        cases++
    } finally { finish(activity) }

    progress("synthetic agenda; empty and create")
    activity = open("empty")
    var createdId = ""
    try {
        text("Nenhum evento pessoal cadastrado.")
        check(events(activity).isEmpty())
        preview("minha-agenda-vazia.png")
        cases++
        click(text("Cadastrar evento"))
        title("Compromisso de teste")
        annual(true)
        click(text("Salvar"))
        savedTitle("Compromisso de teste")
        val saved = events(activity).single()
        check(saved.title == "Compromisso de teste" && saved.date == today && saved.annual)
        createdId = saved.id
        cases++
        progress("synthetic agenda; edit and cancel deletion")
        click(scrollTo("Editar"))
        title("Compromisso editado")
        annual(false)
        click(text("Salvar"))
        savedTitle("Compromisso editado")
        check(events(activity).single() == saved.copy(title = "Compromisso editado", annual = false))
        cases++
        click(scrollTo("Excluir"))
        text("Excluir evento pessoal?")
        click(text("Cancelar"))
        savedTitle("Compromisso editado")
        check(events(activity).size == 1)
        cases++
    } finally { finish(activity) }
    progress("synthetic agenda; reopen and delete")
    activity = open("empty", reset = false)
    try {
        savedTitle("Compromisso editado")
        check(events(activity).single() == PersonalEvent(createdId, "Compromisso editado", today))
        cases++
        click(scrollTo("Excluir"))
        text("Excluir evento pessoal?")
        click(text("Excluir"))
        text("Nenhum evento pessoal cadastrado.")
        check(events(activity).isEmpty())
        cases++
    } finally { finish(activity) }

    progress("synthetic agenda; past event editing and annual recurrence")
    activity = open("past")
    try {
        text("Eventos encerrados")
        scrollTo("Registro encerrado")
        val original = events(activity).single()
        check(!original.annual && original.date < today)
        click(scrollTo("Editar"))
        title("Registro revisado")
        annual(true)
        click(text("Salvar"))
        savedTitle("Registro revisado")
        text("Próximos eventos")
        check(events(activity).single() == original.copy(title = "Registro revisado", annual = true))
        check(current().none { it.text?.toString() == "Eventos encerrados" })
        cases++
        click(scrollTo("Editar"))
        annual(false)
        click(text("Salvar"))
        savedTitle("Registro revisado")
        text("Eventos encerrados")
        check(events(activity).single() == original.copy(title = "Registro revisado"))
        cases++
        click(scrollTo("Excluir"))
        text("Excluir evento pessoal?")
        click(text("Excluir"))
        text("Nenhum evento pessoal cadastrado.")
        check(events(activity).isEmpty())
        cases++
    } finally { finish(activity) }

    progress("synthetic agenda; read-only main navigation")
    val main = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    try {
        val month = YearMonth.now().label()
        text(month)
        click(text("Minha Agenda"))
        text("Seus eventos pessoais, em um só lugar.")
        click(text("Voltar"))
        text("FOLHINHA ÚTIL")
        text(month)
        cases++
    } finally { finish(main) }
    return cases
}
