package com.buddy.wakeforge

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * Applies Material You dynamic color (Android 12+) across all activities —
 * the app's accent colors adapt to the user's wallpaper/system theme instead
 * of looking like every other app's fixed brand color. Falls back to the
 * static palette in themes.xml on older Android versions automatically.
 */
class WakeforgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
