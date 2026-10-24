package com.folhinhautil.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.folhinhautil.app.data.*
import com.folhinhautil.app.domain.*
import com.folhinhautil.app.widget.FolhinhaWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.*

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val snapshot: CalendarSnapshot = CalendarSnapshot(),
    val loading: Boolean = true,
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CalendarRepository(application)
    private val mutableState = MutableStateFlow(CalendarUiState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh(debounce = true) }
    }

    fun navigate(delta: Long) {
        val next = mutableState.value.month.plusMonths(delta)
        if (next.year !in FirstYear..LastYear) return
        mutableState.value = mutableState.value.copy(month = next, selectedDate = next.atDay(1))
        refresh()
    }
    fun today() {
        val now = LocalDate.now()
        mutableState.value = mutableState.value.copy(month = YearMonth.from(now), selectedDate = now, today = now)
        refresh()
    }
    fun selectDay(date: LocalDate) { mutableState.value = mutableState.value.copy(selectedDate = date) }
    fun selectCalendars(ids: Set<Long>) { repository.select(ids); refresh() }

    fun refresh(debounce: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (debounce) delay(500)
            val granted = repository.hasPermission()
            if (granted && !observing) {
                try {
                    getApplication<Application>().contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
                    observing = true
                } catch (_: SecurityException) { /* Permission may have changed between checks. */ }
            } else if (!granted && observing) stopObserving()
            mutableState.value = mutableState.value.copy(loading = true, today = LocalDate.now(),
                snapshot = if (granted) mutableState.value.snapshot else CalendarSnapshot())
            val snapshot = repository.load(mutableState.value.month)
            mutableState.value = mutableState.value.copy(snapshot = snapshot, loading = false)
            // Each widget reads current data directly. No event data is stored in preferences or logs.
            try { FolhinhaWidget().updateAll(getApplication()) } catch (_: RuntimeException) { }
        }
    }
    private fun stopObserving() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        observing = false
    }
    override fun onCleared() { if (observing) stopObserving() }
}
