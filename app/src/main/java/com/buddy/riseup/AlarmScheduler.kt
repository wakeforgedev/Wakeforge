package com.buddy.riseup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Thin wrapper around AlarmManager using setAlarmClock(), which is the most
 * reliable exact-alarm API on Android — it's exempt from Doze/battery
 * restrictions the same way the stock Clock app's alarms are, which is why
 * apps like Alarmy can fire reliably even when the phone has been idle.
 *
 * On Android 12+ (API 31+) the user must explicitly grant the
 * "Alarms & reminders" permission — MainActivity checks/requests this
 * before allowing an alarm to be saved.
 */
object AlarmScheduler {

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_CATEGORY = "extra_category"
    const val EXTRA_MISSION = "extra_mission"
    const val EXTRA_DIFFICULTY = "extra_difficulty"
    const val EXTRA_CAMERA_DURATION_MINUTES = "extra_camera_duration_minutes"

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            val am = context.getSystemService(AlarmManager::class.java)
            am.canScheduleExactAlarms()
        } else true
    }

    fun schedule(context: Context, event: AlarmEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, event)

        val showIntent = PendingIntent.getActivity(
            context, event.id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val info = AlarmManager.AlarmClockInfo(event.timeInMillis, showIntent)
        alarmManager.setAlarmClock(info, pendingIntent)
    }

    fun cancel(context: Context, event: AlarmEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, event))
    }

    private fun buildPendingIntent(context: Context, event: AlarmEvent): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, event.id)
            putExtra(EXTRA_TITLE, event.title)
            putExtra(EXTRA_CATEGORY, event.category.name)
            putExtra(EXTRA_MISSION, event.missionType.name)
            putExtra(EXTRA_DIFFICULTY, event.difficulty)
            putExtra(EXTRA_CAMERA_DURATION_MINUTES, event.cameraDurationMinutes)
        }
        return PendingIntent.getBroadcast(
            context, event.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
