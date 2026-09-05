@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.illusion.app.data.player

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.illusion.app.R

/**
 * Exists purely so the OS shows a real media notification (shade + lock screen, play/pause/seek)
 * while something is playing - PlayerViewModel still owns/creates the actual ExoPlayer exactly as
 * before (including swapping instances for reloadPlayer()'s sharpen-pipeline reset); this service
 * never creates a player of its own, it only ever wraps whatever player PlayerViewModel hands it
 * via [attachPlayer]. Bound (not started as a foreground service directly) from PlayerViewModel -
 * MediaSessionService promotes itself to foreground internally once the attached player is
 * actually playing, so there's no risk of the "didn't call startForeground in time" crash that
 * calling startForegroundService() up front on a still-loading stream would risk.
 *
 * Not a background-playback service - this app is video-first, playback with no screen isn't a
 * supported use case, so [detachPlayer] (called from PlayerViewModel.onCleared) releases the
 * session immediately rather than keeping it alive with no UI.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        // Without this, Media3's own default notification builder falls back to the app's
        // manifest @mipmap/ic_launcher for the status-bar (small) icon - an ADAPTIVE icon (colored
        // layers), which the OS can't render as a status-bar glyph, so the tray icon showed up
        // blank/missing while the notification's own expanded content in the shade rendered fine
        // regardless (confirmed on-device: exactly that split symptom). See ic_stat_play's own KDoc.
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_stat_play) })
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    fun attachPlayer(player: Player) {
        mediaSession?.let { removeSession(it); it.release() }
        // addSession() is what actually registers the session with MediaSessionService's own
        // internal MediaNotificationManager - onGetSession() alone only resolves *incoming*
        // controller-connection requests, it does NOT make the service track the session for its
        // own notification/foreground-promotion bookkeeping. Without this call the session is
        // fully functional (playback, system Bluetooth/AVRCP integration all worked) but no
        // notification ever gets built, confirmed via dumpsys (startForegroundCount stayed 0
        // through a full PLAYING session).
        mediaSession = MediaSession.Builder(this, player).build().also { addSession(it) }
    }

    fun detachPlayer() {
        mediaSession?.let { removeSession(it); it.release() }
        mediaSession = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // A plain local bind from PlayerViewModel carries no action; the system's own media-session
    // discovery (Android Auto, the notification's internal controller, etc.) binds with
    // MediaSessionService.SERVICE_INTERFACE set as the action and needs the superclass's binder.
    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == null) binder else super.onBind(intent)

    override fun onDestroy() {
        mediaSession?.let { removeSession(it); it.release() }
        mediaSession = null
        super.onDestroy()
    }
}
