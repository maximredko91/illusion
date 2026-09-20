package com.illusion.app.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * Installs a downloaded update .apk.
 *
 * Two paths, in order of preference:
 *
 * 1. [installSilently] - a real [PackageInstaller] session. On Android 12+ an app may update
 *    *itself* with no confirmation dialog at all, provided it holds
 *    `UPDATE_PACKAGES_WITHOUT_USER_ACTION` and was itself the installer of the version being
 *    replaced. That last part is why the first update after this path ships still shows the
 *    system dialog: the currently installed build was put there by a browser/adb, not by this
 *    app. Confirm it once and every later update applies in the background - the same mechanism
 *    app stores outside Play (RuStore et al.) use for their own silent updates. Android 14's
 *    `setRequestUpdateOwnership` is deliberately NOT used: it needs the privileged
 *    `ENFORCE_UPDATE_OWNERSHIP` permission, which an ordinary app cannot hold.
 * 2. [installIntent] - the classic ACTION_VIEW hand-off to the system installer, kept as the
 *    fallback for anything the session path can't do (a device that refuses the session, or a
 *    confirmation the OS insists on anyway).
 *
 * Neither path can skip the one-time "install unknown apps" gate ([canInstallPackages]) - that's
 * Android's security model for non-Play installs, not a gap here. Vendor scanners (Play Protect,
 * MIUI's own) are likewise outside app control; the silent path simply doesn't open the installer
 * UI they hook into.
 */
object UpdateInstaller {
    /** False the very first time this app tries to install an update - see [installPermissionSettingsIntent]. */
    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Deep-links straight to this app's "install unknown apps" toggle rather than the generic Settings root. */
    fun installPermissionSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, ("package:${context.packageName}").toUri())

    /**
     * Commits [apkFile] as a self-update session. Returns false if the session couldn't even be
     * created or written (no space, denied, an OS that refuses) - the caller then falls back to
     * [installIntent]. A true return only means the session was handed to the OS: what happens
     * next arrives on [installOutcomeFlow], or, on success, as this process being replaced.
     */
    fun installSilently(context: Context, apkFile: File): Boolean {
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(WRITE_NAME, 0, apkFile.length()).use { output ->
                    apkFile.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                session.commit(statusReceiver(context, sessionId).intentSender)
            }
            true
        } catch (e: Exception) {
            // A failed session leaves a staged copy of the apk behind - drop it rather than leak
            // the space until the OS gets around to it.
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            android.util.Log.w("UpdateInstaller", "Silent install session failed", e)
            false
        }
    }

    /** System-installer hand-off - the fallback path, see this object's own KDoc. */
    fun installIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    internal fun report(outcome: InstallOutcome) {
        installOutcomes.tryEmit(outcome)
    }

    private fun statusReceiver(context: Context, sessionId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
        // Mutable on purpose: the OS fills in EXTRA_STATUS/EXTRA_INTENT on this very Intent.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    private const val WRITE_NAME = "illusion_update"
}
