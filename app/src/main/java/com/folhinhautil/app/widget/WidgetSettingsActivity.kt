package com.folhinhautil.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.launch

class WidgetSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val requested = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val manager = remember { GlanceAppWidgetManager(this) }
                    val scope = rememberCoroutineScope()
                    var ids by remember { mutableStateOf(emptyList<Int>()) }
                    var selected by remember { mutableStateOf(requested.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }) }
                    var loaded by remember { mutableStateOf(false) }
                    var categories by remember { mutableStateOf(WidgetPreferences().categories) }
                    var moon by remember { mutableStateOf(true) }
                    var saving by remember { mutableStateOf(false) }
                    var error by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        ids = manager.getGlanceIds(FolhinhaWidget::class.java).map { manager.getAppWidgetId(it) }
                    }
                    LaunchedEffect(selected) {
                        loaded = false
                        selected?.let { value ->
                            val prefs = getAppWidgetState(this@WidgetSettingsActivity, PreferencesGlanceStateDefinition,
                                manager.getGlanceIdBy(value)).widgetPreferences()
                            categories = prefs.categories; moon = prefs.showMoon; loaded = true
                        }
                    }
                    Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Personalizar widget", style = MaterialTheme.typography.headlineSmall)
                        if (selected == null) {
                            Text("Escolha o widget que deseja personalizar.")
                            if (ids.isEmpty()) Text("Adicione um widget à tela inicial para configurar.")
                            ids.forEach { value -> Button(onClick = { selected = value }) { Text("Widget #$value") } }
                        } else if (loaded) {
                            Text("Widget #$selected · Coluna direita")
                            Text("Selecione até duas categorias. A ordem abaixo será a ordem de exibição.")
                            SidebarCategory.entries.forEach { category ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = category in categories,
                                        enabled = !saving && category.available && (category in categories || categories.size < 2),
                                        onCheckedChange = { checked -> categories = if (checked) categories + category else categories - category })
                                    Text(category.label + if (!category.available) " — integração pendente" else "")
                                }
                            }
                            categories.forEachIndexed { index, category ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}. ${category.label}", Modifier.weight(1f))
                                    if (categories.size == 2) TextButton(enabled = !saving, onClick = { categories = categories.reversed() }) {
                                        Text(if (index == 0) "Descer" else "Subir")
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = moon, enabled = !saving, onCheckedChange = { moon = it })
                                Text("Mostrar fases principais da Lua")
                            }
                            if (SidebarCategory.CALENDAR in categories) Text("Usa as agendas selecionadas no aplicativo. Autorize o acesso aos calendários na tela principal.")
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(enabled = !saving, onClick = {
                                saving = true
                                scope.launch {
                                    try {
                                        val widgetId = selected!!
                                        val id = manager.getGlanceIdBy(widgetId)
                                        saveWidgetPreferences(this@WidgetSettingsActivity, id, WidgetPreferences(categories, moon))
                                        FolhinhaWidget().update(this@WidgetSettingsActivity, id)
                                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                        finish()
                                    } catch (_: RuntimeException) { error = "Não foi possível atualizar. Tente novamente."; saving = false }
                                }
                            }) { Text("Salvar") }
                        }
                        TextButton(onClick = { finish() }) { Text("Cancelar") }
                    }
                }
            }
        }
    }
}

internal suspend fun saveWidgetPreferences(context: android.content.Context, id: androidx.glance.GlanceId, value: WidgetPreferences) {
    updateAppWidgetState(context, id) { prefs ->
        prefs[SidebarSelection] = value.encode()
        prefs[ShowMoon] = value.showMoon
    }
}
