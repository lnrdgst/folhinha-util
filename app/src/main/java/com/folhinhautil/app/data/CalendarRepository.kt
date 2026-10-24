package com.folhinhautil.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars as c
import android.provider.CalendarContract.Instances as i
import com.folhinhautil.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*

data class CalendarSnapshot(
    val permissionGranted: Boolean = false,
    val calendars: List<CalendarInfo> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val monthEvents: List<Appointment> = emptyList(),
    val upcoming: List<Appointment> = emptyList(),
    val error: String? = null,
)

class CalendarRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("calendar_selection", Context.MODE_PRIVATE)
    fun hasPermission() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun select(ids: Set<Long>) { prefs.edit().putStringSet("ids", ids.map(Long::toString).toSet()).apply() }

    suspend fun load(month: YearMonth = YearMonth.now()): CalendarSnapshot = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext CalendarSnapshot()
        try {
            val calendars = readCalendars()
            val stored = prefs.getStringSet("ids", null)
            val selected = stored?.mapNotNull(String::toLongOrNull)?.toSet()
                ?: calendars.map { it.id }.toSet().also { select(it) }
            val active = selected.intersect(calendars.map { it.id }.toSet())
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val today = LocalDate.now(zone)
            // Include UTC all-day boundaries even in zones ahead of or behind UTC.
            fun start(date: LocalDate) = minOf(date.atStartOfDay(zone).toInstant(), date.atStartOfDay(ZoneOffset.UTC).toInstant())
            fun end(date: LocalDate) = maxOf(date.atStartOfDay(zone).toInstant(), date.atStartOfDay(ZoneOffset.UTC).toInstant())
            val monthEvents = readInstances(active, start(month.atDay(1)), end(month.plusMonths(1).atDay(1)))
            val upcoming = readInstances(active, start(today), end(today.plusDays(90)))
            CalendarSnapshot(true, calendars, active, monthEvents, upcomingAppointments(upcoming, now, zone))
        } catch (_: SecurityException) {
            CalendarSnapshot(permissionGranted = hasPermission(), error = "Acesso à agenda indisponível. Confira a permissão nas configurações.")
        } catch (_: RuntimeException) {
            CalendarSnapshot(permissionGranted = hasPermission(), error = "Não foi possível consultar a agenda. Tente atualizar.")
        }
    }

    private fun readCalendars(): List<CalendarInfo> {
        val result = mutableListOf<CalendarInfo>()
        context.contentResolver.query(c.CONTENT_URI,
            arrayOf(c._ID, c.CALENDAR_DISPLAY_NAME, c.ACCOUNT_NAME, c.CALENDAR_COLOR),
            "${c.CALENDAR_ACCESS_LEVEL} >= ?", arrayOf(c.CAL_ACCESS_READ.toString()), "${c.CALENDAR_DISPLAY_NAME} ASC")?.use { cursor ->
            while (cursor.moveToNext()) result += CalendarInfo(cursor.getLong(0), cursor.getString(1) ?: "Calendário",
                cursor.getString(2) ?: "No dispositivo", cursor.getInt(3))
        } ?: error("Calendar provider unavailable")
        return result
    }

    private fun readInstances(ids: Set<Long>, begin: Instant, end: Instant): List<Appointment> {
        if (ids.isEmpty()) return emptyList()
        val uri = i.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin.toEpochMilli()); ContentUris.appendId(it, end.toEpochMilli())
        }.build()
        val result = mutableListOf<Appointment>()
        val placeholders = ids.joinToString(",") { "?" }
        context.contentResolver.query(uri,
            arrayOf(i.EVENT_ID, i.CALENDAR_ID, i.TITLE, i.BEGIN, i.END, i.ALL_DAY),
            "${i.CALENDAR_ID} IN ($placeholders) AND ${i.STATUS} != ? AND ${i.SELF_ATTENDEE_STATUS} != ?",
            (ids.map(Long::toString) + CalendarContract.Events.STATUS_CANCELED.toString() +
                CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED.toString()).toTypedArray(), "${i.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) result += Appointment(cursor.getLong(0), cursor.getLong(1),
                cursor.getString(2)?.takeIf { it.isNotBlank() } ?: "Sem título",
                Instant.ofEpochMilli(cursor.getLong(3)), Instant.ofEpochMilli(cursor.getLong(4)), cursor.getInt(5) == 1)
        } ?: error("Calendar instances unavailable")
        return result.distinctBy { Triple(it.eventId, it.begin, it.end) }
    }
}
