package com.meta.wearable.dat.externalsampleapps.cameraaccess

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingService

class StreamWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val PREFS_NAME = "StreamWidgetPrefs"
        private const val KEY_START_TIME = "pref_stream_start_time"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, StreamWidgetProvider::class.java)
            )
            val chronoBase = resolveChronoBase(context)
            for (appWidgetId in ids) {
                updateWidget(context, manager, appWidgetId, chronoBase)
            }
        }

        /**
         * Converts the stored wall-clock start time to an elapsedRealtime-based Chronometer base.
         * Returns 0 if idle, or if the stored value is stale (from a previous boot).
         */
        private fun resolveChronoBase(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wallClockStart = prefs.getLong(KEY_START_TIME, 0L)
            if (wallClockStart == 0L) return 0L
            val millisSinceStart = System.currentTimeMillis() - wallClockStart
            // If the session started before this boot, the stored value is stale — clear it
            if (millisSinceStart < 0 || millisSinceStart > SystemClock.elapsedRealtime()) {
                prefs.edit().putLong(KEY_START_TIME, 0L).apply()
                return 0L
            }
            return SystemClock.elapsedRealtime() - millisSinceStart
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            startTime: Long,
        ) {
            val tapIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_QUICK_START_STREAMING
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val views = if (startTime > 0L) {
                RemoteViews(context.packageName, R.layout.widget_stream_live).apply {
                    setChronometer(R.id.elapsed_time, startTime, null, true)
                    setOnClickPendingIntent(R.id.widget_root, tapIntent)
                }
            } else {
                RemoteViews(context.packageName, R.layout.widget_stream).apply {
                    setOnClickPendingIntent(R.id.widget_root, tapIntent)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            StreamingService.ACTION_STREAMING_STARTED -> {
                val wallClock = intent.getLongExtra(
                    StreamingService.EXTRA_START_TIME,
                    System.currentTimeMillis()
                )
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_START_TIME, wallClock).apply()
                updateAllWidgets(context)
            }
            StreamingService.ACTION_STREAMING_STOPPED -> {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_START_TIME, 0L).apply()
                updateAllWidgets(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val chronoBase = resolveChronoBase(context)
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, chronoBase)
        }
    }
}
