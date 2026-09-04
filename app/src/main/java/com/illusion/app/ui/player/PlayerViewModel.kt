package com.illusion.app.ui.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import com.illusion.app.data.download.DownloadStorage
import com.illusion.app.data.local.entity.DownloadEntity
import com.illusion.app.data.local.entity.DownloadStatus
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.player.ExternalPlayer
import com.illusion.app.data.player.PlaybackActivity
import com.illusion.app.data.player.SmbDataSourceFactory
import com.illusion.app.data.player.SmbMediaUri
import com.illusion.app.data.repository.DownloadRepository
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.repository.ThumbnailRepository
import com.illusion.app.data.repository.WatchProgressRepository
import com.illusion.app.data.settings.SHARPEN_AMOUNT_DEFAULT
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.data.smb.SmbCredentialStore
import com.illusion.app.domain.model.PlayerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrackOption(
    val group: Tracks.Group,
    val trackIndexInGroup: Int,
    val label: String,
    val isSelected: Boolean
)

data class ThumbnailFrames(
    val frames: List<ImageBitmap>,
    val intervalMs: Long
)

data class PlayerUiState(
    val isLoading: Boolean = true,
    /** True while PlayerMode.ASK is waiting on the user to pick internal vs. external for this one playback - see PlayerViewModel.load(). */
    val awaitingPlayerModeChoice: Boolean = false,
    /** False until [PlayerViewModel.playItem] actually commits to internal playback - stays false for the entire PlayerMode.EXTERNAL hand-off window (settings/SMB-credential lookups are async, so this screen briefly exists before that decision resolves). PlayerScreen gates the video surface, loading spinner, and PiP eligibility on this so that window doesn't visibly flash as if internal playback had started, and so backgrounding the app to launch the external app's Intent doesn't trigger PiP for a player that's never going to play anything. */
    val readyForInternalPlayback: Boolean = false,
    val title: String = "",
    val episodeLabel: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val error: String? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val subtitlesEnabled: Boolean = true,
    val hasNextEpisode: Boolean = false,
    val showSkipIntro: Boolean = false,
    /** Whether "mark end of intro" makes sense for the current item - only for series episodes, where the marker also propagates to the rest of the season. */
    val canMarkIntro: Boolean = false,
    /** Non-null when this episode/season already has an intro marker, so the settings dialog can show it and offer to clear it instead of only offering to (re-)mark it. */
    val introMarkedEndMs: Long? = null,
    /** Mirrors [showSkipIntro] but for the credits banner at the end of an episode - see [PlayerViewModel.isWithinOutro]. */
    val showSkipCredits: Boolean = false,
    /** Mirrors [canMarkIntro] for the "mark start of credits" action. */
    val canMarkCredits: Boolean = false,
    /** Mirrors [introMarkedEndMs] for the credits marker. */
    val outroMarkedStartMs: Long? = null,
    /** Non-null while a sleep timer is counting down - null means no timer is active. Survives episode/trailer transitions (see the playItem/playTrailerItem reconstructions), unlike the per-item intro/credits fields above. */
    val sleepTimerRemainingMs: Long? = null,
    val playbackSpeed: Float = 1f,
    val thumbnailFrames: ThumbnailFrames? = null,
    val videoAspectRatio: Float = 16f / 9f,
    val sharpenEnabled: Boolean = false,
    val sharpenAmount: Float = 0.4f,
    val seekDurationMs: Long = 10_000L,
    /** True once sharpen has been turned on at least once this player session - aspect-ratio cycling is inert from that point on (see the comment in [PlayerViewModel.init]). */
    val aspectRatioLockedBySharpen: Boolean = false,
    val doubleTapSeekEnabled: Boolean = true,
    val swipeSeekEnabled: Boolean = true,
    val holdToSeekEnabled: Boolean = true,
    val subtitleTextColor: Int = -0x1,
    val subtitleBackgroundOpacity: Int = 60,
    val subtitleTextSizePercent: Int = 100
)

