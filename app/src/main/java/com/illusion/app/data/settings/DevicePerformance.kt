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
data class DeviceClass(val isLowEnd: Boolean, val coreCount: Int, val totalRamMb: Long, val isLowRamDevice: Boolean)

object DevicePerformance {
    // Core count alone turned out to be a weak/misleading signal - plenty of genuinely weak
    // budget phones ship 8 cheap cores, and plenty of capable ones ship 4-6 fast ones (per
    // feedback). RAM total is a far better cheap proxy for "can this render smoothly" than core
    // count - there's no public, permission-free API for the actual CPU model/tier, so total RAM
    // plus the OS's own isLowRamDevice flag (which factors in more than raw RAM - see its own
    // platform docs) are what this settles for instead.
    private const val LOW_RAM_MB_THRESHOLD = 3072L

    fun classify(context: Context): DeviceClass {
        val activityManager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRamFlag = activityManager?.isLowRamDevice == true
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val coreCount = Runtime.getRuntime().availableProcessors()
        val isLowEnd = lowRamFlag || (totalRamMb in 1..LOW_RAM_MB_THRESHOLD)
        return DeviceClass(isLowEnd = isLowEnd, coreCount = coreCount, totalRamMb = totalRamMb, isLowRamDevice = lowRamFlag)
    }

    fun isLowEndDevice(context: Context): Boolean = classify(context).isLowEnd
}
