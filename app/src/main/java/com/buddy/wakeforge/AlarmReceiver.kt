package com.buddy.wakeforge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Fires when the AlarmManager alarm goes off. It does NOT show UI directly —
 * that's unreliable from a plain BroadcastReceiver on modern Android.
 * Instead it starts a foreground Service, which is allowed to post a
 * full-screen-intent notification even while the device is locked or idle.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlarmRingService::class.java).apply {
            putExtras(intent)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
