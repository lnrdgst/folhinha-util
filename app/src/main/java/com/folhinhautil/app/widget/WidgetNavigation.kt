package com.folhinhautil.app.widget

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.folhinhautil.app.domain.*
import java.time.LocalDate

internal val ViewedMonth = stringPreferencesKey("viewed_month")
internal val ClockRevision = longPreferencesKey("clock_revision")
internal val MonthDelta = ActionParameters.Key<Int>("month_delta")

class NavigateMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        changeWidgetMonth(context, glanceId, parameters[MonthDelta] ?: 0)
        FolhinhaWidget().update(context, glanceId)
    }
}

internal suspend fun changeWidgetMonth(context: Context, id: GlanceId, delta: Int, today: LocalDate = LocalDate.now()) {
    updateAppWidgetState(context, id) { prefs ->
        prefs[ViewedMonth] = (if (delta == 0) widgetMonth(null, today)
            else moveWidgetMonth(widgetMonth(prefs[ViewedMonth], today), delta)).toString()
    }
}

internal suspend fun refreshWidgetsForClock(context: Context) {
    GlanceAppWidgetManager(context).getGlanceIds(FolhinhaWidget::class.java).forEach { id ->
        updateAppWidgetState(context, id) { it[ClockRevision] = System.currentTimeMillis() }
    }
    FolhinhaWidget().updateAll(context)
}
