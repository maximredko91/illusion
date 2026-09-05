package com.illusion.app.data.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.illusion.app.MainActivity
import com.illusion.app.R
import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Deliberately generous, not a "playback ended" detector - some external players (confirmed on
// MX Player) close the HTTP connection entirely after buffering a burst over the fast loopback
// link, then play for a long stretch from their own buffer before opening a new one for the next
// chunk. "No currently-open stream" is common and normal mid-playback, not just at the real end -
// there's no reliable app-side signal for "the external player is actually done with this file"
// short of tracking that specific player's process lifecycle. This exists purely as a long-tail
// safety net against a genuinely abandoned/forgotten stream leaking the foreground service
// forever, not as a fast responder.
private const val IDLE_TIMEOUT_MS = 6 * 60 * 60 * 1000L
private const val NOTIFICATION_ID = 4200
private const val CHANNEL_ID = "external_streaming"

/**
 * Foreground service hosting [LocalStreamingServer] so it (and the process it lives in) keeps
 * running while an external player is actively pulling bytes from it, even if this app itself
 * gets backgrounded - same reasoning that put downloads on a foreground-serviced WorkManager job
 * rather than a plain app-scoped coroutine. See [IDLE_TIMEOUT_MS] for why the auto-shutdown timer
 * is so long.
 */
class StreamingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleWatchdog: Job? = null

    /** How many streaming responses are currently open (constructed but not yet closed) on
     * [LocalStreamingServer]. The idle-shutdown countdown only ever runs while this is 0 -
     * NanoHTTPD dispatches concurrent requests on separate worker threads, so this needs to be
     * genuinely thread-safe, not just single-threaded-looking. */
    private val activeStreams = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        val factory = dataSourceFactory
        if (factory == null) {
            stopSelf()
            return
        }
        ensureServerStarted(factory).apply {
            onStreamOpened = {
                activeStreams.incrementAndGet()
                idleWatchdog?.cancel()
            }
            onStreamClosed = {
                if (activeStreams.decrementAndGet() <= 0) resetIdleWatchdog()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Manual "Остановить" tap from the notification - the idle timer is a 6h long-tail
            // safety net (see IDLE_TIMEOUT_MS), not a "just finished watching" detector, so a
            // user who knows they're actually done has no other way to clear this notification
            // sooner than that.
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        // Don't blindly (re)start the countdown here - a second playback request arriving while
        // the first is still actively streaming would otherwise schedule a shutdown that has
        // nothing to do with that first, still-open connection.
        if (activeStreams.get() <= 0) resetIdleWatchdog()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Fires when the user swipes Illusion away from Recents (the app's task is removed, not just
    // backgrounded) - stop the stream/notification right away instead of waiting out the 6h
    // safety net, accepting that if an external player is still genuinely reading from this proxy
    // at that exact moment, closing the app this way will cut it off.
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        idleWatchdog?.cancel()
        scope.cancel()
        server?.stop()
        server = null
        super.onDestroy()
    }

    private fun resetIdleWatchdog() {
        idleWatchdog?.cancel()
        idleWatchdog = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            stopSelf()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.streaming_notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): android.app.Notification {
        ensureChannel()
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, StreamingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.streaming_notification_channel_name))
            .setContentText(getString(R.string.streaming_notification_text))
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.streaming_notification_stop), stopIntent)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.illusion.app.streaming.STOP"

        /** Set once by IllusionApplication at startup - a plain Service can't take constructor
         * args, and this only ever needs the one process-wide factory the rest of the app already
         * shares for internal playback. */
        @Volatile var dataSourceFactory: SmbDataSourceFactory? = null

        @Volatile private var server: LocalStreamingServer? = null

        private fun ensureServerStarted(factory: SmbDataSourceFactory): LocalStreamingServer {
            server?.let { if (it.isAlive) return it }
            val newServer = LocalStreamingServer(factory)
            newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = newServer
            return newServer
        }

        /** Starts (or reuses) the loopback server + foreground service and returns the URL
         * external players should open instead of a raw smb:// path. Binding the server itself is
         * fast/synchronous - only the foreground-service notification lags slightly behind, which
         * doesn't block the very first bytes being served. */
        fun streamUrl(context: Context, sourceId: Long, path: String, sizeBytes: Long): String {
            val factory = requireNotNull(dataSourceFactory) { "StreamingService.dataSourceFactory not set" }
            val srv = ensureServerStarted(factory)
            ContextCompat.startForegroundService(context, Intent(context, StreamingService::class.java))
            val encodedPath = Uri.encode(path)
            return "http://127.0.0.1:${srv.listeningPort}${LocalStreamingServer.STREAM_PATH}?source=$sourceId&path=$encodedPath&size=$sizeBytes"
        }
    }
}
