package com.buddy.riseup

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Categories drive the *default* mission suggestion in the UI, and later on
 * (Phase 2+) will drive age-tier presets too. They are stored as plain
 * strings so we can add more categories without a DB migration headache.
 */
enum class Category { STUDENT, GYM, GENERAL }

/**
 * A mission is the "unique stopping style."
 *  - MATH: cognitive challenge, works for any category, no sensors needed.
 *  - SHAKE: motion-sensor challenge (accelerometer), no permission needed.
 *  - CAMERA_MOTION: opens the camera for up to [AlarmEvent.cameraDurationMinutes]
 *    and watches for real movement (frame-to-frame motion) as a stand-in for
 *    "prove you're up and active" — a lighter-weight version of what Early's
 *    push-up-counting does, without needing an on-device pose-estimation
 *    model. If nothing is confirmed before time runs out, it hands off to a
 *    Math mission rather than leaving the user stuck with a dead camera —
 *    see AlarmActivity's camera section for the full flow.
 */
enum class MissionType { MATH, SHAKE, CAMERA_MOTION }

@Entity(tableName = "alarm_events")
data class AlarmEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val timeInMillis: Long,
    val category: Category,
    val missionType: MissionType,
    /** 0 = easy, 1 = medium, 2 = hard */
    val difficulty: Int,
    val isEnabled: Boolean = true,
    /**
     * Only meaningful when missionType == CAMERA_MOTION: how long the camera
     * is allowed to stay open before it gives up and falls back to Math.
     * User-configurable in the add-alarm dialog (1 / 5 / 10 / 15 minutes).
     */
    val cameraDurationMinutes: Int = 10
)
