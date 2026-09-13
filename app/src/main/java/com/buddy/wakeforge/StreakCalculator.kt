package com.buddy.wakeforge

import java.util.Calendar

/**
 * Turns raw completion logs into the two numbers that actually drive
 * retention for this audience: a day streak (Duolingo-style — "don't break
 * the chain") and a total XP count. Pure function, no framework
 * dependencies, easy to unit test.
 */
object StreakCalculator {

    fun currentStreakDays(logs: List<MissionLog>): Int {
        val completedLogs = logs.filter { it.completed && it.completedAtMillis != null }
        if (completedLogs.isEmpty()) return 0
        val completedDays = completedLogs.map { dayKey(it.completedAtMillis!!) }.toSortedSet(compareByDescending { it })
        val today = dayKey(System.currentTimeMillis())

        var streak = 0
        var cursor = today
        // Streak continues today or as of yesterday (so it doesn't reset the instant
        // midnight passes before today's alarm has even fired yet).
        if (!completedDays.contains(cursor)) {
            cursor = addDays(cursor, -1)
        }
        while (completedDays.contains(cursor)) {
            streak++
            cursor = addDays(cursor, -1)
        }
        return streak
    }

    fun totalXp(logs: List<MissionLog>): Int = logs.filter { it.completed }.sumOf { it.xpEarned }

    fun xpForDifficulty(difficulty: Int): Int = 10 + difficulty * 5

    /** Encodes a timestamp as YYYYMMDD in the device's local calendar, for day-bucketing. */
    private fun dayKey(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun addDays(dayKey: Int, delta: Int): Int {
        val year = dayKey / 10000
        val month = (dayKey / 100) % 100
        val day = dayKey % 100
        val cal = Calendar.getInstance().apply {
            set(year, month - 1, day, 12, 0, 0)
        }
        cal.add(Calendar.DAY_OF_MONTH, delta)
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
    }
}
