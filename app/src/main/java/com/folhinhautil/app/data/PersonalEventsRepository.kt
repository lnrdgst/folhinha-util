package com.folhinhautil.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.folhinhautil.app.domain.PersonalEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Context.personalEventsStore by preferencesDataStore(name = "personal_events")
private val EventsJson = stringPreferencesKey("events_json_v1")

class PersonalEventsRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.personalEventsStore)
    val events: Flow<List<PersonalEvent>> = store.data.map { decode(it[EventsJson]) }

    suspend fun save(event: PersonalEvent) {
        val clean = event.copy(title = event.title.trim())
        store.edit { prefs ->
            val updated = decode(prefs[EventsJson]).filterNot { it.id == clean.id } + clean
            prefs[EventsJson] = encode(updated)
        }
    }
    suspend fun delete(id: String) {
        store.edit { prefs -> prefs[EventsJson] = encode(decode(prefs[EventsJson]).filterNot { it.id == id }) }
    }

    private fun encode(events: List<PersonalEvent>): String {
        val array = JSONArray()
        events.forEach { event -> array.put(JSONObject().put("id", event.id).put("title", event.title)
            .put("date", event.date.toString()).put("annual", event.annual)) }
        return JSONObject().put("version", 1).put("events", array).toString()
    }
    private fun decode(json: String?): List<PersonalEvent> {
        if (json == null) return emptyList()
        val root = JSONObject(json)
        check(root.getInt("version") == 1) { "Formato de armazenamento não suportado" }
        val array = root.getJSONArray("events")
        return List(array.length()) { index ->
            val value = array.getJSONObject(index)
            PersonalEvent(value.getString("id"), value.getString("title"), LocalDate.parse(value.getString("date")), value.getBoolean("annual"))
        }.also { values -> check(values.map { it.id }.distinct().size == values.size) { "Armazenamento inválido" } }
    }
}
