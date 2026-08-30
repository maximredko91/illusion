package com.illusion.app.data.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.illusion.app.domain.model.AppIcon

/**
 * Flips which `<activity-alias>` (see AndroidManifest.xml) is enabled - that's what actually
 * changes the icon shown on the home screen, there's no other API for a launcher icon that isn't
 * pinned to system dark/light mode. Only one alias is ever enabled at a time; [apply] disables
 * every other one first. `DONT_KILL_APP` keeps the current process alive - the OS itself decides
 * whether the change needs a restart to show (confirmed on real devices: some launchers pick it up
 * immediately, some show it only after returning to the home screen, and API 33+ sometimes prompts
 * the user to restart the app - all standard platform behavior for this exact mechanism, not
 * something this app can control further).
 */
object IconVariantManager {
    fun apply(context: Context, icon: AppIcon) {
        val packageManager = context.packageManager
        AppIcon.entries.forEach { entry ->
            val state = if (entry == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, "${context.packageName}${entry.aliasName}"),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /** Reads which alias is actually enabled right now, for the Settings picker's initial selection - falls back to [AppIcon.ILLUSION] (the manifest's own default-enabled alias) if somehow none or more than one reads as enabled. */
    fun current(context: Context): AppIcon {
        val packageManager = context.packageManager
        return AppIcon.entries.firstOrNull { entry ->
            val state = packageManager.getComponentEnabledSetting(
                ComponentName(context.packageName, "${context.packageName}${entry.aliasName}")
            )
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && entry == AppIcon.ILLUSION)
        } ?: AppIcon.ILLUSION
    }
}
