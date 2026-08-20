package com.seance.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Rational
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.seance.app.R
import com.seance.app.data.player.SmbDataSourceFactory
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.ThumbnailRepository
import com.seance.app.data.repository.WatchProgressRepository
import com.seance.app.ui.common.focusHighlight
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME }

/** Brightness/volume drags only trigger within this fraction of the screen width from each edge -
 * the middle stays neutral so it doesn't fight with taps/drags meant for the center controls. */
private const val EDGE_ZONE_FRACTION = 0.3f

@Composable
fun PlayerScreen(
    stableId: String,
    isTrailer: Boolean = false,
    libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository,
    thumbnailRepository: ThumbnailRepository,
    settingsRepository: com.seance.app.data.settings.SettingsRepository,
    smbDataSourceFactory: SmbDataSourceFactory,
    downloadRepository: DownloadRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(
            libraryRepository,
            watchProgressRepository,
            thumbnailRepository,
            settingsRepository,
            smbDataSourceFactory,
            downloadRepository,
            context
        )
    )
    LaunchedEffect(stableId, isTrailer) { viewModel.load(stableId, playTrailer = isTrailer) }
    val uiState by viewModel.state.collectAsState()

    KeepImmersiveFullscreen()
    KeepScreenOn()

    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var resizeModeLabel by remember { mutableStateOf<String?>(null) }
    var showAspectRatioBlockedDialog by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val fitLabel = stringResource(R.string.player_aspect_ratio_fit)
    val zoomLabel = stringResource(R.string.player_aspect_ratio_zoom)
    val fillLabel = stringResource(R.string.player_aspect_ratio_fill)
    fun cycleResizeMode() {
        // Cycling the mode itself is harmless even when tainted (it just never has a visible
        // effect - see PlayerViewModel.init), but showing the normal Fit/Zoom/Fill label instead
        // of an explanation would look like the tap silently did nothing.
        if (uiState.aspectRatioLockedBySharpen) {
            showAspectRatioBlockedDialog = true
            return
        }
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        resizeModeLabel = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> fitLabel
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> zoomLabel
            else -> fillLabel
        }
    }
    if (showAspectRatioBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showAspectRatioBlockedDialog = false },
            title = { Text(stringResource(R.string.player_aspect_ratio_blocked_title)) },
            text = { Text(stringResource(R.string.player_aspect_ratio_blocked_message)) },
            confirmButton = {
                val reloadSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        showAspectRatioBlockedDialog = false
                        onBack()
                    },
                    interactionSource = reloadSource,
                    modifier = Modifier.focusHighlight(reloadSource)
                ) { Text(stringResource(R.string.player_aspect_ratio_blocked_reload)) }
            },
            dismissButton = {
                val closeSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showAspectRatioBlockedDialog = false },
                    interactionSource = closeSource,
                    modifier = Modifier.focusHighlight(closeSource)
                ) { Text(stringResource(R.string.player_close)) }
            }
        )
    }
    LaunchedEffect(resizeModeLabel) {
        if (resizeModeLabel != null) {
            delay(800)
            resizeModeLabel = null
        }
    }

    // AspectRatioFrameLayout's own measure pass usually re-scales the video surface on rotation,
    // but when playback is paused (no new decoder frames arriving) that relayout can be missed -
    // force one explicitly so a paused frame isn't left stretched to the pre-rotation size.
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.orientation) {
        playerViewRef?.requestLayout()
    }

    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && uiState.isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    DisposableEffect(Unit) {
        PipController.isPlayerActive = true
        onDispose { PipController.isPlayerActive = false }
    }
    LaunchedEffect(uiState.videoAspectRatio) {
        val ratio = uiState.videoAspectRatio
        if (ratio > 0f) {
            // PictureInPictureParams requires the ratio to stay within [1/2.39, 2.39].
            val clamped = ratio.coerceIn(1f / 2.39f, 2.39f)
            PipController.aspectRatio = Rational((clamped * 1000).toInt(), 1000)
        }
    }
    val isInPip = PipController.isInPipMode

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false }.also { playerViewRef = it } },
            update = { view ->
                view.player = viewModel.player
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isInPip) {
            GestureLayer(
                enabled = !isLocked,
                seekDurationMs = uiState.seekDurationMs,
                currentPositionMs = uiState.currentPositionMs,
                onSingleTap = { controlsVisible = !controlsVisible },
                onDoubleTapSeek = viewModel::seekBy,
                onSeekByCommit = viewModel::seekBy,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!isInPip) {
            resizeModeLabel?.let { label ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LabelToast(label, modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        // Centered on the CenterTransportControls play/pause button below when controls are
        // visible, since that Box's center (excluding the top/bottom bars' height) isn't the
        // same point as the screen's true center - the gap between the two is small in portrait
        // but very noticeable in landscape, where the bars eat a much bigger share of the
        // available height. Only falls back to true screen center when there's no play button
        // shown to line up with.
        if (uiState.isLoading && uiState.error == null && (isInPip || !controlsVisible || isLocked)) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        if (!isInPip) {
            AnimatedVisibility(
                visible = uiState.showSkipIntro,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                SkipIntroBanner(onSkip = viewModel::skipIntro)
            }

            if (isLocked) {
                LockedOverlay(onUnlock = { isLocked = false; controlsVisible = true })
            }
            AnimatedVisibility(
                visible = controlsVisible && !isLocked,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    TopGradientBar(
                        title = uiState.title,
                        episodeLabel = uiState.episodeLabel,
                        onBack = onBack,
                        onOpenSubtitles = { showSubtitleDialog = true },
                        onOpenAudioTracks = { showAudioDialog = true },
                        onCycleAspectRatio = { cycleResizeMode() },
                        onOpenSettings = { showSpeedDialog = true }
                    )
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        if (uiState.isLoading && uiState.error == null && !isLocked) {
                            CircularProgressIndicator(color = Color.White)
                        }
                        CenterTransportControls(
                            isPlaying = uiState.isPlaying,
                            onTogglePlayPause = viewModel::togglePlayPause
                        )
                    }
                    BottomGradientBar(
                        currentPositionMs = uiState.currentPositionMs,
                        durationMs = uiState.durationMs,
                        bufferedPositionMs = uiState.bufferedPositionMs,
                        thumbnailFrames = uiState.thumbnailFrames,
                        hasNextEpisode = uiState.hasNextEpisode,
                        isLocked = isLocked,
                        onSeekTo = viewModel::seekTo,
                        onNextEpisode = viewModel::playNext,
                        onToggleLock = { isLocked = true; controlsVisible = false }
                    )
                }
            }

            uiState.error?.let { message ->
                ErrorOverlay(message = message, onRetry = viewModel::retry)
            }
        }
    }

    if (showAudioDialog && !isInPip) {
        TrackSelectionDialog(
            title = stringResource(R.string.player_audio_tracks_title),
            tracks = uiState.audioTracks,
            allowOff = false,
            onSelect = { option -> option?.let(viewModel::selectAudioTrack); showAudioDialog = false },
            onDismiss = { showAudioDialog = false }
        )
    }
    if (showSubtitleDialog && !isInPip) {
        TrackSelectionDialog(
            title = stringResource(R.string.player_subtitles_title),
            tracks = uiState.subtitleTracks,
            allowOff = true,
            onSelect = { option -> viewModel.selectSubtitleTrack(option); showSubtitleDialog = false },
            onDismiss = { showSubtitleDialog = false }
        )
    }
    if (showSpeedDialog && !isInPip) {
        PlaybackSpeedDialog(
            currentSpeed = uiState.playbackSpeed,
            videoFormatSummary = viewModel.currentVideoFormatSummary(),
            sharpenEnabled = uiState.sharpenEnabled,
            onSharpenEnabledChange = viewModel::setSharpenEnabled,
            canMarkIntro = uiState.canMarkIntro,
            introMarkedEndMs = uiState.introMarkedEndMs,
            onMarkIntroEnd = { viewModel.markIntroEnd(); showSpeedDialog = false },
            onClearIntroMarkers = { viewModel.clearIntroMarkers(); showSpeedDialog = false },
            onSelect = { speed -> viewModel.setPlaybackSpeed(speed); showSpeedDialog = false },
            onDismiss = { showSpeedDialog = false }
        )
    }
}

