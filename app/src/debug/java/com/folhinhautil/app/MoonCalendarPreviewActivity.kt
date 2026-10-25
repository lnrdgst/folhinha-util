package com.folhinhautil.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folhinhautil.app.data.CalendarSnapshot
import com.folhinhautil.app.domain.*
import java.time.*

/** Debug-only fixture host. No repository, permissions, user events or saved preferences. */
class MoonCalendarPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val month = YearMonth.parse(intent.getStringExtra("month") ?: "2026-10")
        val scale = intent.getFloatExtra("fontScale", 1f)
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB6E4BE),
                    onSurface = Color(0xFFE4EAE5), background = Color(0xFF101513), surface = Color(0xFF1B231F))) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val zone = ZoneId.systemDefault()
                        var selected by remember { mutableStateOf(month.atDay(1)) }
                        val moons = remember(month,zone) { MoonPhases.forMonth(month,zone) }
                        val holidays = remember(month.year) { BrazilianHolidayProvider().forYear(month.year) }
                        val fixtureDate = month.atDay(12)
                        val fixtureEvent = Appointment(-1,-1,"Compromisso de validação",
                            fixtureDate.atTime(10,0).atZone(zone).toInstant(), fixtureDate.atTime(11,0).atZone(zone).toInstant(), false)
                        val state = CalendarUiState(month, selected, month.atDay(25),
                            CalendarSnapshot(permissionGranted = true, monthEvents = listOf(fixtureEvent)), loading = false)
                        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Validação lunar — dados sintéticos", fontSize = 12.sp)
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B231F))) {
                                Column(Modifier.padding(12.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(month.label(), fontSize = 19.sp)
                                    Row { WeekLabels.forEachIndexed { index,label -> Text(label,Modifier.weight(1f),
                                        color = if(index == 0 || index == 6) Color(0xFFFF9894) else Color(0xFFADB9B0),
                                        textAlign = TextAlign.Center, fontSize = 10.sp) } }
                                    MonthDayGrid(month,state.today,selected,holidays,moons) { selected = it }
                                }
                            }
                            DayPanel(state,holidays,moons.getValue(selected),zone)
                        }
                    }
                }
            }
        }
    }
}
