package com.illusion.app.data.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class InstalledPlayerApp(val packageName: String, val label: String)

/**
 * Lists apps on the device that can handle a video ACTION_VIEW Intent (VLC, MX Player, ...) -
 * used to let the user pick a specific external player in Settings instead of relying on
 * Android's own disambiguation dialog every time. Requires the `<queries>` block in
 * AndroidManifest.xml (package visibility, Android 11+) or this returns an empty list even with
 * matching apps installed.
 */
object InstalledPlayerApps {
    fun list(context: Context): List<InstalledPlayerApp> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW).setType("video/*")
        return pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                InstalledPlayerApp(packageName, resolveInfo.loadLabel(pm).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
