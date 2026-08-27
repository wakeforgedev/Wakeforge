package com.buddy.wakeforge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * Foreground service that owns the actual "ringing" (sound + vibration) for
 * as long as the mission is unsolved, and is responsible for posting the
 * full-screen-intent notification that launches AlarmActivity over the
 * lock screen. AlarmActivity calls [stopRinging] once the mission is
 * completed successfully.
 */
class AlarmRingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val CHANNEL_ID = "wakeforge_alarm_channel"
        const val NOTIFICATION_ID = 1001
        var isRinging = false
            private set

        // Which alarm's mission is currently on screen / ringing, if any.
        // Lets us tell "stop because THIS alarm was handled/deleted" apart
        // from "stop some other alarm that happens to be ringing right now."
        private var currentAlarmId: Int? = null

        /**
         * Stops the ringing service.
         *
         * [alarmId] is optional: pass it when the caller only wants to stop a
         * *specific* alarm (e.g. deleting alarm #2 from the list should never
         * silence alarm #5 if #5 happens to be the one actually ringing right
         * now). Leave it null when the caller is already inside that alarm's
         * own mission screen and unconditionally means "stop me."
         */
        fun stopRinging(context: Context, alarmId: Int? = null) {
            if (alarmId != null && currentAlarmId != null && alarmId != currentAlarmId) return
            context.stopService(Intent(context, AlarmRingService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0) ?: 0
        val title = intent?.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Alarm"
        val ringtoneUri = intent?.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_URI)
        currentAlarmId = alarmId

        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            // Explicit "this." matters here — this lambda's enclosing function
            // (onStartCommand) also has a parameter named "flags", and without
            // qualifying it, Kotlin resolves the assignment to that read-only
            // parameter instead of this Intent's own settable flags property.
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtras(intent?.extras ?: android.os.Bundle())
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, alarmId, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Tap to complete today's mission")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        startRingingAndVibrating(ringtoneUri)
        return START_STICKY
    }

    private fun startRingingAndVibrating(ringtoneUriString: String?) {
        isRinging = true
        val defaultUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        // Per-alarm sound if one was picked for this specific alarm, otherwise
        // the device's default alarm sound.
        val customUri = ringtoneUriString?.let { android.net.Uri.parse(it) }

        if (!tryPlay(customUri ?: defaultUri) && customUri != null) {
            // The saved per-alarm sound may have been uninstalled/removed
            // (e.g. it pointed at another app's ringtone file) — don't leave
            // the user with silence, fall back to the device default instead.
            tryPlay(defaultUri)
        }

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    /** Returns true if playback actually started. Releases and returns false on any failure. */
    private fun tryPlay(uri: android.net.Uri?): Boolean {
        if (uri == null) return false
        return try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            // Fall back silently to vibration-only if no alarm sound is available at all.
            mediaPlayer = null
            false
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Wake-up mission alarms"
                    setBypassDnd(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        isRinging = false
        currentAlarmId = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
