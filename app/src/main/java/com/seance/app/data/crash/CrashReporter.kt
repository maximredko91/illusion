package com.seance.app.data.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import com.seance.app.BuildConfig
import java.io.File

/**
 * Local-only crash capture: writes the stack trace of any uncaught exception to a file in the
 * app's private storage, then hands off to the previous default handler (system crash dialog,
 * process death - unchanged). Nothing is ever sent anywhere automatically - this app is
 * offline-first by design (see CLAUDE.md), and a Crashlytics-style always-on remote reporter
 * would silently break that on every launch. [pendingReport] lets the UI notice a crash file
 * exists next launch and offer the user a one-tap share (email/Telegram/whatever they pick) -
 * their explicit action each time, not an automatic upload.
 */
object CrashReporter {
    private const val DIR_NAME = "crash_logs"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(appContext, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val header = buildString {
            appendLine("Seance ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
        }
        File(dir, "crash_${System.currentTimeMillis()}.txt").writeText(header + throwable.stackTraceToString())
    }

    /** The most recent unreported crash, if any - null once [clear] has been called for it. */
    fun pendingReport(context: Context): File? =
        File(context.filesDir, DIR_NAME).listFiles()?.maxByOrNull { it.lastModified() }

    fun clear(file: File) {
        file.delete()
    }

    /** A plain-text share sheet (email, Telegram, notes, ...) prefilled with the crash log - no FileProvider/manifest wiring needed for text this small. */
    fun shareIntent(file: File): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Seance - отчёт о сбое")
            putExtra(Intent.EXTRA_TEXT, file.readText())
        }

    /** Same free-choice share sheet, for the Settings "обратная связь" entry - general feedback/bug/suggestion, not tied to a crash. Prefills version/device context so the tester doesn't have to type it. */
    fun feedbackIntent(): Intent {
        val header = "Seance ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n\n"
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Seance - отзыв")
            putExtra(Intent.EXTRA_TEXT, header)
        }
    }
}
