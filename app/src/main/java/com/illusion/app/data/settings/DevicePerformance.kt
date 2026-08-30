package com.illusion.app.data.settings

import android.app.ActivityManager
import android.content.Context

/**
 * One-shot device-capability guess for [com.illusion.app.domain.model.PerformanceMode.AUTO] - not
 * a live benchmark, just cheap enough to check on every app launch. [ActivityManager.isLowRamDevice]
 * is Android's own OEM-set "treat this as memory/CPU constrained" flag; a low CPU core count alone
 * also counts, since it catches budget devices with acceptable RAM but a weak CPU that the RAM flag
 * misses.
 */
object DevicePerformance {
    private const val LOW_CORE_COUNT_THRESHOLD = 4

    fun isLowEndDevice(context: Context): Boolean {
        val activityManager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = activityManager?.isLowRamDevice == true
        val fewCores = Runtime.getRuntime().availableProcessors() <= LOW_CORE_COUNT_THRESHOLD
        return lowRam || fewCores
    }
}
