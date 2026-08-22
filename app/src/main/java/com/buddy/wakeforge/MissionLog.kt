package com.buddy.wakeforge

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per successfully-completed mission. This powers the streak/XP
 * gamification in the main screen — logging *successes only* (not attempts)
 * keeps this simple and keeps the data minimal: no video, no images, no
 * biometric data, just a timestamp and a point value. Small data footprint
 * is itself a security/privacy choice, not just a performance one.
 */
@Entity(tableName = "mission_logs")
data class MissionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val alarmEventId: Int,
    val completedAtMillis: Long,
    val xpEarned: Int
)
