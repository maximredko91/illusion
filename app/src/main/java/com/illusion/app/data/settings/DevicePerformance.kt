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
/** The raw signals behind [DevicePerformance.classify], for the Settings note showing the user which class their own device landed in - not just the final yes/no. */
data class DeviceClass(val isLowEnd: Boolean, val coreCount: Int, val isLowRamDevice: Boolean)

object DevicePerformance {
    private const val LOW_CORE_COUNT_THRESHOLD = 4

    fun classify(context: Context): DeviceClass {
        val activityManager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = activityManager?.isLowRamDevice == true
        val coreCount = Runtime.getRuntime().availableProcessors()
        return DeviceClass(isLowEnd = lowRam || coreCount <= LOW_CORE_COUNT_THRESHOLD, coreCount = coreCount, isLowRamDevice = lowRam)
    }

    fun isLowEndDevice(context: Context): Boolean = classify(context).isLowEnd
}
