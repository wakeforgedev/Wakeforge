package com.buddy.riseup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager alarms are wiped when the device reboots. This receiver
 * reschedules every enabled alarm so users don't lose their alarms after
 * a restart (a common complaint with naive alarm-app implementations).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(appContext).alarmDao()
            dao.getAllEnabled().forEach { event ->
                if (event.timeInMillis > System.currentTimeMillis()) {
                    AlarmScheduler.schedule(appContext, event)
                }
            }
        }
    }
}
