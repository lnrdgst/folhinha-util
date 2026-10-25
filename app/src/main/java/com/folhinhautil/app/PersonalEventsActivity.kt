package com.folhinhautil.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
                Surface(Modifier.fillMaxSize()) { PersonalEventsScreen(back = { finish() }) }
            }
        }
    }
}

private data class PersonalListState(val events: List<PersonalEvent> = emptyList(), val ready: Boolean = false, val error: Boolean = false)

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
    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = back) { Text("Voltar") }
        Text("Eventos pessoais", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text("Datas importantes salvas somente neste aparelho.")
        Button(onClick = { edit(null) }, enabled = state.ready && !busy) { Text("Cadastrar evento") }
        if (!state.ready && !state.error) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.error) Text("Não foi possível ler os eventos salvos. Seus dados não foram substituídos.", color = MaterialTheme.colorScheme.error)
        operationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.ready && state.events.isEmpty()) Text("Nenhum evento pessoal cadastrado.")
        state.events.sortedWith(compareBy<PersonalEvent> { nextPersonalOccurrence(it, LocalDate.now())?.date ?: LocalDate.MAX }.thenBy { it.date }).forEach { event ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(event.title, fontWeight = FontWeight.Medium)
                    Text(formatPersonalDate(event.date) + if (event.annual) " · Anual" else "")
                    val next = nextPersonalOccurrence(event, LocalDate.now())
                    Text(next?.let { "${it.countdown} · ${formatPersonalDate(it.date)}" } ?: "Evento encerrado", color = MaterialTheme.colorScheme.primary)
                    Row {
                        TextButton(onClick = { edit(event) }, enabled = !busy) { Text("Editar") }
                        TextButton(onClick = { deletingId = event.id }, enabled = !busy) { Text("Excluir") }
                    }
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
