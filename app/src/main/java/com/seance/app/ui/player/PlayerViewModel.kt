package com.seance.app.ui.player

import android.content.Context
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
import com.seance.app.data.download.DownloadStorage
import com.seance.app.data.local.entity.DownloadEntity
import com.seance.app.data.local.entity.DownloadStatus
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.player.SmbDataSourceFactory
import com.seance.app.data.player.SmbMediaUri
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.ThumbnailRepository
import com.seance.app.data.repository.WatchProgressRepository
import com.seance.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    val playbackSpeed: Float = 1f,
    val thumbnailFrames: ThumbnailFrames? = null,
    val videoAspectRatio: Float = 16f / 9f,
    val sharpenEnabled: Boolean = false,
    val seekDurationMs: Long = 10_000L
)

@OptIn(UnstableApi::class)
class PlayerViewModel(
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val thumbnailRepository: ThumbnailRepository,
    private val settingsRepository: SettingsRepository,
    dataSourceFactory: SmbDataSourceFactory,
    private val downloadRepository: DownloadRepository,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        // EXTENSION_RENDERER_MODE_ON: falls back to the FFmpeg extension (DTS/AC3/TrueHD) only when no
        // platform decoder handles the format - a no-op today since the extension isn't on the classpath
        // yet (see scripts/build_ffmpeg_extension.sh), but no code change will be needed once it is.
        DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    )
        // DefaultDataSource routes file:// URIs (a completed offline download) to Media3's built-in
        // FileDataSource and everything else to dataSourceFactory - so smb-item:// keeps streaming
        // over SMB exactly as before, with no branching needed at the MediaItem-building call site.
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context).setDataSourceFactory(DefaultDataSource.Factory(context, dataSourceFactory))
        )
        .setLoadControl(buildAdaptiveLoadControl(context))
        .build()
        .apply { setWakeMode(C.WAKE_MODE_NETWORK) }

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var currentItem: MediaItemEntity? = null
    private var nextEpisode: MediaItemEntity? = null
    private var tickerJob: Job? = null
    private var lastSavedAtMs = 0L

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startTicker() else stopTicker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
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
            settingsRepository.sharpenEnabled.collect { enabled ->
                if (enabled) {
                    effectsPipelineTainted = true
                    player.setVideoEffects(listOf(SharpenEffect()))
                } else if (effectsPipelineTainted) {
                    player.setVideoEffects(emptyList())
                }
                _state.update { it.copy(sharpenEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            settingsRepository.seekDurationSeconds.collect { seconds ->
                _state.update { it.copy(seekDurationMs = seconds * 1000L) }
            }
        }
    }

    fun setSharpenEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSharpenEnabled(enabled) }
    }

    fun load(stableId: String) {
        viewModelScope.launch {
            val item = libraryRepository.getById(stableId) ?: run {
                _state.update { it.copy(error = "Файл не найден в библиотеке", isLoading = false) }
                return@launch
            }
            currentItem = item
            val resume = watchProgressRepository.getProgress(stableId)
            val startPositionMs = resume?.takeIf { !it.watched }?.positionMs ?: 0L

            val download = completedDownload(stableId)
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
            player.playWhenReady = true

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
                    subtitlesEnabled = it.subtitlesEnabled,
                    sharpenEnabled = it.sharpenEnabled,
                    seekDurationMs = it.seekDurationMs
                )
            }

            checkNextEpisode(item)
            launch { loadThumbnailFrames(item.stableId) }
        }
    }

    private suspend fun completedDownload(stableId: String): DownloadEntity? {
        val download = downloadRepository.getForItem(stableId) ?: return null
        if (download.status != DownloadStatus.COMPLETED) return null
        return download.takeIf { DownloadStorage.exists(appContext, Uri.parse(it.contentUri)) }
    }

    private suspend fun loadThumbnailFrames(stableId: String) {
        val frames = withContext(Dispatchers.IO) {
            val sprite = thumbnailRepository.getForItem(stableId) ?: return@withContext null
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

    /** Diagnostic snapshot of the currently rendered video track - used to verify HDR10/DV actually negotiated on-device. */
    fun currentVideoFormatSummary(): String {
        val format = player.videoFormat ?: return "Видеодорожка ещё не определена"
        val color = format.colorInfo
        val isDolbyVision = format.codecs?.let {
            it.startsWith("dvhe") || it.startsWith("dvh1") || it.startsWith("dva1") || it.startsWith("dvav")
        } == true
        val dynamicRange = when {
            isDolbyVision -> "Dolby Vision"
            color?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10/HDR10+"
            color?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
            else -> "SDR"
        }
        return buildString {
            appendLine("Кодек: ${format.sampleMimeType ?: "—"} (${format.codecs ?: "—"})")
            appendLine("Разрешение: ${format.width}x${format.height}")
            appendLine("Динамический диапазон: $dynamicRange")
            appendLine("Цвет: пространство=${color?.colorSpace ?: "—"}, transfer=${color?.colorTransfer ?: "—"}, range=${color?.colorRange ?: "—"}")
        }
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        currentItem?.let { load(it.stableId) }
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
        val mimeType = if (extension == "ass") MimeTypes.TEXT_SSA else MimeTypes.APPLICATION_SUBRIP
        val fileName = remotePath.substringAfterLast('\\')
        val language = guessLanguage(fileName)
        return MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLanguage(language)
            .setLabel(language ?: fileName)
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
                        durationMs = duration,
                        bufferedPositionMs = buffered,
                        showSkipIntro = isWithinIntro(position)
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
        val item = currentItem
        if (item != null) {
            val position = player.currentPosition.coerceAtLeast(0)
            val duration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L
            val watched = duration > 0 && position >= duration - 5000
            runBlocking {
                watchProgressRepository.updateProgress(item.stableId, position, duration, watched, System.currentTimeMillis())
            }
        }
        player.release()
    }

    companion object {
        fun factory(
            libraryRepository: LibraryRepository,
            watchProgressRepository: WatchProgressRepository,
            thumbnailRepository: ThumbnailRepository,
            settingsRepository: SettingsRepository,
            dataSourceFactory: SmbDataSourceFactory,
            downloadRepository: DownloadRepository,
            context: Context
        ) = viewModelFactory {
            initializer {
                PlayerViewModel(
                    libraryRepository,
                    watchProgressRepository,
                    thumbnailRepository,
                    settingsRepository,
                    dataSourceFactory,
                    downloadRepository,
                    context.applicationContext
                )
            }
        }
    }
}
