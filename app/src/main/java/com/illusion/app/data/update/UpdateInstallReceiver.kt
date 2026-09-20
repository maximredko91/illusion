package com.illusion.app.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Outcome of a [UpdateInstaller.installSilently] session, as far as the app can observe it. */
sealed interface InstallOutcome {
    /** The OS decided this install needs confirmation after all - [intent] opens its own dialog. */
    data class NeedsUserAction(val intent: Intent) : InstallOutcome

    /** Session failed; [message] is the OS's own reason, usually worth showing verbatim. */
    data class Failed(val message: String?) : InstallOutcome
}

/**
 * Receives the result of the install session started by [UpdateInstaller.installSilently].
 *
 * Success is deliberately not reported: a successful self-update replaces this very process, so
 * nothing would be left to observe it. Only the two cases the app can still act on are forwarded -
 * "the OS wants the user to confirm" (the fallback path, which is what happens until this app
 * becomes the installer of record for itself) and an outright failure.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT)
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Started straight from here rather than handed to the UI: a receiver can
                    // legitimately run while no Activity of this app is on screen (the install is
                    // committed in the background), and the confirmation dialog is exactly what the
                    // user is waiting for at that moment.
                    runCatching { context.startActivity(confirmation) }
                        .onFailure { UpdateInstaller.report(InstallOutcome.NeedsUserAction(confirmation)) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> UpdateInstaller.report(
                InstallOutcome.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
            )
        }
    }
}

/** Tiny shim so the deprecated non-typed getParcelableExtra isn't called on API 33+. */
private object IntentCompat {
    fun getParcelableExtra(intent: Intent, name: String): Intent? =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(name, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(name)
        }
}

/** Install outcomes, published by [UpdateInstallReceiver] and collected by the update UI. */
internal val installOutcomes = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 4)

val installOutcomeFlow: SharedFlow<InstallOutcome> get() = installOutcomes
