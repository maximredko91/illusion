package com.illusion.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Launches the system package installer on a downloaded update .apk. This is as close to a
 * one-tap update as sideloading (outside Play Store) can get - Android still shows its own
 * "install unknown apps" gate the first time for this app as a source (Settings > Apps > Special
 * access > Install unknown apps), and (separately) the OS/Play Protect may still scan and warn on
 * the .apk itself. Neither can be suppressed from app code - that's Android's actual security
 * model for non-Play installs working as intended, not a gap in this implementation. What this
 * *does* remove is the old flow of "open a browser, find the release page, tap the asset link,
 * switch back" - the download and the install-intent launch both happen without leaving the app.
 */
object UpdateInstaller {
    /** False the very first time this app tries to install an update - see [installPermissionSettingsIntent]. */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Deep-links straight to this app's "install unknown apps" toggle rather than the generic Settings root. */
    fun installPermissionSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
