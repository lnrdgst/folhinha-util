package com.folhinhautil.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Inexact local midnight plus durable periodic recovery; neither needs exact-alarm permission. */
internal object WidgetRefresh {
    private const val WorkName = "widget-clock-recovery"
    const val Action = "com.folhinhautil.app.WIDGET_MIDNIGHT"
    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ClockChangeReceiver::class.java).setAction(Action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build())
        val midnight = ZonedDateTime.now().toLocalDate().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, midnight.toInstant().toEpochMilli(), alarmIntent(context))
    }
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WorkName)
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            if (androidx.glance.appwidget.GlanceAppWidgetManager(applicationContext)
                    .getGlanceIds(FolhinhaWidget::class.java).isEmpty()) {
                WidgetRefresh.cancel(applicationContext)
                return Result.success()
            }
            refreshWidgetsForClock(applicationContext)
            WidgetRefresh.schedule(applicationContext)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            Result.retry()
        }
    }
}
