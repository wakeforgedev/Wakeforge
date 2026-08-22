package com.buddy.riseup

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.sqrt

/**
 * Counts distinct "shake" gestures using accelerometer magnitude with a
 * cooldown so a single continuous shake doesn't get over-counted. This is
 * the same basic technique Alarmy's "Shake" mission and most fitness-tap
 * step counters use; it needs no extra permissions on Android.
 */
class ShakeDetector(private val onShake: (count: Int) -> Unit) : SensorEventListener {

    private var shakeCount = 0
    private var lastShakeTime = 0L
    private val shakeThreshold = 13f // m/s^2 above gravity, tuned to be a deliberate shake, not a bump
    private val cooldownMs = 400L

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val gX = event.values[0]
        val gY = event.values[1]
        val gZ = event.values[2]
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat() - SensorManager_GRAVITY

        val now = System.currentTimeMillis()
        if (gForce > shakeThreshold && now - lastShakeTime > cooldownMs) {
            lastShakeTime = now
            shakeCount++
            onShake(shakeCount)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val SensorManager_GRAVITY = 9.81f
    }
}
