package com.folhinhautil.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.folhinhautil.app.data.PersonalEventsRepository
import com.folhinhautil.app.domain.PersonalEvent
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate

/** Full production form with a cache-only store and no launcher widget updates. Debug APK only. */
class PersonalDatePreviewActivity : ComponentActivity() {
    private val fixtureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var fixtureRepository: PersonalEventsRepository
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val file = File(cacheDir,"date-picker-fixture.preferences_pb")
        if (intent.getBooleanExtra("reset",true)) file.delete()
        fixtureRepository = PersonalEventsRepository(PreferenceDataStoreFactory.create(scope = fixtureScope, produceFile = { file }))
        if (intent.getBooleanExtra("reset",true)) runBlocking {
            fixtureRepository.save(PersonalEvent("date-fixture","Evento de teste",LocalDate.parse(intent.getStringExtra("date") ?: "2026-12-27"),true))
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB6E4BE), onPrimary = Color(0xFF101513),
                background = Color(0xFF101513), surface = Color(0xFF1B231F))) {
                Surface(Modifier.fillMaxSize()) {
                    PersonalEventsScreen(back = { finish() }, repositoryOverride = fixtureRepository, onSaved = {})
                }
            }
        }
    }
    override fun onDestroy() {
        runBlocking { fixtureScope.coroutineContext[Job]!!.cancelAndJoin() }
        super.onDestroy()
    }
}
