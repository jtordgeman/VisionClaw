package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.meta.wearable.dat.externalsampleapps.cameraaccess.MainActivity
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.StreamWidgetProvider
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.GlassesButtonChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the camera streaming alive when the screen is locked
 * or the app is in the background.
 *
 * - Displays a persistent notification while streaming
 * - Acquires a partial wake lock to prevent CPU sleep
 * - Allows the streaming to continue when the app is backgrounded
 */
class StreamingService : Service() {

  companion object {
    private const val TAG = "StreamingService"
    private const val CHANNEL_ID = "streaming_channel"
    private const val CHANNEL_NAME = "Camera Streaming"
    private const val NOTIFICATION_ID = 1001
    private const val WAKELOCK_TAG = "VisionClaw::StreamingWakeLock"
    fun start(context: Context) {
      val intent =
          Intent(context, StreamingService::class.java).apply { `package` = context.packageName }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      val intent =
          Intent(context, StreamingService::class.java).apply { `package` = context.packageName }
      context.stopService(intent)
    }
  }

  private var wakeLock: PowerManager.WakeLock? = null
  private var mediaSession: MediaSession? = null
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "Service created")
    createNotificationChannel()
    setupMediaSession()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "Service started")

    val notification = createNotification()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
              ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    acquireWakeLock()

    StreamWidgetProvider.notifyStreamingStarted(this)

    return START_STICKY
  }

  override fun onDestroy() {
    StreamWidgetProvider.notifyStreamingStopped(this)
    Log.d(TAG, "Service destroyed")
    releaseWakeLock()
    mediaSession?.release()
    mediaSession = null
    super.onDestroy()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
          NotificationChannel(
                  CHANNEL_ID,
                  CHANNEL_NAME,
                  NotificationManager.IMPORTANCE_LOW,
              )
              .apply {
                description = "Notifications for active camera streaming"
                setShowBadge(false)
              }

      val notificationManager = getSystemService(NotificationManager::class.java)
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(): Notification {
    val pendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
              flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Camera Streaming")
        .setContentText("Streaming from your glasses...")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()
  }

  private fun acquireWakeLock() {
    if (wakeLock == null) {
      val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
      wakeLock =
          powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire(35 * 60 * 1000L) // 35 minutes — outlasts the 30-minute session timeout
          }
      Log.d(TAG, "WakeLock acquired")
    }
  }

  private fun releaseWakeLock() {
    wakeLock?.let {
      if (it.isHeld) {
        it.release()
        Log.d(TAG, "WakeLock released")
      }
    }
    wakeLock = null
  }

  private fun setupMediaSession() {
    val session = MediaSession(this, TAG)
    var buttonDownAt = 0L

    session.setCallback(object : MediaSession.Callback() {
      override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
          @Suppress("DEPRECATION")
          mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        } ?: return false

        val isTargetKey = keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        if (!isTargetKey) return false

        Log.d(TAG, "Button key event: action=${keyEvent.action} keyCode=${keyEvent.keyCode}")

        when (keyEvent.action) {
          KeyEvent.ACTION_DOWN -> if (keyEvent.repeatCount == 0) buttonDownAt = System.currentTimeMillis()
          KeyEvent.ACTION_UP -> {
            if (buttonDownAt == 0L) return true // no paired ACTION_DOWN — ignore
            val duration = System.currentTimeMillis() - buttonDownAt
            buttonDownAt = 0L
            val event = if (duration < 800) GlassesButtonChannel.Event.SHORT_PRESS
                        else GlassesButtonChannel.Event.LONG_PRESS
            Log.d(TAG, "Button event: $event (held ${duration}ms)")
            serviceScope.launch { GlassesButtonChannel.events.emit(event) }
          }
        }
        return true
      }
    })

    session.isActive = true
    mediaSession = session
    Log.d(TAG, "MediaSession created and active")
  }
}
