package com.folhinhautil.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.glance.appwidget.updateAll
import com.folhinhautil.app.data.PersonalEventsRepository
import com.folhinhautil.app.domain.*
import com.folhinhautil.app.widget.FolhinhaWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class PersonalEventsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB6E4BE), onPrimary = Color(0xFF101513),
                background = Color(0xFF101513), surface = Color(0xFF1B231F))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { PersonalEventsScreen(back = { finish() }) }
            }
        }
    }
}

private data class PersonalListState(val events: List<PersonalEvent> = emptyList(), val ready: Boolean = false, val error: Boolean = false)
private val AgendaAmber = Color(0xFFECC88B)
private val AgendaMuted = Color(0xFFADB9B0)
private val AgendaPanel = Color(0xFF1B231F)

@Composable
internal fun PersonalEventsScreen(back: () -> Unit, repositoryOverride: PersonalEventsRepository? = null,
    onSaved: (suspend () -> Unit)? = null) {
    val context = LocalContext.current
    val repository = repositoryOverride ?: remember { PersonalEventsRepository(context) }
    val states = remember { repository.events.map { PersonalListState(it, ready = true) }
        .catch { emit(PersonalListState(error = true)) } }
    val state by states.collectAsStateWithLifecycle(PersonalListState())
    val scope = rememberCoroutineScope()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf("") }
    var annual by rememberSaveable { mutableStateOf(false) }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }

    fun edit(event: PersonalEvent?) {
        editingId = event?.id; title = event?.title.orEmpty()
        dateText = formatPersonalDate(event?.date ?: LocalDate.now()); annual = event?.annual ?: false
        validation = null; editorOpen = true
    }
    suspend fun updateWidget() {
        try { if (onSaved != null) onSaved() else FolhinhaWidget().updateAll(context) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: RuntimeException) { operationError = "Alteração salva. O widget será atualizado na próxima atualização permitida." }
    }
    val today = LocalDate.now()
    val upcoming = upcomingPersonalEvents(state.events, today)
    val ended = state.events.filter { nextPersonalOccurrence(it, today) == null }
        .sortedWith(compareByDescending<PersonalEvent> { it.date }.thenBy { it.id })
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = back) { Text("Voltar", color = AgendaAmber) }
            Text("Minha Agenda", Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall, color = AgendaAmber)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Seus eventos pessoais, em um só lugar.", color = AgendaMuted)
                Text("Datas importantes salvas somente neste aparelho.", color = AgendaMuted,
                    style = MaterialTheme.typography.bodySmall)
            }
            item {
                Button(onClick = { edit(null) }, enabled = state.ready && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgendaAmber, contentColor = Color(0xFF101513))) {
                    Text("Cadastrar evento")
                }
            }
            if (!state.ready && !state.error) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error) item {
                Text("Não foi possível ler os eventos salvos. Seus dados não foram substituídos.", color = MaterialTheme.colorScheme.error)
            }
            operationError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (state.ready && state.events.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nenhum evento pessoal cadastrado.", color = AgendaMuted)
                    Text("Cadastre uma data importante para começar.", color = AgendaMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (upcoming.isNotEmpty()) {
                item { AgendaSectionHeading("Próximos eventos") }
                items(upcoming, key = { "upcoming-${it.event.id}" }) { occurrence ->
                    AgendaEventCard(occurrence.event, occurrence, busy,
                        onEdit = { edit(occurrence.event) }, onDelete = { deletingId = occurrence.event.id })
                }
            } else if (state.ready && state.events.isNotEmpty()) item {
                Text("Nenhum evento próximo.", color = AgendaMuted)
            }
            if (ended.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AgendaSectionHeading("Eventos encerrados")
                        Text("Datas passadas que continuam disponíveis para consulta.", color = AgendaMuted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(ended, key = { "ended-${it.id}" }) { event ->
                    AgendaEventCard(event, null, busy, onEdit = { edit(event) }, onDelete = { deletingId = event.id })
                }
            }
        }
    }
    if (editorOpen) AlertDialog(onDismissRequest = { if (!busy) editorOpen = false }, title = { Text(if (editingId == null) "Cadastrar evento" else "Editar evento") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it.take(120) }, label = { Text("Título") }, singleLine = true, enabled = !busy)
            PersonalEventDateField(parsePersonalDate(dateText) ?: LocalDate.now(), enabled = !busy) {
                dateText = formatPersonalDate(it)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(annual, { annual = it }, enabled = !busy)
                Text("Repetir anualmente")
            }
            if (annual && parsePersonalDate(dateText)?.let { it.monthValue == 2 && it.dayOfMonth == 29 } == true)
                Text("Em anos sem 29/02, a ocorrência será em 28/02.", style = MaterialTheme.typography.bodySmall)
            validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        val parsed = parsePersonalDate(dateText)
        when {
            title.isBlank() -> validation = "Informe um título."
            parsed == null -> validation = "Informe uma data válida no formato dd/MM/aaaa."
            else -> scope.launch {
                busy = true; operationError = null
                try {
                    repository.save(PersonalEvent(editingId ?: UUID.randomUUID().toString(), title.trim(), parsed, annual))
                    editorOpen = false; updateWidget()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { validation = "Não foi possível salvar. Tente novamente." }
                finally { busy = false }
            }
        }
    }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = { editorOpen = false }, enabled = !busy) { Text("Cancelar") } })
    if (deletingId != null) AlertDialog(onDismissRequest = { if (!busy) deletingId = null }, title = { Text("Excluir evento pessoal?") },
        text = { Text("O evento será removido deste aparelho.") }, confirmButton = {
            TextButton(enabled = !busy, onClick = { scope.launch {
                busy = true; operationError = null
                try { repository.delete(checkNotNull(deletingId)); deletingId = null; updateWidget() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { operationError = "Não foi possível excluir. Tente novamente." }
                finally { busy = false }
            } }) { Text("Excluir") }
        }, dismissButton = { TextButton(onClick = { deletingId = null }, enabled = !busy) { Text("Cancelar") } })
}

@Composable
private fun AgendaSectionHeading(label: String) {
    Text(label, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold, color = AgendaAmber)
}

@Composable
private fun AgendaEventCard(event: PersonalEvent, occurrence: PersonalOccurrence?, busy: Boolean,
    onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AgendaPanel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(event.title, fontWeight = FontWeight.Medium, color = Color(0xFFE4EAE5))
            Text(formatPersonalDate(event.date) + if (event.annual) " · Anual" else "", color = AgendaMuted)
            Text(occurrence?.let { "${it.countdown} · ${formatPersonalDate(it.date)}" } ?: "Evento encerrado",
                color = if (occurrence == null) AgendaMuted else Color(0xFF9ED8CB))
            Row {
                TextButton(onClick = onEdit, enabled = !busy) { Text("Editar", color = AgendaAmber) }
                TextButton(onClick = onDelete, enabled = !busy) { Text("Excluir", color = Color(0xFFFF9894)) }
            }
        }
    }
}
