package com.illusion.app.data.cast

import android.content.Context
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient

data class GoogleCastDevice(val id: String, val name: String)

/** Main-thread-only adapter for the SDK's discovery and session callbacks. */
class GoogleCastController(
    context: Context,
    private val onDevices: (List<GoogleCastDevice>) -> Unit,
    private val onConnected: (String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onError: (Int) -> Unit
) {
    private val castContext = CastContext.getSharedInstance(context)
    private val sessions = castContext.sessionManager
    private val router = MediaRouter.getInstance(context)
    private val selector = requireNotNull(castContext.mergedSelector)
    private var selected = false
    private var connecting = false
    private var discovering = false
    private var released = false
    private var contentId: String? = null

    val client: RemoteMediaClient?
        get() = if (selected) sessions.currentCastSession?.remoteMediaClient else null

    private val routesCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = publishDevices()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = publishDevices()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = publishDevices()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            if (selected && connecting) {
                connecting = false
                onConnected(session.castDevice?.friendlyName ?: "Google Cast")
            }
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) = failed(error)
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) {
            if (selected) {
                selected = false
                connecting = false
                contentId = null
                onDisconnected()
            }
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            // Reconnection retains the TV position; never reload the file on a resumed session.
            if (selected && connecting) onSessionStarted(session, session.sessionId.orEmpty())
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) = failed(error)
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    init {
        sessions.addSessionManagerListener(sessionListener, CastSession::class.java)
    }

    fun startDiscovery() {
        if (!discovering) {
            discovering = true
            router.addCallback(selector, routesCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        }
        publishDevices()
    }

    fun stopDiscovery() {
        router.removeCallback(routesCallback)
        discovering = false
    }

    private fun publishDevices() {
        onDevices(router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
            .map { GoogleCastDevice(it.id, it.name) }.distinctBy { it.id }.sortedBy { it.name })
    }

    fun connect(id: String) {
        val route = router.routes.firstOrNull { it.id == id && it.isEnabled && it.matchesSelector(selector) }
        if (route == null) {
            onError(-1)
            return
        }
        selected = true
        connecting = true
        val session = sessions.currentCastSession
        if (route.isSelected && session?.isConnected == true) {
            connecting = false
            onConnected(session.castDevice?.friendlyName ?: route.name)
        } else {
            route.select()
        }
    }

    fun load(url: String, title: String, mimeType: String, positionMs: Long, onLoaded: () -> Unit) {
        val remote = client ?: return failed(-1)
        contentId = url
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        val media = MediaInfo.Builder(url).setContentType(mimeType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata).build()
        remote.load(MediaLoadRequestData.Builder().setMediaInfo(media)
            .setAutoplay(true).setCurrentTime(positionMs).build()).setResultCallback { result ->
            if (!released && selected && contentId == url) {
                if (result.status.isSuccess) onLoaded() else failed(result.status.statusCode)
            }
        }
    }

    /** Ignore stale receiver status from before LOAD, or content started by another sender. */
    fun ownsCurrentMedia(): Boolean = contentId != null && client?.mediaInfo?.contentId == contentId

    fun toggle() {
        val remote = client ?: return
        val request = if (remote.isPlaying) remote.pause() else remote.play()
        request.setResultCallback { if (!released && selected && !it.status.isSuccess) onError(it.status.statusCode) }
    }

    fun seek(positionMs: Long) {
        client?.seek(MediaSeekOptions.Builder().setPosition(positionMs).build())?.setResultCallback {
            if (!released && selected && !it.status.isSuccess) onError(it.status.statusCode)
        }
    }

    fun stop() {
        val wasSelected = selected
        selected = false
        connecting = false
        contentId = null
        if (wasSelected) sessions.endCurrentSession(true)
    }

    private fun failed(code: Int) {
        if (!selected) return
        onError(code)
        stop()
        onDisconnected()
    }

    fun release() {
        released = true
        stopDiscovery()
        sessions.removeSessionManagerListener(sessionListener, CastSession::class.java)
        stop()
    }
}