@Composable
private fun LockedOverlay(onUnlock: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        val unlockSource = remember { MutableInteractionSource() }
        IconButton(
            onClick = onUnlock,
            interactionSource = unlockSource,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).focusHighlight(unlockSource, color = Color.White)
        ) {
            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.player_unlock), tint = Color.White)
        }
    }
}

@Composable
private fun KeepImmersiveFullscreen() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun GestureLayer(
    enabled: Boolean,
    seekDurationMs: Long,
    currentPositionMs: Long,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (Long) -> Unit,
    onSeekByCommit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled) {
        Box(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val scope = rememberCoroutineScope()

    var showVolume by remember { mutableStateOf(false) }
    var showBrightness by remember { mutableStateOf(false) }
    var volumeFraction by remember { mutableFloatStateOf(0f) }
    var brightnessFraction by remember { mutableFloatStateOf(0.5f) }
    var volumeHideJob: Job? by remember { mutableStateOf<Job?>(null) }
    var brightnessHideJob: Job? by remember { mutableStateOf<Job?>(null) }

    fun pulseVolume() {
        showVolume = true
        volumeHideJob?.cancel()
        volumeHideJob = scope.launch {
            delay(800)
            showVolume = false
        }
    }

    fun pulseBrightness() {
        showBrightness = true
        brightnessHideJob?.cancel()
        brightnessHideJob = scope.launch {
            delay(800)
            showBrightness = false
        }
    }

    var seekToastText by remember { mutableStateOf<String?>(null) }
    var seekToastAlignment by remember { mutableStateOf(Alignment.Center) }
    var seekToastHideJob: Job? by remember { mutableStateOf<Job?>(null) }

    fun showSeekToast(text: String, alignment: Alignment, autoHideMs: Long?) {
        seekToastText = text
        seekToastAlignment = alignment
        seekToastHideJob?.cancel()
        seekToastHideJob = if (autoHideMs != null) {
            scope.launch {
                delay(autoHideMs)
                seekToastText = null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { offset ->
                        val forward = offset.x >= size.width / 2f
                        onDoubleTapSeek(if (forward) seekDurationMs else -seekDurationMs)
                        val seconds = seekDurationMs / 1000
                        showSeekToast(
                            text = "${if (forward) "+" else "-"}$seconds сек",
                            alignment = if (forward) Alignment.CenterEnd else Alignment.CenterStart,
                            autoHideMs = 600
                        )
                    }
                )
            }
            .pointerInput(activity) {
                var mode = DragMode.NONE
                var startX = 0f
                var accumulatedDx = 0f
                var accumulatedDy = 0f
                var dragStartVolume = 0
                var dragStartBrightness = 0f
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                detectDragGestures(
                    onDragStart = { offset ->
                        mode = DragMode.NONE
                        startX = offset.x
                        accumulatedDx = 0f
                        accumulatedDy = 0f
                        dragStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        dragStartBrightness = activity?.let { currentBrightness(it) } ?: 0.5f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDx += dragAmount.x
                        accumulatedDy += dragAmount.y
                        if (mode == DragMode.NONE) {
                            if (abs(accumulatedDx) > 24f || abs(accumulatedDy) > 24f) {
                                val edgeZone = size.width * EDGE_ZONE_FRACTION
                                mode = if (abs(accumulatedDx) > abs(accumulatedDy)) {
                                    DragMode.SEEK
                                } else if (startX <= edgeZone) {
                                    DragMode.BRIGHTNESS
                                } else if (startX >= size.width - edgeZone) {
                                    DragMode.VOLUME
                                } else {
                                    // Middle of the screen - not an edge zone, so a vertical drag here
                                    // adjusts neither brightness nor volume (avoids accidental changes
                                    // from taps/drags meant for the center controls).
                                    DragMode.NONE
                                }
                            }
                        }
                        when (mode) {
                            DragMode.VOLUME -> {
                                val fraction = -accumulatedDy / size.height
                                val newVolume = (dragStartVolume + fraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                volumeFraction = if (maxVolume > 0) newVolume.toFloat() / maxVolume else 0f
                                pulseVolume()
                            }
                            DragMode.BRIGHTNESS -> {
                                val fraction = -accumulatedDy / size.height
                                val newBrightness = (dragStartBrightness + fraction).coerceIn(0.02f, 1f)
                                activity?.let { setBrightness(it, newBrightness) }
                                brightnessFraction = newBrightness
                                pulseBrightness()
                            }
                            DragMode.SEEK -> {
                                val deltaMs = (accumulatedDx / size.width * 120_000f).toLong()
                                val targetMs = (currentPositionMs + deltaMs).coerceAtLeast(0)
                                val sign = if (deltaMs >= 0) "+" else "-"
                                showSeekToast(
                                    text = "${formatTime(targetMs)}  $sign${formatTime(abs(deltaMs))}",
                                    alignment = Alignment.Center,
                                    autoHideMs = null
                                )
                            }
                            DragMode.NONE -> Unit
                        }
                    },
                    onDragEnd = {
                        if (mode == DragMode.SEEK) {
                            val deltaMs = (accumulatedDx / size.width * 120_000f).toLong()
                            onSeekByCommit(deltaMs)
                            showSeekToast(seekToastText.orEmpty(), Alignment.Center, autoHideMs = 400)
                        }
                        mode = DragMode.NONE
                    }
                )
            }
    ) {
        if (showBrightness) {
            GestureIndicator(
                label = "Яркость",
                fraction = brightnessFraction,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            )
        }
        if (showVolume) {
            GestureIndicator(
                label = "Громкость",
                fraction = volumeFraction,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
            )
        }
        seekToastText?.let { text ->
            LabelToast(
                text,
                modifier = Modifier
                    .align(seekToastAlignment)
                    .padding(horizontal = 96.dp)
            )
        }
    }
}

@Composable
private fun LabelToast(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.White)
    }
}

/** A vertical capsule HUD for volume/brightness, positioned at a screen edge so the two never overlap. */
@Composable
private fun GestureIndicator(label: String, fraction: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall
        )
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(96.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.75f))
            )
        }
        Text(
            text = "${(fraction * 100).roundToInt()}%",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun currentBrightness(activity: Activity): Float {
    val value = activity.window.attributes.screenBrightness
    return if (value in 0f..1f) value else 0.5f
}

private fun setBrightness(activity: Activity, value: Float) {
    val attrs = activity.window.attributes
    attrs.screenBrightness = value
    activity.window.attributes = attrs
}
