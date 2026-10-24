package com.folhinhautil.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhinhautil.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

private val Green = Color(0xFFB6E4BE)
private val Aqua = Color(0xFF9ED8CB)
private val Red = Color(0xFFFF9894)
private val Amber = Color(0xFFECC88B)
private val Muted = Color(0xFFADB9B0)
private val Panel = Color(0xFF1B231F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Green, onPrimary = Color(0xFF101513), background = Color(0xFF101513),
                surface = Panel, onBackground = Color(0xFFE4EAE5), onSurface = Color(0xFFE4EAE5))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { FolhinhaApp() }
            }
        }
    }
}

@Composable
private fun FolhinhaApp(model: CalendarViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var showCalendars by rememberSaveable { mutableStateOf(false) }
    var permissionAttempted by rememberSaveable { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionAttempted = true
        model.refresh()
        if (it) showCalendars = true
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    // Refresh the date and current events while the screen remains open across midnight.
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { kotlinx.coroutines.delay(60_000); model.refresh() }
        }
    }
    val holidays = remember(state.month.year) { BrazilianHolidayProvider().forYear(state.month.year) }
    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("FOLHINHA ÚTIL", color = Green, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 13.sp)
                Text("Seu tempo, por perto.", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = { model.refresh() }, enabled = !state.loading) {
                Icon(painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.refresh_calendar), tint = Aqua)
            }
        }
        TextButton(onClick = { context.startActivity(Intent(context, PersonalEventsActivity::class.java)) }) {
            Text("Eventos pessoais")
        }
        BoxWithConstraints {
            if (maxWidth >= 700.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) { MonthPanel(state, holidays, model) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        DayPanel(state, holidays)
                        AgendaPanel(state, permissionAttempted,
                            { requestPermission.launch(Manifest.permission.READ_CALENDAR) },
                            { showCalendars = true },
                            { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    MonthPanel(state, holidays, model)
                    DayPanel(state, holidays)
                    AgendaPanel(state, permissionAttempted,
                        { requestPermission.launch(Manifest.permission.READ_CALENDAR) },
                        { showCalendars = true },
                        { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
                }
            }
        }
        Text("O widget usa o mês atual. Adicione-o pela tela inicial do celular.", color = Muted, fontSize = 12.sp)
    }
    if (showCalendars && state.snapshot.permissionGranted) {
        var selection by remember(state.snapshot.calendars) { mutableStateOf(state.snapshot.selectedIds) }
        AlertDialog(onDismissRequest = { showCalendars = false }, title = { Text("Seus calendários") }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text("Selecione as agendas que deseja ver. A sincronização é feita pelo Android.", fontSize = 14.sp)
                if (state.snapshot.calendars.isEmpty()) Text("Nenhum calendário disponível. Adicione ou sincronize uma conta nas configurações do celular.")
                state.snapshot.calendars.forEach { calendar ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selection = if (calendar.id in selection) selection - calendar.id else selection + calendar.id
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = calendar.id in selection, onCheckedChange = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(calendar.name)
                            Text(calendar.account, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { model.selectCalendars(selection); showCalendars = false }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { showCalendars = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun MonthPanel(state: CalendarUiState, holidays: List<Holiday>, model: CalendarViewModel) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { model.navigate(-1) }, enabled = state.month > YearMonth.of(FirstYear, 1),
                    modifier = Modifier.semantics { contentDescription = "Mês anterior" }) { Text("‹", fontSize = 28.sp) }
                Text(state.month.label(), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                TextButton(onClick = { model.navigate(1) }, enabled = state.month < YearMonth.of(LastYear, 12),
                    modifier = Modifier.semantics { contentDescription = "Próximo mês" }) { Text("›", fontSize = 28.sp) }
            }
            Row {
                WeekLabels.forEachIndexed { index, label ->
                    Text(label, Modifier.weight(1f), color = if (index == 0 || index == 6) Red else Muted,
                        textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            monthGrid(state.month).forEach { week ->
                Row {
                    week.forEach { date ->
                        val datesHolidays = holidays.filter { it.date == date }
                        val today = date == state.today
                        val selected = date == state.selectedDate
                        val color = if (date?.isWeekend() == true) Red else MaterialTheme.colorScheme.onSurface
                        Box(Modifier.weight(1f).heightIn(min = 48.dp).padding(2.dp)
                            .then(if (today) Modifier.background(Color(0xFF34513C), CircleShape) else Modifier)
                            .then(if (selected) Modifier.border(1.dp, Green, CircleShape) else Modifier)
                            .then(if (date != null) Modifier.clickable { model.selectDay(date) }.semantics {
                                contentDescription = "${date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", BrazilianLocale))}" +
                                    (if (today) ", hoje" else "") + datesHolidays.joinToString("") { ", ${it.name}, ${it.kind.label}" }
                                this.selected = selected
                            } else Modifier), contentAlignment = Alignment.Center) {
                            if (date != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(date.dayOfMonth.toString(), color = color, fontSize = 16.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal)
                                Box(Modifier.size(4.dp).background(if (datesHolidays.isEmpty()) Color.Transparent
                                    else if (datesHolidays.any { it.kind == HolidayKind.NATIONAL }) Red else Amber, CircleShape))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("● Feriado nacional", color = Red, fontSize = 11.sp)
                    BoxWithConstraints {
                        val fullLabel = "● Pontos facultativos e outras datas"
                        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                        val style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.roundToPx() }
                        val label = if (measurer.measure(fullLabel, style).size.width <= widthPx) fullLabel else "● Outras datas"
                        Text(label, color = Amber, style = style, maxLines = 1)
                    }
                }
                TextButton(onClick = model::today) { Text("Hoje") }
            }
        }
    }
}

@Composable
private fun DayPanel(state: CalendarUiState, holidays: List<Holiday>) {
    val date = state.selectedDate
    val datesHolidays = holidays.filter { it.date == date }
    val events = state.snapshot.monthEvents.filter { it.occursOn(date, ZoneId.systemDefault()) }
        .sortedBy { it.effectiveStart(ZoneId.systemDefault()) }
    val canShowEvents = state.snapshot.permissionGranted && state.snapshot.error == null
    if (datesHolidays.isEmpty() && (!canShowEvents || events.isEmpty())) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(date.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", BrazilianLocale))
            .replaceFirstChar { it.titlecase(BrazilianLocale) }, fontWeight = FontWeight.SemiBold)
        datesHolidays.forEach { holiday ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(holiday.name, color = if (holiday.kind == HolidayKind.NATIONAL) Red else Amber)
                    Text(holiday.kind.label, fontSize = 12.sp)
                    Text(holiday.source, fontSize = 11.sp, color = Muted)
                }
            }
        }
        if (canShowEvents) events.forEach { AppointmentCard(it) }
    }
}

@Composable
private fun AgendaPanel(state: CalendarUiState, attempted: Boolean, request: () -> Unit, calendars: () -> Unit, settings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Próximos compromissos", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (state.snapshot.permissionGranted) TextButton(onClick = calendars) { Text("Agendas") }
        }
        when {
            state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
            !state.snapshot.permissionGranted -> {
                Text("Conecte os calendários já sincronizados no celular. O acesso é somente para leitura.", color = Muted, fontSize = 14.sp)
                Button(onClick = request) { Text("Permitir leitura da agenda") }
                if (attempted) TextButton(onClick = settings) { Text("Revisar permissão nas configurações") }
            }
            state.snapshot.error != null -> Text(state.snapshot.error, color = Amber)
            state.snapshot.calendars.isEmpty() -> Text("Nenhum calendário disponível. Ative a sincronização de uma agenda nas configurações do celular.", color = Muted)
            state.snapshot.selectedIds.isEmpty() -> Text("Selecione pelo menos uma agenda em Agendas.", color = Muted)
            state.snapshot.upcoming.isEmpty() -> Text("Nenhum compromisso nos próximos 90 dias nas agendas selecionadas.", color = Muted)
            else -> state.snapshot.upcoming.take(3).forEach { AppointmentCard(it) }
        }
    }
}

@Composable
private fun AppointmentCard(event: Appointment) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.description(ZoneId.systemDefault()), color = Green, fontSize = 12.sp)
            Text(event.title, fontWeight = FontWeight.Medium)
        }
    }
}
