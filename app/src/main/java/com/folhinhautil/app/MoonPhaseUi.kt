package com.folhinhautil.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folhinhautil.app.domain.MoonDay
import com.folhinhautil.app.domain.MoonPhase
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Schematic vector glyphs: waxing is lit on the left, the southern-hemisphere convention.
 * No physical illumination percentage or observer-specific sky rotation is implied. */
@Composable
internal fun MoonPhaseIcon(phase: MoonPhase, modifier: Modifier = Modifier) {
    Canvas(modifier.size(12.dp).clearAndSetSemantics { }) {
        val r = size.minDimension / 2 - .7.dp.toPx()
        val cx = center.x; val cy = center.y
        val light = Color(0xFF9ED8CB)
        drawCircle(Color(0xFF354139), r)
        if (phase == MoonPhase.FULL) drawCircle(light, r)
        else if (phase != MoonPhase.NEW) {
            val waxing = phase == MoonPhase.WAXING_CRESCENT || phase == MoonPhase.FIRST_QUARTER || phase == MoonPhase.WAXING_GIBBOUS
            val bulge = when (phase) {
                MoonPhase.WAXING_CRESCENT, MoonPhase.WANING_CRESCENT -> -.9f * r
                MoonPhase.WAXING_GIBBOUS, MoonPhase.WANING_GIBBOUS -> .9f * r
                else -> 0f
            }
            val shape = Path().apply {
                moveTo(cx, cy-r)
                cubicTo(cx-.552285f*r,cy-r,cx-r,cy-.552285f*r,cx-r,cy)
                cubicTo(cx-r,cy+.552285f*r,cx-.552285f*r,cy+r,cx,cy+r)
                cubicTo(cx+bulge,cy+.6f*r,cx+bulge,cy-.6f*r,cx,cy-r)
                close()
            }
            withTransform({ scale(if (waxing) 1f else -1f, 1f, center) }) { drawPath(shape, light) }
        }
        drawCircle(light.copy(alpha = .75f), r, style = Stroke(.7.dp.toPx()))
    }
}

@Composable
internal fun LunarDayCard(day: MoonDay, zone: ZoneId) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B231F))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MoonPhaseIcon(day.phase, Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(day.phase.label, color = Color(0xFF9ED8CB))
                day.principal?.let { event ->
                    Text("Horário local aproximado: ${event.instant.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        fontSize = 12.sp, color = Color(0xFFADB9B0))
                }
            }
        }
    }
}
