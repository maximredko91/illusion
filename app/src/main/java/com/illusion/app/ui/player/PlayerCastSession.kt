package com.illusion.app.ui.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.illusion.app.data.local.entity.MediaItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Casting the current item to a TV - Google Cast and DLNA (see the data.cast package). Split out of
 * [PlayerViewModel], which owns it and hands in what it needs: the live player (swapped on reload,
 * hence a getter), the item being played, and the watch-progress writers - the TV's position is
 * saved like local playback, so stopping a cast leaves the item resumable.
 *
 * Its state is a separate StateFlow on purpose: playItem/playTrailerItem rebuild
 * [PlayerUiState] from scratch, and a cast must survive that.
 */
class PlayerCastSession(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val player: () -> ExoPlayer,
    private val currentItem: () -> MediaItemEntity?,
    private val currentTrailerItem: () -> MediaItemEntity?,
    private val persistProgress: (positionMs: Long, durationMs: Long) -> Unit,
    private val maybeSaveProgress: (positionMs: Long, durationMs: Long) -> Unit
) {
    private val _castState = MutableStateFlow(CastUiState())
    val state: StateFlow<CastUiState> = _castState.asStateFlow()

    private val dlnaDiscovery by lazy { com.illusion.app.data.cast.DlnaDiscovery(appContext) }
    private var castController: com.illusion.app.data.cast.DlnaController? = null
    private var castPollJob: Job? = null
    private var castSearchJob: Job? = null
    private var googleCast: com.illusion.app.data.cast.GoogleCastController? = null
    private var castConnectJob: Job? = null
    private var pendingGoogleItem: MediaItemEntity? = null

    private fun ensureGoogleCast(): com.illusion.app.data.cast.GoogleCastController? {
        googleCast?.let { return it }
        return runCatching {
            com.illusion.app.data.cast.GoogleCastController(
                appContext,
                onDevices = { devices -> _castState.update { it.copy(googleDevices = devices) } },
                onConnected = ::loadGoogleCastMedia,
                onDisconnected = {
                    castConnectJob?.cancel()
                    castPollJob?.cancel()
                    pendingGoogleItem = null
                    val old = _castState.value
                    if (old.googleDeviceName != null) {
                        persistProgress(old.positionMs, old.durationMs)
                        player().seekTo(old.positionMs)
                    }
                    _castState.update { it.copy(googleDeviceName = null, isConnecting = false, isPlaying = false) }
                },
                onError = { code ->
                    castConnectJob?.cancel()
                    pendingGoogleItem = null
                    _castState.update { it.copy(isPickerOpen = true, isConnecting = false,
                        error = appContext.getString(com.illusion.app.R.string.player_cast_google_error, code)) }
                }
            ).also { googleCast = it }
        }.getOrElse {
            _castState.update { it.copy(error = appContext.getString(com.illusion.app.R.string.player_cast_google_unavailable)) }
            null
        }
    }

    fun castToGoogle(deviceId: String) {
        if (_castState.value.isConnecting || _castState.value.isCasting) return
        val item = currentItem() ?: currentTrailerItem() ?: return
        val controller = ensureGoogleCast() ?: return
        pendingGoogleItem = item
        _castState.update { it.copy(isConnecting = true, error = null) }
        castConnectJob?.cancel()
        castConnectJob = scope.launch {
            delay(30_000)
            controller.stop()
            pendingGoogleItem = null
            _castState.update { it.copy(isPickerOpen = true, isConnecting = false,
                error = appContext.getString(com.illusion.app.R.string.player_cast_error_failed)) }
        }
        controller.connect(deviceId)
    }

    private fun loadGoogleCastMedia(deviceName: String) {
        val item = pendingGoogleItem ?: return
        val controller = googleCast ?: return
        val path = if (currentItem() == null) item.trailerPath ?: item.filePath else item.filePath
        val position = player().currentPosition.coerceAtLeast(0)
        runCatching {
            val url = com.illusion.app.data.player.StreamingService.lanStreamUrl(
                appContext, item.sourceId, path, if (currentItem() == null) -1L else item.sizeBytes
            ) ?: error(appContext.getString(com.illusion.app.R.string.player_cast_error_no_network))
            controller.load(url, item.title,
                com.illusion.app.data.player.mimeTypeForExtension(path.substringAfterLast('.', "")), position) {
                castConnectJob?.cancel()
                pendingGoogleItem = null
                player().pause()
                controller.stopDiscovery()
                _castState.update { it.copy(isPickerOpen = false, isConnecting = false,
                    googleDeviceName = deviceName, isPlaying = true, positionMs = position,
                    durationMs = player().duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L, error = null) }
                startGoogleCastPolling()
            }
        }.onFailure { error ->
            castConnectJob?.cancel()
            pendingGoogleItem = null
            controller.stop()
            _castState.update { it.copy(isConnecting = false, error = error.message) }
        }
    }

    private fun startGoogleCastPolling() {
        castPollJob?.cancel()
        castPollJob = scope.launch {
            while (isActive) {
                val controller = googleCast ?: break
                val remote = controller.client
                if (remote != null && controller.ownsCurrentMedia()) {
                    val position = remote.approximateStreamPosition.coerceAtLeast(0)
                    val duration = remote.streamDuration.coerceAtLeast(0)
                    _castState.update { it.copy(positionMs = position,
                        durationMs = duration.takeIf { d -> d > 0 } ?: it.durationMs,
                        isPlaying = remote.isPlaying) }
                    maybeSaveProgress(position, _castState.value.durationMs)
                    // Громкость телевизора может измениться и его собственным пультом - забираем
                    // её тем же опросом, чтобы ползунок не расходился с реальностью.
                    controller.volume()?.let { volume -> _castState.update { it.copy(volume = volume) } }
                    if (remote.playerState == com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE) {
                        val failed = remote.idleReason == com.google.android.gms.cast.MediaStatus.IDLE_REASON_ERROR
                        if (remote.idleReason == com.google.android.gms.cast.MediaStatus.IDLE_REASON_FINISHED || failed) {
                            stopCast(resumeLocally = false)
                            if (failed) _castState.update { it.copy(isPickerOpen = true,
                                error = appContext.getString(com.illusion.app.R.string.player_cast_google_format_error)) }
                            break
                        }
                    }
                }
                delay(CAST_POLL_INTERVAL_MS)
            }
        }
    }

    fun openCastPicker() {
        _castState.update { it.copy(isPickerOpen = true, error = null) }
        refreshCastDevices()
    }

    fun closeCastPicker() {
        castSearchJob?.cancel()
        googleCast?.stopDiscovery()
        _castState.update { it.copy(isPickerOpen = false, isSearching = false) }
    }

    fun refreshCastDevices() {
        if (_castState.value.isCasting || _castState.value.isConnecting) return
        castSearchJob?.cancel()
        _castState.update { it.copy(isSearching = true, error = null) }
        ensureGoogleCast()?.startDiscovery()
        castSearchJob = scope.launch {
            val found = runCatching { dlnaDiscovery.discover() }.getOrDefault(emptyList())
            _castState.update { it.copy(isSearching = false, devices = found) }
        }
    }

    /**
     * Hands the currently playing file to [device] and pauses local playback.
     *
     * The renderer fetches the file from this app's own [com.illusion.app.data.player.StreamingService]
     * over the LAN - the TV can't read SMB itself, which is exactly why casting needed that bridge
     * first. A downloaded copy isn't used even when one exists: the bridge serves SMB paths, and
     * re-exposing app-private storage is a separate thing not worth building for this.
     *
     * Renderers need a moment between accepting a URL and being able to seek in it, hence the
     * delay before resuming at the local position - seeking too early is simply ignored by most.
     */
    fun castTo(device: com.illusion.app.data.cast.DlnaDevice) {
        if (_castState.value.isConnecting || _castState.value.isCasting) return
        val item = currentItem() ?: currentTrailerItem()
        if (item == null) {
            _castState.update { it.copy(error = appContext.getString(com.illusion.app.R.string.player_cast_error_no_item)) }
            return
        }
        val startPositionMs = player().currentPosition.coerceAtLeast(0)
        scope.launch {
            _castState.update { it.copy(isConnecting = true, error = null) }
            val url = com.illusion.app.data.player.StreamingService.lanStreamUrl(
                appContext, item.sourceId, item.filePath, item.sizeBytes
            )
            if (url == null) {
                _castState.update { it.copy(isConnecting = false, error = appContext.getString(com.illusion.app.R.string.player_cast_error_no_network)) }
                return@launch
            }
            player().pause()
            val controller = com.illusion.app.data.cast.DlnaController(device)
            val mimeType = com.illusion.app.data.player.mimeTypeForExtension(item.filePath.substringAfterLast('.', ""))
            runCatching { controller.playUrl(url, item.title, mimeType) }
                .onFailure {
                    _castState.update { state -> state.copy(isConnecting = false, error = appContext.getString(com.illusion.app.R.string.player_cast_error_failed)) }
                    return@launch
                }
            if (startPositionMs > 5_000) {
                delay(1_500)
                runCatching { controller.seekTo(startPositionMs) }
            }
            castController = controller
            val initialVolume = if (controller.supportsVolume) runCatching { controller.volume() }.getOrNull() else null
            _castState.update {
                it.copy(
                    isPickerOpen = false,
                    isConnecting = false,
                    device = device,
                    isPlaying = true,
                    positionMs = startPositionMs,
                    durationMs = player().duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L,
                    volume = initialVolume,
                    error = null
                )
            }
            startCastPolling()
        }
    }

    /** Asks the renderer where it is every couple of seconds - there's no push channel short of
     * subscribing to UPnP eventing (a whole callback HTTP server), and a 2 s cadence is plenty for
     * a progress bar. Watch progress is written from these reports too, so stopping the cast or
     * closing the player leaves the item resumable at the right spot. */
    private fun startCastPolling() {
        castPollJob?.cancel()
        castPollJob = scope.launch {
            while (isActive) {
                val controller = castController ?: break
                val position = runCatching { controller.position() }.getOrNull()
                val transport = runCatching { controller.transportState() }.getOrNull()
                if (position != null) {
                    _castState.update {
                        it.copy(
                            positionMs = position.positionMs,
                            durationMs = if (position.durationMs > 0) position.durationMs else it.durationMs
                        )
                    }
                    maybeSaveProgress(position.positionMs, position.durationMs)
                }
                if (controller.supportsVolume) {
                    runCatching { controller.volume() }.getOrNull()
                        ?.let { volume -> _castState.update { it.copy(volume = volume) } }
                }
                if (transport != null) {
                    _castState.update { it.copy(isPlaying = transport.isPlaying) }
                    // The renderer reaching the end is the cast's own "finished" signal - the local
                    // player never played those last minutes, so nothing else would notice.
                    if (transport.isStopped && _castState.value.positionMs > 0) {
                        stopCast(resumeLocally = false)
                        break
                    }
                }
                delay(CAST_POLL_INTERVAL_MS)
            }
        }
    }

    fun castTogglePlayPause() {
        if (_castState.value.googleDeviceName != null) {
            googleCast?.toggle()
            return
        }
        val controller = castController ?: return
        val playing = _castState.value.isPlaying
        _castState.update { it.copy(isPlaying = !playing) }
        scope.launch { runCatching { if (playing) controller.pause() else controller.play() } }
    }

    fun castSeekTo(positionMs: Long) {
        if (_castState.value.googleDeviceName != null) {
            val duration = _castState.value.durationMs
            googleCast?.seek(positionMs.coerceIn(0, duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
            return
        }
        val controller = castController ?: return
        val target = positionMs.coerceAtLeast(0)
        _castState.update { it.copy(positionMs = target) }
        scope.launch { runCatching { controller.seekTo(target) } }
    }

    fun castSeekBy(deltaMs: Long) = castSeekTo(_castState.value.positionMs + deltaMs)

    /**
     * The LAN HTTP bridge only exists for whoever is pulling from it. An external player is out of
     * this app's sight once launched (hence that path's six-hour idle timeout), but a cast ending
     * is a definite "nobody is reading any more" - close it right away rather than leave a port
     * open on every interface for hours.
     */
    private fun stopStreamingBridge() {
        runCatching { com.illusion.app.data.player.StreamingService.stop(appContext) }
    }

    /**
     * Sets the TV's own volume, 0..1 - the receiver's device volume for Cast, RenderingControl's
     * Master channel for DLNA. The local state is updated straight away rather than waiting for
     * the next poll, or the slider would snap back under the finger.
     */
    fun castSetVolume(volume: Float) {
        val target = volume.coerceIn(0f, 1f)
        _castState.update { it.copy(volume = target) }
        if (_castState.value.googleDeviceName != null) {
            googleCast?.setVolume(target)
            return
        }
        val controller = castController ?: return
        scope.launch { runCatching { controller.setVolume(target) } }
    }

    /**
     * Hardware volume keys while casting: they should move the TV, not this phone's own speaker,
     * which is silent anyway (local playback is paused). Returns true when the key was consumed -
     * see PlayerKeyEvents for how it reaches here.
     */
    fun castAdjustVolume(deltaSteps: Int): Boolean {
        if (!_castState.value.isCasting) return false
        val current = _castState.value.volume ?: return false
        castSetVolume(current + deltaSteps * VOLUME_STEP)
        return true
    }

    /**
     * Ends the cast. [resumeLocally] moves this device's own player to wherever the TV got to and
     * keeps playing there - what the "остановить трансляцию" button is for; the end-of-file case
     * passes false instead, since there's nothing left to resume.
     */
    fun stopCast(resumeLocally: Boolean = true) {
        if (_castState.value.googleDeviceName != null || pendingGoogleItem != null) {
            val old = _castState.value
            castConnectJob?.cancel()
            castPollJob?.cancel()
            pendingGoogleItem = null
            googleCast?.stop()
            if (old.googleDeviceName != null) {
                persistProgress(old.positionMs, old.durationMs)
                player().seekTo(old.positionMs)
                if (resumeLocally) player().play()
            }
            _castState.update { CastUiState(devices = it.devices, googleDevices = it.googleDevices) }
            stopStreamingBridge()
            return
        }
        val controller = castController ?: return
        val position = _castState.value.positionMs
        castPollJob?.cancel()
        castPollJob = null
        castController = null
        scope.launch { runCatching { controller.stop() } }
        persistProgress(position, _castState.value.durationMs)
        _castState.update { CastUiState(devices = it.devices, googleDevices = it.googleDevices) }
        stopStreamingBridge()
        if (resumeLocally && position > 0) {
            player().seekTo(position)
            player().play()
        }
    }

    /** Casting, or a Google Cast connection still being set up. */
    val isBusy: Boolean get() = _castState.value.isCasting || pendingGoogleItem != null

    fun release() {
        googleCast?.release()
    }

    private companion object {
        /** How often the cast session asks the renderer where it is - see [startCastPolling]. */
        const val CAST_POLL_INTERVAL_MS = 2_000L

        /** One press of a volume key = 5% of the TV's range, roughly what a TV's own remote does. */
        const val VOLUME_STEP = 0.05f
    }
}