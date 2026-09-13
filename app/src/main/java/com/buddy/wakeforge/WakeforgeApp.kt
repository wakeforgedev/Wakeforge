package com.buddy.wakeforge

import android.app.Application

/**
 * Deliberately does NOT call DynamicColors.applyToActivitiesIfAvailable().
 *
 * That used to be here — Material You dynamic color, which on Android 12+
 * overrides every color in themes.xml/colors.xml with colors extracted from
 * the user's wallpaper. It sounded nice in theory ("adapts to you instead of
 * looking like every other app") but in practice it silently discarded the
 * whole designed palette on real devices, which is the root cause behind
 * "the UI color isn't fixed" complaints even after multiple palette passes —
 * every fix was being overridden by the phone, not failing on its own.
 * Wakeforge now ships one deliberate, fixed brand palette (see themes.xml /
 * colors.xml) that looks the same on every device.
 */
class WakeforgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
