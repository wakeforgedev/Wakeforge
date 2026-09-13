package com.buddy.wakeforge

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per alarm that actually rang. A row is created the moment
 * [AlarmRingService] starts ringing (completed = false, completedAtMillis =
 * null), then updated in place once the mission is solved (completed = true,
 * completedAtMillis set, xpEarned set). This is what makes the History
 * screen's "missed" days possible — earlier this only logged *successes*,
 * so there was no way to tell "no alarm that day" apart from "alarm rang,
 * never finished." Still a small, privacy-minimal footprint: a timestamp,
 * a title snapshot, and a point value — no video, no images, no biometrics.
 */
@Entity(tableName = "mission_logs")
data class MissionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val alarmEventId: Int,
    /** Snapshot of the alarm's title at ring time, so History still reads fine even if the alarm was later renamed or deleted. */
    val alarmTitle: String,
    /** When this alarm actually started ringing. */
    val scheduledAtMillis: Long,
    /** Null until the mission is solved. */
    val completedAtMillis: Long? = null,
    val completed: Boolean = false,
    val xpEarned: Int = 0
)