@OptIn(UnstableApi::class)
class PlayerViewModel(
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val thumbnailRepository: ThumbnailRepository,
    private val generateThumbnailIfMissing: (MediaItemEntity) -> Unit,
    /** Fire-and-forget final progress save for [onCleared] - see [com.illusion.app.IllusionApplication.persistFinalWatchProgress]'s own KDoc for why this can't just be `viewModelScope.launch{}`. */
    private val persistFinalWatchProgress: (stableId: String, positionMs: Long, durationMs: Long, watched: Boolean, updatedAtMs: Long) -> Unit,
    private val settingsRepository: SettingsRepository,
    private val dataSourceFactory: SmbDataSourceFactory,
    private val downloadRepository: DownloadRepository,
    private val smbSourceRepository: SmbSourceRepository,
    private val credentialStore: SmbCredentialStore,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    /**
     * [disableCuesSeek] trades away seeking entirely for [item] in exchange for not hanging -
     * verified via javap that with MatroskaExtractor's Cues-seek disabled, it emits
     * SeekMap.Unseekable for the WHOLE file rather than a merely-less-precise seek map. Only worth
     * it for files already known (via [SettingsRepository.cuesSeekWorkaroundStableIds]) to hang
     * otherwise - see [playItem]'s stall watchdog for how a file gets added to that list. Normal
     * files never pass true here, so they keep full seeking.
     */
    private fun createPlayer(disableCuesSeek: Boolean = false): ExoPlayer = ExoPlayer.Builder(
        appContext,
        // EXTENSION_RENDERER_MODE_ON: falls back to the FFmpeg extension (DTS/AC3/TrueHD) only when no
        // platform decoder handles the format - a no-op today since the extension isn't on the classpath
        // yet (see scripts/build_ffmpeg_extension.sh), but no code change will be needed once it is.
        DefaultRenderersFactory(appContext).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    )
        // DefaultDataSource routes file:// URIs (a completed offline download) to Media3's built-in
        // FileDataSource and everything else to dataSourceFactory - so smb-item:// keeps streaming
        // over SMB exactly as before, with no branching needed at the MediaItem-building call site.
        .setMediaSourceFactory(
            (
                if (disableCuesSeek) {
                    DefaultMediaSourceFactory(
                        appContext,
                        DefaultExtractorsFactory().setMatroskaExtractorFlags(MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)
                    )
                } else {
                    DefaultMediaSourceFactory(appContext)
                }
            ).setDataSourceFactory(DefaultDataSource.Factory(appContext, dataSourceFactory))
        )
        .setLoadControl(
            buildAdaptiveLoadControl(appContext, settingsRepository.playerBufferSizeSnapshot)
        )
        .build()
        .apply { setWakeMode(C.WAKE_MODE_NETWORK) }

    // Backed by a StateFlow (not a plain val) so it can be swapped out entirely - reloadPlayer()
    // needs a truly fresh ExoPlayer instance to reset the sharpen effects pipeline (see its own
    // KDoc), and PlayerScreen's AndroidView needs to observe that swap to rebind view.player.
    private val _player = MutableStateFlow(createPlayer())
    val playerState: StateFlow<ExoPlayer> = _player.asStateFlow()
    val player: ExoPlayer get() = _player.value

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    // One-shot event, not a state field - the player-mode setting is checked once per load(), and
    // the screen just hands the intent to the OS and navigates back; there's nothing about "an
    // external player was launched" that should survive a recomposition or get re-fired by one.
    private val _launchExternalPlayer = Channel<Intent>(Channel.BUFFERED)
    val launchExternalPlayer: Flow<Intent> = _launchExternalPlayer.receiveAsFlow()

    private var currentItem: MediaItemEntity? = null
    private var currentTrailerItem: MediaItemEntity? = null

    /** Whether [player] (the CURRENT instance) was built with Cues-based seeking disabled - see [createPlayer]. */
    private var cuesSeekDisabledForCurrentPlayer = false

    /** Detects the Cues-table hang (see [SettingsRepository.cuesSeekWorkaroundStableIds]'s KDoc) on
     * a file playing for the first time, without needing the user to report it - cancelled as soon
     * as a new [playItem] call supersedes it. */
    private var stallWatchdogJob: Job? = null
    private var nextEpisode: MediaItemEntity? = null
    private var tickerJob: Job? = null
    private var lastSavedAtMs = 0L

    // Bound (not started) so PlaybackService only ever promotes itself to a foreground service
    // once the player it's holding actually starts playing - see PlaybackService's own KDoc for why
    // that's safer than starting it up front on a still-loading stream.
    private var playbackService: com.illusion.app.data.player.PlaybackService? = null
    private val playbackServiceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
            val service = (binder as? com.illusion.app.data.player.PlaybackService.LocalBinder)?.getService() ?: return
            playbackService = service
            service.attachPlayer(player)
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            playbackService = null
        }
    }

    private fun attachListeners(target: ExoPlayer) {
        target.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                // Only stop here when genuinely idle (paused, not buffering) - onPlaybackStateChanged
                // below is what keeps it running through STATE_BUFFERING, and isPlaying flips false
                // for that too (STATE_BUFFERING implies !isPlaying), so unconditionally stopping it
                // here would immediately undo that and this ticker would never survive a buffering
                // window at all - which is exactly the window the buffered% display needs it for.
                if (isPlaying) startTicker() else if (player.playbackState != Player.STATE_BUFFERING) stopTicker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
                // bufferedPositionMs/durationMs (the buffering-percent display) only ever updated
                // while actually playing - during the initial buffer, before playback has started
                // even once, isPlaying is always false, so the ticker never ran and the percent
                // could never appear (reported on-device: spinner with no number, on every file).
                if (playbackState == Player.STATE_BUFFERING) startTicker()
                if (playbackState == Player.STATE_ENDED) onPlaybackEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                // error.message is often ExoPlayer's generic per-errorCode text (e.g. "Source error") -
                // the actual IOException from SmbDataSource is one level down in .cause and is what
                // actually explains the failure (reconnect exhausted, auth issue, etc).
                val detail = error.cause?.message ?: error.message ?: "Ошибка воспроизведения"
                _state.update { it.copy(error = detail, isLoading = false) }
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracksFromPlayer(tracks)
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                    _state.update { it.copy(videoAspectRatio = ratio) }
                }
            }
        })
    }

    private var playbackServiceStarted = false

    /**
     * Both start AND bind: a purely-bound (never started) service is denied FGS-start eligibility
     * by Android 12+'s background-start restrictions - MediaSessionService's internal
     * startForeground() call for the notification silently no-ops in that case (no crash, no log -
     * it was only caught by dumpsys showing startForegroundCount=0). The session/notification
     * becomes active within milliseconds of binding, well inside the ~5s startForegroundService()
     * grace window, regardless of how long the stream itself takes to start buffering.
     *
     * Deliberately NOT called unconditionally from init - only from [playItem], which fires
     * exclusively on an actual internal-playback path (PlayerMode.INTERNAL, the ASK/EXTERNAL
     * fallback when no external app is available, or a trailer, which always plays internally).
     * Starting it eagerly for every PlayerScreen composition used to also fire it for
     * PlayerMode.EXTERNAL, where [load] immediately hands off to another app and this screen pops
     * itself via onBack() - the bound player then never actually plays anything, so
     * MediaSessionService never gets a reason to call its own startForeground(), and Android kills
     * the process ~5s later with ForegroundServiceDidNotStartInTimeException once the OS gets
     * around to enforcing it (observed on-device as a crash right after returning from an external
     * player, not immediately on launch).
     */
    private fun ensurePlaybackServiceStarted() {
        if (playbackServiceStarted) return
        playbackServiceStarted = true
        val serviceIntent = Intent(appContext, com.illusion.app.data.player.PlaybackService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(appContext, serviceIntent)
        appContext.bindService(serviceIntent, playbackServiceConnection, Context.BIND_AUTO_CREATE)
    }

    init {
        attachListeners(player)

        viewModelScope.launch {
            // Calling setVideoEffects() at all - even with an empty list - permanently switches
            // MediaCodecVideoRenderer onto its VideoSink/effects pipeline for this renderer
            // instance (videoEffects != null is the trigger, not list emptiness; see
            // MediaCodecVideoRenderer.onEnabled). That pipeline's onVideoSizeChanged is a
            // deliberate no-op in Media3 1.11.0 (upstream TODO b/292111083 - "Video size reporting
            // is removed at the moment..."), so Player.getVideoSize() stays stuck at UNKNOWN
            // forever, which silently breaks the resize-mode cycle button (Fit/Zoom/Fill) -
            // AspectRatioFrameLayout never sees a real aspect ratio to apply modes to. Skipping the
            // call while sharpen has never been turned on keeps the default (off) case unaffected;
            // once the user does turn it on for this session, the pipeline is unavoidably tainted
            // for the rest of it (upstream limitation), so later toggles just call through normally.
            var effectsPipelineTainted = false
            // Re-enabling sharpen after it was switched off once already in this same tainted
            // pipeline needs tracking separately from `enabled` itself - see the reload branch
            // below for why.
            var wasEnabled = false
            // Economical performance mode force-disables the GPU sharpen shader regardless of the
            // user's own sharpenEnabled switch - the single most expensive per-frame effect this
            // player can apply (see PerformanceMode's own KDoc for the other things it bundles).
            kotlinx.coroutines.flow.combine(
                settingsRepository.sharpenEnabled,
                settingsRepository.sharpenAmount,
                settingsRepository.performanceMode
            ) { enabled, amount, perfMode ->
                val economical = when (perfMode) {
                    com.illusion.app.domain.model.PerformanceMode.MAXIMUM -> false
                    com.illusion.app.domain.model.PerformanceMode.ECONOMICAL -> true
                    com.illusion.app.domain.model.PerformanceMode.AUTO ->
                        com.illusion.app.data.settings.DevicePerformance.isLowEndDevice(appContext)
                }
                (enabled && !economical) to amount
            }
                .collect { (enabled, amount) ->
                    val reenableAfterOff = enabled && effectsPipelineTainted && !wasEnabled
                    if (enabled) effectsPipelineTainted = true
                    // Updated before the reload branch below runs - reloadPlayer() reads
                    // _state.value.sharpenEnabled/sharpenAmount to decide what to re-apply on the
                    // fresh player instance, so it needs this emission's values, not the previous
                    // one's.
                    _state.update { it.copy(sharpenEnabled = enabled, sharpenAmount = amount, aspectRatioLockedBySharpen = effectsPipelineTainted) }
                    when {
                        reenableAfterOff -> {
                            // Media3's GL effects VideoSink doesn't reliably pick a fresh
                            // setVideoEffects() call back up once it's already seen an *empty*
                            // list on this renderer instance - confirmed on-device: toggling
                            // sharpen off then back on left the picture visibly unsharpened
                            // despite the call succeeding with no error. A full reload (position/
                            // playing state preserved, see reloadPlayer()'s own KDoc) is the only
                            // reliable way to make it reapply - fast enough that it doesn't read
                            // as a real reload to the user.
                            reloadPlayer()
                        }
                        enabled -> player.setVideoEffects(listOf(SharpenEffect(amount)))
                        // Was a plain player.setVideoEffects(emptyList()) call - crashed on-device
                        // (confirmed via logcat: BufferQueueProducer "already connected", EGL_BAD_ALLOC
                        // creating a new EGL surface) the moment a NEW media item was loaded afterward
                        // (e.g. autoplay-next, or retrying a transient error), not immediately on
                        // toggle. Root cause: the effects VideoSink's own EGL surface connection to
                        // the output Surface isn't released by an empty setVideoEffects() call either
                        // (same underlying Media3 1.11.0 limitation as the reenableAfterOff case above,
                        // just the mirror direction) - the plain MediaCodecVideoRenderer path then
                        // fails to connect to a Surface still held by the stale effects pipeline. A
                        // full reload releases and recreates that connection cleanly, same fix as
                        // re-enabling.
                        effectsPipelineTainted -> reloadPlayer()
                    }
                    wasEnabled = enabled
                }
        }

        viewModelScope.launch {
            settingsRepository.seekDurationSeconds.collect { seconds ->
                _state.update { it.copy(seekDurationMs = seconds * 1000L) }
            }
        }

        viewModelScope.launch {
            settingsRepository.doubleTapSeekEnabled.collect { enabled -> _state.update { it.copy(doubleTapSeekEnabled = enabled) } }
        }
        viewModelScope.launch {
            settingsRepository.swipeSeekEnabled.collect { enabled -> _state.update { it.copy(swipeSeekEnabled = enabled) } }
        }
        viewModelScope.launch {
            settingsRepository.holdToSeekEnabled.collect { enabled -> _state.update { it.copy(holdToSeekEnabled = enabled) } }
        }
        viewModelScope.launch {
            settingsRepository.subtitleTextColor.collect { color -> _state.update { it.copy(subtitleTextColor = color) } }
        }
        viewModelScope.launch {
            settingsRepository.subtitleBackgroundOpacity.collect { opacity -> _state.update { it.copy(subtitleBackgroundOpacity = opacity) } }
        }
        viewModelScope.launch {
            settingsRepository.subtitleTextSizePercent.collect { percent -> _state.update { it.copy(subtitleTextSizePercent = percent) } }
        }
    }

    fun setSharpenEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSharpenEnabled(enabled) }
    }

    fun setSharpenAmount(amount: Float) {
        viewModelScope.launch { settingsRepository.setSharpenAmount(amount) }
    }

    fun resetSharpenAmount() {
        viewModelScope.launch { settingsRepository.setSharpenAmount(SHARPEN_AMOUNT_DEFAULT) }
    }

    fun setSeekDurationSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setSeekDurationSeconds(seconds) }
    }

    fun setDoubleTapSeekEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDoubleTapSeekEnabled(enabled) }
    }

    fun setSwipeSeekEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSwipeSeekEnabled(enabled) }
    }

    fun setHoldToSeekEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHoldToSeekEnabled(enabled) }
    }

    fun setSubtitleTextColor(colorArgb: Int) {
        viewModelScope.launch { settingsRepository.setSubtitleTextColor(colorArgb) }
    }

    fun setSubtitleBackgroundOpacity(percent: Int) {
        viewModelScope.launch { settingsRepository.setSubtitleBackgroundOpacity(percent) }
    }

    fun setSubtitleTextSizePercent(percent: Int) {
        viewModelScope.launch { settingsRepository.setSubtitleTextSizePercent(percent) }
    }

    fun resetSubtitleStyle() {
        viewModelScope.launch { settingsRepository.resetSubtitleStyle() }
    }

    fun load(stableId: String, playTrailer: Boolean = false) {
        viewModelScope.launch {
            val item = libraryRepository.getById(stableId) ?: run {
                _state.update { it.copy(error = "Файл не найден в библиотеке", isLoading = false) }
                return@launch
            }
            if (playTrailer) {
                loadTrailer(item)
                return@launch
            }
            currentItem = item
            currentTrailerItem = null

            // Was a manual "open in external player" button inside the player's own settings
            // sheet (one-off, had to be tapped every single time) - moved to a persistent Settings
            // choice per user feedback, checked once here instead. Trailers always play internally
            // regardless (short clips, not worth the round-trip to another app), which is why this
            // sits after the `playTrailer` branch above rather than before it.
            when (settingsRepository.playerMode.first()) {
                PlayerMode.EXTERNAL -> {
                    val intent = resolveExternalPlayerIntent(item)
                    if (intent != null) {
                        _launchExternalPlayer.send(intent)
                        return@launch
                    }
                    // No compatible app / the item's SMB source no longer exists - fall through to
                    // internal playback rather than leaving the user stuck on a blank screen.
                }
                PlayerMode.ASK -> {
                    _state.update { it.copy(isLoading = false, awaitingPlayerModeChoice = true) }
                    return@launch
                }
                PlayerMode.INTERNAL -> Unit
            }

            playInternally(item)
        }
    }

    private suspend fun playInternally(item: MediaItemEntity) {
        val resume = watchProgressRepository.getProgress(item.stableId)
        val startPositionMs = resume?.takeIf { !it.watched }?.positionMs ?: 0L
        playItem(item, startPositionMs, autoPlay = true)
    }

    /** User's answer to the PlayerMode.ASK prompt for this one playback - falls back to internal playback if no external app is available. */
    fun choosePlayerMode(external: Boolean) {
        val item = currentItem ?: return
        viewModelScope.launch {
            _state.update { it.copy(awaitingPlayerModeChoice = false) }
            val intent = if (external) resolveExternalPlayerIntent(item) else null
            if (intent != null) {
                _launchExternalPlayer.send(intent)
            } else {
                playInternally(item)
            }
        }
    }

    /** Actually hands [item] to the current player - shared by [load] (fresh navigation into the
     * player) and [reloadPlayer] (same item, same [player] instance identity swapped out under it). */
    private suspend fun playItem(item: MediaItemEntity, startPositionMs: Long, autoPlay: Boolean) {
        stallWatchdogJob?.cancel()
        val needsCuesWorkaround = item.stableId in settingsRepository.cuesSeekWorkaroundStableIds.first()
        if (needsCuesWorkaround != cuesSeekDisabledForCurrentPlayer) {
            val old = player
            old.release()
            val fresh = createPlayer(disableCuesSeek = needsCuesWorkaround)
            attachListeners(fresh)
            _player.value = fresh
            cuesSeekDisabledForCurrentPlayer = needsCuesWorkaround
            playbackService?.attachPlayer(fresh)
            if (_state.value.sharpenEnabled) fresh.setVideoEffects(listOf(SharpenEffect(_state.value.sharpenAmount)))
        }
        if (!needsCuesWorkaround) {
            // First play of a file with no known Cues-table problem: watch for the exact signature
            // of the hang documented in project memory - stuck in BUFFERING, never having advanced
            // past its start position, for far longer than any real (even very slow) initial
            // buffer should take. If it hits, this is almost certainly the same pathological-Cues
            // hang, not just a slow network - remember it and transparently retry with seeking
            // disabled instead of leaving the user stuck forever.
            val watchedItem = item
            val watchedStartPositionMs = startPositionMs
            val watchedAutoPlay = autoPlay
            stallWatchdogJob = viewModelScope.launch {
                delay(STALL_WATCHDOG_TIMEOUT_MS)
                if (player.playbackState == Player.STATE_BUFFERING &&
                    player.currentPosition <= watchedStartPositionMs + 2_000
                ) {
                    settingsRepository.addCuesSeekWorkaroundStableId(watchedItem.stableId)
                    // Must null this out BEFORE recursing - playItem()'s own first line cancels
                    // stallWatchdogJob to supersede a stale watchdog from a PREVIOUS call, but this
                    // recursive call is running INSIDE that same job. Leaving the reference in place
                    // meant that line canceled its own enclosing coroutine, which took effect (as a
                    // CancellationException) at the very next suspend call and silently aborted the
                    // retry before it ever got to swap the player - confirmed on-device as the
                    // workaround never actually kicking in (buffering stayed infinite).
                    stallWatchdogJob = null
                    playItem(watchedItem, watchedStartPositionMs, watchedAutoPlay)
                }
            }
        }
        ensurePlaybackServiceStarted()
        _state.update { it.copy(readyForInternalPlayback = true) }
        // See PlaybackActivity's own KDoc - lets ThumbnailGenerator back off from the shared
        // hardware video decoder while real playback needs it.
        PlaybackActivity.isActive = true
        val download = completedDownload(item.stableId)
        val uri = download?.let { Uri.parse(it.contentUri) } ?: SmbMediaUri.build(item.sourceId, item.filePath, item.sizeBytes)
        val subtitleConfigs = if (download != null && download.subtitles.isNotEmpty()) {
            download.subtitles.map { sub -> buildSubtitleConfig(Uri.parse(sub.uri), sub.remotePath) }
        } else {
            item.subtitlePaths.map { path -> buildSubtitleConfig(item.sourceId, path) }
        }
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
            .build()

        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playWhenReady = autoPlay

        val episodeLabel = if (item.seasonNumber != null && item.episodeNumber != null) {
            "S${item.seasonNumber}E${item.episodeNumber} · ${item.title}"
        } else {
            null
        }

        _state.update {
            PlayerUiState(
                isLoading = true,
                title = item.title,
                episodeLabel = episodeLabel,
                // A fresh PlayerUiState() defaults both of these to 0 - on a plain load() that's
                // invisible (the seek bar isn't on screen yet), but reloadPlayer() (sharpen
                // toggle/reload-after-tainted-effects-pipeline) replaces state on an ALREADY-VISIBLE
                // seek bar showing e.g. "1:07 / 1:39:10". Leaving these at their 0 default for even
                // one recomposition put a stale currentPositionMs against a reset durationMs=0,
                // which Slider clamps to its far right end (value > valueRange.end) - looked like the
                // bar flashing to the very end and back once the real duration arrived a moment
                // later. Seeding both from what's already known (the resume position this reload was
                // called with, and the previous duration - accurate for the same file, and corrected
                // within one tick either way) keeps the bar visually stationary through the reload.
                currentPositionMs = startPositionMs,
                durationMs = it.durationMs,
                subtitlesEnabled = it.subtitlesEnabled,
                sharpenEnabled = it.sharpenEnabled,
                sharpenAmount = it.sharpenAmount,
                seekDurationMs = it.seekDurationMs,
                doubleTapSeekEnabled = it.doubleTapSeekEnabled,
                swipeSeekEnabled = it.swipeSeekEnabled,
                holdToSeekEnabled = it.holdToSeekEnabled,
                subtitleTextColor = it.subtitleTextColor,
                subtitleBackgroundOpacity = it.subtitleBackgroundOpacity,
                subtitleTextSizePercent = it.subtitleTextSizePercent,
                canMarkIntro = item.seriesStableId != null && item.seasonNumber != null,
                introMarkedEndMs = item.introEndMs,
                canMarkCredits = item.seriesStableId != null && item.seasonNumber != null,
                outroMarkedStartMs = item.outroStartMs,
                sleepTimerRemainingMs = it.sleepTimerRemainingMs,
                readyForInternalPlayback = it.readyForInternalPlayback
            )
        }

        checkNextEpisode(item)
        viewModelScope.launch { loadThumbnailFrames(item) }
    }

    /**
     * Swaps in a brand-new [ExoPlayer] instance at the same position/playing-state instead of
     * requiring the user to back out to Details and press Play again - turning sharpen on taints
     * the effects pipeline for the rest of the session (see the KDoc in [init]), and the only way
     * to actually undo that is a fresh ExoPlayer. Previously the only way to get one was the full
     * navigate-away-and-back round trip, which dropped the user back on the Details card instead of
     * where they were watching - this does the same "close and reopen" the warning dialog already
     * told them to do, just without leaving this screen at all.
     */
    // Rapid-fire reload requests (e.g. mashing the sharpen quick-toggle - each off->on edge
    // triggers a reload, see the sharpenEnabled/sharpenAmount collector in init) used to overlap:
    // reloadPlayer() swaps _player.value synchronously but finishes the actual playItem()/
    // playTrailerItem() call in a launched coroutine, so a second call arriving before that
    // coroutine ran would release()/replace the player out from under it, and the first
    // coroutine's eventual playItem() call would then hit an already-released or already-replaced
    // instance - confirmed on-device as an IllegalStateException from ExoPlayer. isReloading makes
    // overlapping requests coalesce into one follow-up reload instead of firing concurrently -
    // safe to coalesce since reloadPlayer() always reads the *current* _state.value/currentItem
    // fresh, never stale captured values.
    private var isReloading = false
    private var reloadRequestedAgain = false

    fun reloadPlayer() {
        if (isReloading) {
            reloadRequestedAgain = true
            return
        }
        isReloading = true

        val old = player
        val position = old.currentPosition.coerceAtLeast(0)
        // old.isPlaying (not just playWhenReady) is false during a momentary rebuffer even though
        // the user never paused anything - a reload that happened to land mid-stall (e.g. a
        // transient SMB reconnect, confirmed to occur in the wild via SmbDataSource's own retry
        // logging) would then resume paused instead of picking playback back up. playWhenReady
        // reflects actual user intent, not the decoder's instantaneous state, and survives that.
        val wasPlaying = old.playWhenReady
        val duration = old.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L
        val item = currentItem
        val trailerItem = currentTrailerItem
        if (item != null) persistProgress(position, duration)

        old.release()
        // Preserves whichever mode (normal / Cues-seek-disabled) the player was already in -
        // reloadPlayer() exists for the sharpen-effects pipeline, unrelated to Cues workaround
        // status, so this must not silently reset a file back to the mode that was hanging it.
        val fresh = createPlayer(disableCuesSeek = cuesSeekDisabledForCurrentPlayer)
        attachListeners(fresh)
        _player.value = fresh
        // MediaSession's player can't be swapped in place - re-attaching rebuilds it around the
        // fresh instance so the notification keeps working after a sharpen-triggered reload.
        playbackService?.attachPlayer(fresh)
        if (_state.value.sharpenEnabled) fresh.setVideoEffects(listOf(SharpenEffect(_state.value.sharpenAmount)))

        viewModelScope.launch {
            when {
                item != null -> playItem(item, position, wasPlaying)
                trailerItem != null -> playTrailerItem(trailerItem, position, wasPlaying)
            }
            isReloading = false
            if (reloadRequestedAgain) {
                reloadRequestedAgain = false
                reloadPlayer()
            }
        }
    }

    /**
     * Plays the trailer file found next to [item] during scanning, not [item] itself. Deliberately
     * leaves `currentItem`/`nextEpisode` unset - a trailer isn't a library item in its own right, so
     * watch-progress persistence, autoplay-next and skip-intro (all gated on `currentItem`) simply
     * no-op for the duration, which is the correct behavior here.
     */
    private fun loadTrailer(item: MediaItemEntity) {
        if (item.trailerPath == null) {
            _state.update { it.copy(error = "Трейлер не найден", isLoading = false) }
            return
        }
        currentItem = null
        currentTrailerItem = item
        nextEpisode = null
        // Trailers always play internally with no player-mode gate (see load()'s own comment) -
        // nothing else on this path ever sets this true the way playInternally() does for the main
        // item, so without it the video surface never renders even though playback itself starts
        // fine (confirmed: black screen, audio playing).
        _state.update { it.copy(readyForInternalPlayback = true) }
        playTrailerItem(item, startPositionMs = 0L, autoPlay = true)
    }

    private fun playTrailerItem(item: MediaItemEntity, startPositionMs: Long, autoPlay: Boolean) {
        val path = item.trailerPath ?: return
        val uri = SmbMediaUri.build(item.sourceId, path, item.trailerSizeBytes ?: -1L)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
            .build()
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playWhenReady = autoPlay
        _state.update {
            PlayerUiState(
                isLoading = true,
                title = "${item.title} — трейлер",
                subtitlesEnabled = it.subtitlesEnabled,
                sharpenEnabled = it.sharpenEnabled,
                sharpenAmount = it.sharpenAmount,
                seekDurationMs = it.seekDurationMs,
                doubleTapSeekEnabled = it.doubleTapSeekEnabled,
                swipeSeekEnabled = it.swipeSeekEnabled,
                holdToSeekEnabled = it.holdToSeekEnabled,
                subtitleTextColor = it.subtitleTextColor,
                subtitleBackgroundOpacity = it.subtitleBackgroundOpacity,
                subtitleTextSizePercent = it.subtitleTextSizePercent,
                sleepTimerRemainingMs = it.sleepTimerRemainingMs,
                // Missing here (unlike playItem's own reconstruction, which explicitly carries
                // this forward) meant every trailer playback silently reset it to the class
                // default of false - the video surface never became "ready" from the UI's
                // perspective, so it stayed black while ExoPlayer itself decoded/played audio
                // completely normally underneath, invisible to anything gating on this flag.
                readyForInternalPlayback = it.readyForInternalPlayback
            )
        }
    }

    private suspend fun completedDownload(stableId: String): DownloadEntity? {
        val download = downloadRepository.getForItem(stableId) ?: return null
        if (download.status != DownloadStatus.COMPLETED) return null
        return download.takeIf { DownloadStorage.exists(appContext, Uri.parse(it.contentUri)) }
    }

    /** Intent to hand [item] off to an external video player app - null if there's no compatible app or its SMB source no longer exists. */
    /** Returns null (never an unresolvable [Intent]) if the pinned app is gone (uninstalled since
     * it was chosen in Settings) or, with no pinned app, nothing on the device declares itself able
     * to view smb://-scheme content at all - `resolveActivity` is what ACTION_VIEW's own dispatch
     * uses internally, so this mirrors exactly what startActivity would actually do rather than
     * guessing. Without this check, [load]'s "fall through to internal playback" comment was a lie -
     * a stale/uninstalled pinned package, or a fresh device with no SMB-capable player installed at
     * all, produced an Intent that resolved to nothing, and startActivity's resulting
     * ActivityNotFoundException just dumped the user out to a "no app found" toast and back to
     * Details instead of ever reaching internal playback. */
    private fun Intent.resolvesToRealApp(): Boolean =
        resolveActivity(appContext.packageManager) != null

    private suspend fun resolveExternalPlayerIntent(item: MediaItemEntity): Intent? {
        val packageName = settingsRepository.externalPlayerPackage.first()
        val download = completedDownload(item.stableId)
        val intent = if (download != null) {
            ExternalPlayer.forDownload(download.contentUri, item.title, packageName)
        } else {
            // Not downloaded - no real local Uri exists for an external app to read directly, so
            // StreamingService re-serves this SMB file over a loopback-only HTTP url instead of a
            // literal smb:// one (see ExternalPlayer's own KDoc for why that raw scheme approach
            // didn't actually work for anything but VLC/MX Player).
            smbSourceRepository.getById(item.sourceId) ?: return null
            val streamUrl = com.illusion.app.data.player.StreamingService.streamUrl(
                appContext, item.sourceId, item.filePath, item.sizeBytes
            )
            ExternalPlayer.forUrl(Uri.parse(streamUrl), item.title, packageName)
        }
        return intent.takeIf { it.resolvesToRealApp() }
    }

    /**
     * No more proactive whole-library background scan (removed [com.illusion.app.work.ThumbnailGenerationWorker]) -
     * a sprite is generated lazily the first time each item is opened, and cached from then on.
     * Can't generate it synchronously right here though: by this point [PlaybackActivity.isActive]
     * is already true for *this* item's own playback (set in [playItem] just before this is
     * called), and [com.illusion.app.data.scan.ThumbnailGenerator] deliberately no-ops while any
     * playback is active - it shares the one hardware video decoder with real playback and
     * contending for it was confirmed on-device to blank the video. So a missing sprite instead
     * just kicks off [generateThumbnailIfMissing] (runs on an application-scoped coroutine, not
     * [viewModelScope], so it survives this screen closing) - it'll actually generate once
     * PlaybackActivity.isActive goes back to false, i.e. once nothing is playing. This session's
     * own scrub bar simply has no preview thumbnails; the next time this item is opened, it will.
     */
    private suspend fun loadThumbnailFrames(item: MediaItemEntity) {
        val stableId = item.stableId
        val frames = withContext(Dispatchers.IO) {
            val sprite = thumbnailRepository.getForItem(stableId) ?: run {
                generateThumbnailIfMissing(item)
                return@withContext null
            }
            val sheet = BitmapFactory.decodeFile(sprite.filePath) ?: return@withContext null
            val sliced = (0 until sprite.frameCount).mapNotNull { index ->
                val col = index % sprite.columns
                val row = index / sprite.columns
                val x = col * sprite.frameWidth
                val y = row * sprite.frameHeight
                if (x + sprite.frameWidth > sheet.width || y + sprite.frameHeight > sheet.height) return@mapNotNull null
                Bitmap.createBitmap(sheet, x, y, sprite.frameWidth, sprite.frameHeight).asImageBitmap()
            }
            sheet.recycle()
            sliced.takeIf { it.isNotEmpty() }?.let { ThumbnailFrames(it, sprite.intervalMs) }
        }
        if (frames != null && currentItem?.stableId == stableId) {
            _state.update { it.copy(thumbnailFrames = frames) }
        }
    }

    private suspend fun checkNextEpisode(item: MediaItemEntity) {
        val seriesId = item.seriesStableId
        val next = if (seriesId != null) {
            val episodes = libraryRepository.observeEpisodes(seriesId).first()
            val currentIndex = episodes.indexOfFirst { it.stableId == item.stableId }
            episodes.getOrNull(currentIndex + 1)
        } else {
            null
        }
        nextEpisode = next
        _state.update { it.copy(hasNextEpisode = next != null) }
    }

    private fun onPlaybackEnded() {
        persistProgress(player.duration.coerceAtLeast(0), player.duration.coerceAtLeast(0))
        if (nextEpisode != null) playNext()
    }

    fun playNext() {
        val next = nextEpisode ?: return
        load(next.stableId)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, duration))
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    fun skipIntro() {
        currentItem?.introEndMs?.let { seekTo(it) }
    }

    /**
     * Manual stand-in for audio-fingerprint auto-detection (not built yet - see
     * LibraryRepository.markIntroEnd): the user taps this at the moment the intro actually ends,
     * marking 0..currentPosition as the intro for this episode and, via the repository, every
     * other episode in the same season. Updates [currentItem] in memory too so the skip-intro
     * banner can react without waiting for a reload.
     */
    fun markIntroEnd() {
        val item = currentItem ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0)
        currentItem = item.copy(introStartMs = 0L, introEndMs = positionMs)
        _state.update { it.copy(introMarkedEndMs = positionMs) }
        viewModelScope.launch {
            libraryRepository.markIntroEnd(item, positionMs)
        }
    }

    /** Undoes [markIntroEnd] for the whole season, in case it was marked at the wrong spot. */
    fun clearIntroMarkers() {
        val item = currentItem ?: return
        currentItem = item.copy(introStartMs = null, introEndMs = null)
        _state.update { it.copy(introMarkedEndMs = null, showSkipIntro = false) }
        viewModelScope.launch {
            libraryRepository.clearIntroMarkers(item)
        }
    }

    /** Mirrors [markIntroEnd] but for the credits at the end of an episode - only a start position is marked, since credits run to EOF. */
    fun markCreditsStart() {
        val item = currentItem ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0)
        currentItem = item.copy(outroStartMs = positionMs)
        _state.update { it.copy(outroMarkedStartMs = positionMs) }
        viewModelScope.launch {
            libraryRepository.markCreditsStart(item, positionMs)
        }
    }

    /** Undoes [markCreditsStart] for the whole season. */
    fun clearOutroMarker() {
        val item = currentItem ?: return
        currentItem = item.copy(outroStartMs = null)
        _state.update { it.copy(outroMarkedStartMs = null, showSkipCredits = false) }
        viewModelScope.launch {
            libraryRepository.clearOutroMarker(item)
        }
    }

    private var sleepTimerJob: Job? = null

    /** Starts (or replaces) a countdown that pauses playback once it reaches zero - not tied to any particular item, so it survives autoplay-to-next-episode same as playbackSpeed. */
    fun setSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        _state.update { it.copy(sleepTimerRemainingMs = durationMs) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _state.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0)) }
            }
            player.pause()
            _state.update { it.copy(sleepTimerRemainingMs = null) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.update { it.copy(sleepTimerRemainingMs = null) }
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun selectAudioTrack(option: TrackOption) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndexInGroup))
            .build()
    }

    fun selectSubtitleTrack(option: TrackOption?) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            _state.update { it.copy(subtitlesEnabled = false) }
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndexInGroup))
            _state.update { it.copy(subtitlesEnabled = true) }
        }
        player.trackSelectionParameters = builder.build()
    }

    /**
     * Diagnostic snapshot of the currently rendered video track - used to verify HDR10/DV actually
     * negotiated on-device. Also flags Dolby Vision Profile 7 specifically: it's DV's only
     * dual-layer profile (a low-res enhancement-layer stream + RPU on top of the base layer), and
     * this app can never composite that layer even in principle - Media3's own MP4/MKV extractors
     * only ever parse the `dvcC`/`dvvC` config box for a codec string (verified by decompiling
     * media3-container 1.11.0's `DolbyVisionConfig.java` - it stops after profile/level, never
     * reads `el_present_flag` or an EL track), and AOSP's own HDR docs confirm BL+EL concatenation
     * requires a vendor-supplied "Dolby-Vision capable MediaExtractor" that only the platform
     * `android.media.MediaExtractor` can hand off to - not available through ExoPlayer's Java
     * extractors, which is all this app's custom SmbDataSource pipeline uses. True either on the
     * phone or the Xiaomi TV box, regardless of that box's decoder chip. Profile 7 still plays -
     * MediaCodec decodes the base layer like any other HEVC track - just without the EL detail.
     */
    fun currentVideoFormatSummary(): String {
        val format = player.videoFormat
        val item = currentItem ?: currentTrailerItem
        val audio = player.audioFormat

        return buildString {
            item?.let {
                appendLine("Файл: ${it.filePath.substringAfterLast('\\')}")
                appendLine("Размер: ${formatFileSize(it.sizeBytes)}")
            }
            if (player.duration > 0) appendLine("Длительность: ${formatTime(player.duration)}")

            if (format == null) {
                appendLine()
                appendLine("Видеодорожка ещё не определена")
            } else {
                val color = format.colorInfo
                val dvProfile = format.codecs
                    ?.takeIf { it.startsWith("dvhe") || it.startsWith("dvh1") || it.startsWith("dva1") || it.startsWith("dvav") }
                    ?.split(".")
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                val dynamicRange = when {
                    dvProfile != null -> "Dolby Vision (Profile $dvProfile)"
                    color?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10/HDR10+"
                    color?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
                    else -> "SDR"
                }
                appendLine()
                appendLine("Видео")
                appendLine("Кодек: ${format.sampleMimeType ?: "—"} (${format.codecs ?: "—"})")
                appendLine("Разрешение: ${format.width}x${format.height}")
                if (format.frameRate > 0) appendLine("Частота кадров: ${"%.2f".format(format.frameRate)} fps")
                if (format.bitrate > 0) appendLine("Битрейт: ${format.bitrate / 1000} кбит/с")
                appendLine("Динамический диапазон: $dynamicRange")
                appendLine("Цвет: пространство=${color?.colorSpace ?: "—"}, transfer=${color?.colorTransfer ?: "—"}, range=${color?.colorRange ?: "—"}")
                if (dvProfile == 7) {
                    appendLine(
                        "⚠ Profile 7 хранит доп. детализацию в отдельном enhancement-layer потоке. " +
                            "Плеер показывает только базовый слой - картинка корректна, но без этой детализации."
                    )
                }
            }

            if (audio != null) {
                appendLine()
                appendLine("Аудио")
                appendLine("Кодек: ${audio.sampleMimeType ?: "—"} (${audio.codecs ?: "—"})")
                if (audio.channelCount != androidx.media3.common.Format.NO_VALUE) appendLine("Каналы: ${audio.channelCount}")
                if (audio.sampleRate != androidx.media3.common.Format.NO_VALUE) appendLine("Частота дискретизации: ${audio.sampleRate} Гц")
                if (audio.bitrate > 0) appendLine("Битрейт: ${audio.bitrate / 1000} кбит/с")
            }

            if (_state.value.audioTracks.size > 1) {
                appendLine()
                appendLine("Все аудиодорожки: ${_state.value.audioTracks.joinToString(", ") { it.label }}")
            }
            if (_state.value.subtitleTracks.isNotEmpty()) {
                appendLine("Субтитры: ${_state.value.subtitleTracks.joinToString(", ") { it.label }}")
            } else {
                appendLine()
                appendLine("Субтитры: нет")
            }
        }.trim()
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        val units = arrayOf("Б", "КБ", "МБ", "ГБ")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return "%.1f %s".format(value, units[unitIndex])
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        currentItem?.let { load(it.stableId) }
        currentTrailerItem?.let { load(it.stableId, playTrailer = true) }
    }

    private fun updateTracksFromPlayer(tracks: Tracks) {
        val audio = mutableListOf<TrackOption>()
        val subtitles = mutableListOf<TrackOption>()
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val label = format.language ?: format.label ?: "Дорожка ${i + 1}"
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> audio += TrackOption(group, i, label, group.isTrackSelected(i))
                    C.TRACK_TYPE_TEXT -> subtitles += TrackOption(group, i, label, group.isTrackSelected(i))
                    else -> Unit
                }
            }
        }
        _state.update { it.copy(audioTracks = audio, subtitleTracks = subtitles) }
    }

    private fun buildSubtitleConfig(sourceId: Long, path: String): MediaItem.SubtitleConfiguration =
        buildSubtitleConfig(SmbMediaUri.build(sourceId, path), path)

    /** [remotePath] is only used to derive mime type/language/label from its filename - the actual bytes are read from [uri], which may be the live SMB path or a downloaded local copy. */
    private fun buildSubtitleConfig(uri: Uri, remotePath: String): MediaItem.SubtitleConfiguration {
        val extension = remotePath.substringAfterLast('.', "").lowercase()
        val mimeType = when (extension) {
            "ass" -> MimeTypes.TEXT_SSA
            "vtt" -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val fileName = remotePath.substringAfterLast('\\')
        val language = guessLanguage(fileName)
        return MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLanguage(language)
            .setLabel(language ?: fileName)
            // Without a selection flag, ExoPlayer's default track selector never auto-picks a text
            // track on its own - subtitlesEnabled=true in the UI state only means the track TYPE
            // isn't disabled, it doesn't mean any specific track actually gets selected/rendered.
            // Sidecar subtitles found during scanning are exactly the case a viewer wants shown
            // automatically (unlike embedded tracks in other languages they didn't ask for).
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    private fun guessLanguage(fileName: String): String? {
        val parts = fileName.split('.')
        if (parts.size < 3) return null
        val candidate = parts[parts.size - 2]
        return candidate.takeIf { it.length in 2..3 && it.all { c -> c.isLetter() } }?.lowercase()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                val position = player.currentPosition.coerceAtLeast(0)
                val duration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L
                val buffered = player.bufferedPosition.coerceAtLeast(0)
                _state.update {
                    it.copy(
                        currentPositionMs = position,
                        // A freshly created ExoPlayer (reloadPlayer(), e.g. from the sharpen
                        // toggle) reports duration as C.TIME_UNSET - coerced to 0 above - until it
                        // actually reads the container's metadata, which is a real network round
                        // trip over SmbDataSource, not instant. Ticking through a real, large
                        // currentPositionMs (position preserved across the reload) against a
                        // momentarily-0 durationMs isn't just briefly wrong data - the seek bar's
                        // valueRange is 0..durationMs, so it visibly snapped to the far right end
                        // for however many 500ms ticks metadata took to arrive, then back once the
                        // real duration landed. Keeping the last known non-zero duration instead of
                        // regressing to 0 makes that transition invisible - the real value (same
                        // file, so unchanged) arrives a tick or two later regardless.
                        durationMs = if (duration > 0) duration else it.durationMs,
                        bufferedPositionMs = buffered,
                        showSkipIntro = isWithinIntro(position),
                        showSkipCredits = isWithinOutro(position)
                    )
                }
                maybeSaveProgress(position, duration)
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
    }

    private fun isWithinIntro(positionMs: Long): Boolean {
        val start = currentItem?.introStartMs ?: return false
        val end = currentItem?.introEndMs ?: return false
        return positionMs in start..end
    }

    /** No upper bound needed (credits run to EOF) - gated on there actually being a next episode to skip to, otherwise the banner would offer to "skip" into nothing. */
    private fun isWithinOutro(positionMs: Long): Boolean {
        val start = currentItem?.outroStartMs ?: return false
        return positionMs >= start && nextEpisode != null
    }

    private fun maybeSaveProgress(position: Long, duration: Long) {
        val now = System.currentTimeMillis()
        if (now - lastSavedAtMs < 5000) return
        lastSavedAtMs = now
        persistProgress(position, duration)
    }

    private fun persistProgress(position: Long, duration: Long) {
        val item = currentItem ?: return
        val watched = duration > 0 && position >= duration - 5000
        viewModelScope.launch {
            watchProgressRepository.updateProgress(item.stableId, position, duration, watched, System.currentTimeMillis())
        }
    }

    override fun onCleared() {
        PlaybackActivity.isActive = false
        val item = currentItem
        if (item != null) {
            val position = player.currentPosition.coerceAtLeast(0)
            val duration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L
            val watched = duration > 0 && position >= duration - 5000
            persistFinalWatchProgress(item.stableId, position, duration, watched, System.currentTimeMillis())
        }
        if (playbackServiceStarted) {
            playbackService?.detachPlayer()
            runCatching { appContext.unbindService(playbackServiceConnection) }
            appContext.stopService(Intent(appContext, com.illusion.app.data.player.PlaybackService::class.java))
        }
        player.release()
    }

    companion object {
        /** How long a first-ever play is allowed to sit in BUFFERING at/near its start position before [playItem] assumes it's the Cues-table hang, not just a slow network - generous on purpose since falsely tripping it permanently disables seeking for that file. */
        private const val STALL_WATCHDOG_TIMEOUT_MS = 30_000L

        fun factory(
            libraryRepository: LibraryRepository,
            watchProgressRepository: WatchProgressRepository,
            thumbnailRepository: ThumbnailRepository,
            generateThumbnailIfMissing: (MediaItemEntity) -> Unit,
            persistFinalWatchProgress: (stableId: String, positionMs: Long, durationMs: Long, watched: Boolean, updatedAtMs: Long) -> Unit,
            settingsRepository: SettingsRepository,
            dataSourceFactory: SmbDataSourceFactory,
            downloadRepository: DownloadRepository,
            smbSourceRepository: SmbSourceRepository,
            credentialStore: SmbCredentialStore,
            context: Context
        ) = viewModelFactory {
            initializer {
                PlayerViewModel(
                    libraryRepository,
                    watchProgressRepository,
                    thumbnailRepository,
                    generateThumbnailIfMissing,
                    persistFinalWatchProgress,
                    settingsRepository,
                    dataSourceFactory,
                    downloadRepository,
                    smbSourceRepository,
                    credentialStore,
                    context.applicationContext
                )
            }
        }
    }
}
