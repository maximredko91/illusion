package com.illusion.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.compose.ui.graphics.toArgb
import com.illusion.app.R
import com.illusion.app.data.player.SmbDataSourceFactory
import com.illusion.app.data.repository.DownloadRepository
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.ThumbnailRepository
import com.illusion.app.data.repository.WatchProgressRepository
import com.illusion.app.ui.common.focusHighlight
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME }

/** Brightness/volume drags only trigger within this fraction of the screen width from each edge -
 * the middle stays neutral so it doesn't fight with taps/drags meant for the center controls. */
private const val EDGE_ZONE_FRACTION = 0.3f

/** Brightness/volume drags reach their full 0-100% swing over this fraction of the screen height,
 * not the full height - dividing by the whole screen height (tried first) meant covering the full
 * range required dragging your thumb literally up to the physical top/bottom edge of the display,
 * which is awkward to reach and easy to have swallowed by system gesture areas up there. Scaling
 * against a shorter effective range makes the same 0-100% swing reachable from a comfortable
 * middle stretch of the screen instead. */
private const val VERTICAL_GESTURE_RANGE_FRACTION = 0.55f

/** No tap/double-tap/hold/drag gesture starts within this distance of either screen edge - matches
 * the same reasoning as Details' fanart hit-region fix (a touch that close to the bezel is an easy
 * accidental hit, and it's also where the OS's own edge-swipe-back gesture lives). */
private val EDGE_DEAD_ZONE = 32.dp

/** How often a held press re-fires the seek step while holdToSeek is active. */
private const val HOLD_SEEK_INTERVAL_MS = 400L

/** How long a press must be held before hold-to-seek arms and starts stepping - the platform's
 * own [androidx.compose.ui.platform.ViewConfiguration.longPressTimeoutMillis] (~500ms) was used
 * before, but that's tuned for a generic long-press, not specifically for "the user actually
 * wants to hold-seek rather than just pausing their tap a beat longer than usual" - per feedback,
 * a longer, more deliberate hold reads as more intentional and cuts down on accidental triggers. */
private const val HOLD_SEEK_ARM_DELAY_MS = 900L

@Composable
fun PlayerScreen(
    stableId: String,
    isTrailer: Boolean = false,
    libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository,
    thumbnailRepository: ThumbnailRepository,
    generateThumbnailIfMissing: (com.illusion.app.data.local.entity.MediaItemEntity) -> Unit,
    persistFinalWatchProgress: (stableId: String, positionMs: Long, durationMs: Long, watched: Boolean, updatedAtMs: Long) -> Unit,
    settingsRepository: com.illusion.app.data.settings.SettingsRepository,
    smbDataSourceFactory: SmbDataSourceFactory,
    downloadRepository: DownloadRepository,
    smbSourceRepository: com.illusion.app.data.repository.SmbSourceRepository,
    credentialStore: com.illusion.app.data.smb.SmbCredentialStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(
            libraryRepository,
            watchProgressRepository,
            thumbnailRepository,
            generateThumbnailIfMissing,
            persistFinalWatchProgress,
            settingsRepository,
            smbDataSourceFactory,
            downloadRepository,
            smbSourceRepository,
            credentialStore,
            context
        )
    )
    LaunchedEffect(stableId, isTrailer) { viewModel.load(stableId, playTrailer = isTrailer) }
    val uiState by viewModel.state.collectAsState()
    // Collected (not read as a plain viewModel.player property access) so this composition
    // actually recomposes when reloadPlayer() swaps in a fresh ExoPlayer instance - a plain
    // property read wouldn't be observed by Compose's snapshot system.
    val currentPlayer by viewModel.playerState.collectAsState()
    val noExternalAppMessage = stringResource(R.string.player_open_external_no_app)

    // Settings' "external player" choice (see SettingsRepository.playerMode) is applied once by
    // PlayerViewModel.load() itself - this screen's only job when that fires is to hand the OS the
    // intent and get out of the way, since there's no internal playback to show for this session.
    LaunchedEffect(Unit) {
        viewModel.launchExternalPlayer.collect { intent ->
            runCatching { context.startActivity(intent) }.onFailure {
                android.widget.Toast.makeText(context, noExternalAppMessage, android.widget.Toast.LENGTH_SHORT).show()
            }
            onBack()
        }
    }

    KeepImmersiveFullscreen()
    KeepScreenOn()

    var controlsVisible by remember { mutableStateOf(true) }
    // TV D-pad had no way to bring the controls back once they auto-hid: everything here
    // (GestureLayer's onSingleTap toggle included) was wired purely for touch, so on a real
    // remote - once focus fell off whatever button last had it as the controls disappeared -
    // there was nothing left focused for DPAD_CENTER/Enter to land on at all (confirmed:
    // "повторное нажатие на центр пульта не открывает интерфейс плеера"). This dedicated
    // full-screen focus target sits underneath everything else, stays focusable even with no
    // controls on screen, and re-claims focus itself the moment controlsVisible goes false so a
    // remote press always has somewhere to be delivered.
    val rootFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocusRequester.requestFocus() }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) rootFocusRequester.requestFocus()
    }
    // Revealing the controls put focus back on the root above, not on any of the actual buttons -
    // the root spans the whole screen and geometrically overlaps every button inside it, so
    // Compose's directional (D-pad arrow) focus search from it had no sane "nearest neighbor" to
    // land on and just stayed put (confirmed on-device: "кнопки в плеере нельзя выбрать, только
    // плеер по нажатию появляется"). Explicitly moving focus onto the play/pause button - the
    // most central, always-reachable control - the moment the controls become visible fixes this;
    // from there D-pad arrows can navigate normally between real sibling buttons instead of never
    // leaving the oversized root.
    val controlsFocusRequester = remember { FocusRequester() }
    LaunchedEffect(controlsVisible, uiState.isLoading) {
        if (controlsVisible && !uiState.isLoading) {
            runCatching { controlsFocusRequester.requestFocus() }
        }
    }
    var isLocked by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    // There was no BackHandler here at all - the system/remote Back button always fell straight
    // through to onBack() (exiting the player entirely) regardless of what was open on top,
    // confirmed on-device: pressing Back to close the settings panel or just hide the controls
    // instead kicked all the way out to the Details screen. Closes whichever's open first (panel/
    // dialog takes priority over merely hiding the controls); only when NOTHING is showing does
    // Back fall through to the natural nav-pop below.
    androidx.activity.compose.BackHandler(
        enabled = showSpeedDialog || showSubtitleDialog || showAudioDialog || controlsVisible
    ) {
        when {
            showSpeedDialog -> showSpeedDialog = false
            showSubtitleDialog -> showSubtitleDialog = false
            showAudioDialog -> showAudioDialog = false
            controlsVisible -> controlsVisible = false
        }
    }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var resizeModeLabel by remember { mutableStateOf<String?>(null) }
    var sharpenToggleLabel by remember { mutableStateOf<String?>(null) }
    var showAspectRatioBlockedDialog by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    // The seek-drag toast (GestureLayer, a full-screen sibling of this Column) used to center on
    // the TRUE screen center via its own root Box, while CenterTransportControls below centers on
    // the narrower area BETWEEN the top/bottom bars - two different points whenever the bars are
    // showing, most visible in landscape where they eat a much bigger share of the height (real
    // report: the dark seek-time backdrop sat visibly above the play/pause icon it should line up
    // with). Measuring both bars' real heights lets GestureLayer apply the same offset
    // CenterTransportControls effectively already gets "for free" from being nested between them.
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

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
                        viewModel.reloadPlayer()
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
    val sharpenOnLabel = stringResource(R.string.player_sharpen_on_label)
    val sharpenOffLabel = stringResource(R.string.player_sharpen_off_label)
    LaunchedEffect(sharpenToggleLabel) {
        if (sharpenToggleLabel != null) {
            delay(800)
            sharpenToggleLabel = null
        }
    }

    // AspectRatioFrameLayout's own measure pass usually re-scales the video surface on rotation,
    // but when playback is paused (no new decoder frames arriving) that relayout can be missed -
    // a bare requestLayout() alone wasn't enough (confirmed on-device: repeated rotation could
    // leave the video rendered as a small rectangle, or briefly black). AspectRatioFrameLayout's
    // own aspect ratio field is only ever set from a Player.Listener's onVideoSizeChanged
    // callback, not from a plain View relayout - requestLayout() re-measures with whatever
    // aspect ratio it already has, which can be stale/wrong after several rotations in a row.
    // Detaching and reattaching the player forces PlayerView to re-bind its video output to the
    // surface from scratch, which re-fires that video-size callback and rebuilds the correct
    // aspect ratio and surface size together instead of trusting stale state.
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.orientation) {
        playerViewRef?.let { view ->
            val boundPlayer = view.player
            view.player = null
            view.player = boundPlayer
        }
        playerViewRef?.requestLayout()
    }

    // Any interaction with the controls themselves (dragging the seek bar, tapping subtitle/
    // audio/speed icons, ...) needs to restart this countdown - previously it only keyed on
    // controlsVisible/isPlaying, neither of which change for most control interactions, so the
    // controls could fade out mid-interaction (e.g. while still dragging the seek bar).
    // interactionTick is bumped from each control's own callback below (see bumpInteraction) -
    // a full-screen non-consuming pointerInput sibling was tried first and dropped, since it sat
    // in front of GestureLayer in the same Box and silently broke its brightness/volume/seek
    // swipe gestures even though it never called .consume() itself.
    var interactionTick by remember { mutableIntStateOf(0) }
    fun bumpInteraction() { interactionTick++ }
    LaunchedEffect(controlsVisible, uiState.isPlaying, interactionTick) {
        if (controlsVisible && uiState.isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    // The unlock icon itself used to have no auto-hide at all - once locked, it sat on screen
    // permanently for as long as playback stayed locked (confirmed on-device: "замочек не
    // пропадает"), unlike every other piece of player chrome, which fades after a few seconds of
    // no interaction. Mirrors the controlsVisible countdown above - fades out the same way, and a
    // tap anywhere on the locked screen (LockedOverlay's own tap-catcher below) brings it back.
    var lockIconVisible by remember { mutableStateOf(true) }
    LaunchedEffect(isLocked, lockIconVisible) {
        if (isLocked && lockIconVisible) {
            delay(3500)
            lockIconVisible = false
        }
    }

    // Gated on readyForInternalPlayback (not unconditional on entering this screen) - otherwise
    // PlayerMode.EXTERNAL's brief async hand-off window flagged isPlayerActive = true too, and
    // backgrounding the app to launch the external app's Intent made onUserLeaveHint think a real
    // internal playback session was in progress and enter PiP for a player showing nothing.
    LaunchedEffect(uiState.readyForInternalPlayback) {
        if (uiState.readyForInternalPlayback) {
            PipController.isPlayerActive = true
            // See PipController.onPipClosed's own KDoc - some OEM skins don't finish() the activity
            // when the PiP window's close button is tapped, so this is the fallback that actually
            // stops playback in that case instead of leaving audio running with nothing visible.
            PipController.onPipClosed = { viewModel.player.pause() }
            // See PipController.onBackgroundedWithoutPip's own KDoc - the same "never leave audio
            // running with nothing visible" principle, for the case where PiP never actually
            // started in the first place.
            PipController.onBackgroundedWithoutPip = { viewModel.player.pause() }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            PipController.isPlayerActive = false
            PipController.onPipClosed = null
            PipController.onBackgroundedWithoutPip = null
        }
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
            // Any D-pad activity while the controls are visible counts as "still interacting",
            // same as bumpInteraction() below for actual clicks - previously the 3.5s auto-hide
            // countdown only reset on a real onClick, so simply moving focus around with the
            // remote (deciding what to press, without pressing OK yet) did nothing to it, and the
            // controls could vanish mid-navigation (confirmed on-device: "когда переключаюсь
            // пультом в меню, то интерфейс плеера пропадает"). onPreviewKeyEvent (not onKeyEvent)
            // so this observes every key on the way down without consuming it - normal focus-move/
            // click handling still happens exactly as before.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && controlsVisible) bumpInteraction()
                false
            }
            .focusRequester(rootFocusRequester)
            // Only a focus candidate while the controls are hidden - while they're visible, this
            // full-screen node would otherwise sit in the same directional-focus-search pool as
            // every real button inside it (it spatially overlaps all of them), which broke normal
            // D-pad navigation between buttons entirely (confirmed on-device). With nothing else
            // focusable while hidden, it's still the fallback DPAD_CENTER/Enter target that brings
            // the controls back (see the onKeyEvent below and the controlsVisible-keyed
            // LaunchedEffect above).
            .focusable(enabled = !controlsVisible)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    controlsVisible = !controlsVisible
                    true
                } else {
                    false
                }
            }
    ) {
        // Gated on readyForInternalPlayback rather than always rendered: for PlayerMode.EXTERNAL,
        // this whole subtree (video surface, spinner, PiP eligibility below) used to exist for the
        // brief async window before load() hands off to the external app, visibly flashing as if
        // internal playback had started - see PlayerUiState.readyForInternalPlayback's own KDoc.
        if (uiState.readyForInternalPlayback) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false }.also { playerViewRef = it } },
            update = { view ->
                view.player = currentPlayer
                view.resizeMode = resizeMode
                // backgroundAlpha is the per-glyph "подложка" directly behind the text - windowColor
                // (the larger padded box around the whole cue) is left fully transparent since that's
                // not what "opacity" here refers to. edgeType/edgeColor stay Media3's own defaults.
                val backgroundAlpha = (uiState.subtitleBackgroundOpacity * 255 / 100).coerceIn(0, 255)
                val backgroundColor = (backgroundAlpha shl 24) or 0x000000
                view.subtitleView?.setStyle(
                    CaptionStyleCompat(
                        uiState.subtitleTextColor,
                        backgroundColor,
                        Color.Transparent.toArgb(),
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        Color.Black.toArgb(),
                        null
                    )
                )
                view.subtitleView?.setFractionalTextSize(
                    SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (uiState.subtitleTextSizePercent / 100f)
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isInPip) {
            GestureLayer(
                enabled = !isLocked,
                seekDurationMs = uiState.seekDurationMs,
                currentPositionMs = uiState.currentPositionMs,
                doubleTapSeekEnabled = uiState.doubleTapSeekEnabled,
                swipeSeekEnabled = uiState.swipeSeekEnabled,
                holdToSeekEnabled = uiState.holdToSeekEnabled,
                onSingleTap = { controlsVisible = !controlsVisible },
                onDoubleTapSeek = viewModel::seekBy,
                onSeekByCommit = viewModel::seekBy,
                // Only meaningful while the bars (and so CenterTransportControls' own off-true-
                // center position) are actually showing - with controls hidden there's no play/
                // pause icon to line up with, and GestureLayer's own true-screen-center is already
                // correct then.
                centerToastOffsetPx = if (controlsVisible) (topBarHeightPx - bottomBarHeightPx) / 2 else 0,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!isInPip) {
            resizeModeLabel?.let { label ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LabelToast(label, modifier = Modifier.align(Alignment.Center))
                }
            }
            sharpenToggleLabel?.let { label ->
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
            BufferingIndicator(
                bufferedPositionMs = uiState.bufferedPositionMs,
                currentPositionMs = uiState.currentPositionMs,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
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

            AnimatedVisibility(
                visible = uiState.showSkipCredits,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                SkipCreditsBanner(onSkip = viewModel::playNext)
            }

            if (isLocked) {
                LockedOverlay(
                    iconVisible = lockIconVisible,
                    onTap = { lockIconVisible = true },
                    onUnlock = { isLocked = false; controlsVisible = true }
                )
            }
            AnimatedVisibility(
                visible = controlsVisible && !isLocked,
                enter = fadeIn(tween(com.illusion.app.ui.common.economicalDurationMs(300))),
                exit = fadeOut(tween(com.illusion.app.ui.common.economicalDurationMs(300)))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    Box(modifier = Modifier.onSizeChanged { topBarHeightPx = it.height }) {
                    TopGradientBar(
                        title = uiState.title,
                        episodeLabel = uiState.episodeLabel,
                        onBack = onBack,
                        onOpenSubtitles = { bumpInteraction(); showSubtitleDialog = true },
                        onOpenAudioTracks = { bumpInteraction(); showAudioDialog = true },
                        onCycleAspectRatio = { bumpInteraction(); cycleResizeMode() },
                        onOpenSettings = { bumpInteraction(); showSpeedDialog = true },
                        sharpenEnabled = uiState.sharpenEnabled,
                        onToggleSharpen = {
                            bumpInteraction()
                            val nowEnabled = !uiState.sharpenEnabled
                            viewModel.setSharpenEnabled(nowEnabled)
                            sharpenToggleLabel = if (nowEnabled) sharpenOnLabel else sharpenOffLabel
                        },
                        sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
                        onSetSleepTimer = { duration -> bumpInteraction(); viewModel.setSleepTimer(duration) },
                        onCancelSleepTimer = { bumpInteraction(); viewModel.cancelSleepTimer() }
                    )
                    }
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        // Both used to render unconditionally, stacked on the exact same center
                        // point - a bare spinner is thin enough that the overlap with the play
                        // button underneath wasn't obviously broken, but adding buffering% text
                        // right below it made the collision plainly visible on-device (play
                        // triangle and "39%" mashed together). Mutually exclusive now - only one
                        // thing ever occupies this center spot at a time.
                        if (uiState.isLoading && uiState.error == null && !isLocked) {
                            BufferingIndicator(
                                bufferedPositionMs = uiState.bufferedPositionMs,
                                currentPositionMs = uiState.currentPositionMs,
                                color = Color.White
                            )
                        } else {
                            CenterTransportControls(
                                isPlaying = uiState.isPlaying,
                                onTogglePlayPause = { bumpInteraction(); viewModel.togglePlayPause() },
                                modifier = Modifier.focusRequester(controlsFocusRequester)
                            )
                        }
                    }
                    Box(modifier = Modifier.onSizeChanged { bottomBarHeightPx = it.height }) {
                    BottomGradientBar(
                        currentPositionMs = uiState.currentPositionMs,
                        durationMs = uiState.durationMs,
                        bufferedPositionMs = uiState.bufferedPositionMs,
                        thumbnailFrames = uiState.thumbnailFrames,
                        hasNextEpisode = uiState.hasNextEpisode,
                        isLocked = isLocked,
                        onSeekTo = { position -> bumpInteraction(); viewModel.seekTo(position) },
                        onSeekDragging = { bumpInteraction() },
                        onNextEpisode = { bumpInteraction(); viewModel.playNext() },
                        onToggleLock = { bumpInteraction(); isLocked = true; lockIconVisible = true; controlsVisible = false }
                    )
                    }
                }
            }

            uiState.error?.let { message ->
                ErrorOverlay(message = message, onRetry = viewModel::retry)
            }
        }
        }

        if (uiState.awaitingPlayerModeChoice) {
            AlertDialog(
                onDismissRequest = onBack,
                title = { Text(stringResource(R.string.player_mode_ask_title)) },
                confirmButton = {
                    val externalSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { viewModel.choosePlayerMode(external = true) },
                        interactionSource = externalSource,
                        modifier = Modifier.focusHighlight(externalSource)
                    ) { Text(stringResource(R.string.player_mode_ask_external)) }
                },
                dismissButton = {
                    val internalSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { viewModel.choosePlayerMode(external = false) },
                        interactionSource = internalSource,
                        modifier = Modifier.focusHighlight(internalSource)
                    ) { Text(stringResource(R.string.player_mode_ask_internal)) }
                }
            )
        }

        // Stays mounted at all times (not gated behind `if (showSpeedDialog)` like the other
        // dialogs below) so its slide-in/out animation actually has something to animate - see the
        // KDoc on PlayerSettingsPanel itself.
        PlayerSettingsPanel(
            visible = showSpeedDialog && !isInPip,
            currentSpeed = uiState.playbackSpeed,
            videoFormatSummary = viewModel.currentVideoFormatSummary(),
            sharpenEnabled = uiState.sharpenEnabled,
            onSharpenEnabledChange = viewModel::setSharpenEnabled,
            sharpenAmount = uiState.sharpenAmount,
            onSharpenAmountChange = viewModel::setSharpenAmount,
            onResetSharpenAmount = viewModel::resetSharpenAmount,
            aspectRatioLockedBySharpen = uiState.aspectRatioLockedBySharpen,
            onReloadPlayer = viewModel::reloadPlayer,
            subtitleTextColor = uiState.subtitleTextColor,
            onSubtitleTextColorChange = viewModel::setSubtitleTextColor,
            subtitleBackgroundOpacity = uiState.subtitleBackgroundOpacity,
            onSubtitleBackgroundOpacityChange = viewModel::setSubtitleBackgroundOpacity,
            subtitleTextSizePercent = uiState.subtitleTextSizePercent,
            onSubtitleTextSizePercentChange = viewModel::setSubtitleTextSizePercent,
            onResetSubtitleStyle = viewModel::resetSubtitleStyle,
            seekDurationSeconds = (uiState.seekDurationMs / 1000L).toInt(),
            onSeekDurationSecondsChange = viewModel::setSeekDurationSeconds,
            doubleTapSeekEnabled = uiState.doubleTapSeekEnabled,
            onDoubleTapSeekEnabledChange = viewModel::setDoubleTapSeekEnabled,
            swipeSeekEnabled = uiState.swipeSeekEnabled,
            onSwipeSeekEnabledChange = viewModel::setSwipeSeekEnabled,
            holdToSeekEnabled = uiState.holdToSeekEnabled,
            onHoldToSeekEnabledChange = viewModel::setHoldToSeekEnabled,
            canMarkIntro = uiState.canMarkIntro,
            introMarkedEndMs = uiState.introMarkedEndMs,
            onMarkIntroEnd = { viewModel.markIntroEnd(); showSpeedDialog = false },
            onClearIntroMarkers = { viewModel.clearIntroMarkers(); showSpeedDialog = false },
            canMarkCredits = uiState.canMarkCredits,
            outroMarkedStartMs = uiState.outroMarkedStartMs,
            onMarkCreditsStart = { viewModel.markCreditsStart(); showSpeedDialog = false },
            onClearOutroMarker = { viewModel.clearOutroMarker(); showSpeedDialog = false },
            onSelect = { speed -> viewModel.setPlaybackSpeed(speed); showSpeedDialog = false },
            onDismiss = { showSpeedDialog = false }
        )
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
}

/** How far ahead of the current position ExoPlayer needs to have buffered before 100% reads as
 * "basically ready" - a display-only approximation (not read from [buildAdaptiveLoadControl]'s
 * actual per-connection profile, which ranges 2-8s for the after-rebuffer threshold depending on
 * estimated bandwidth) rather than the literal readiness threshold. Precision doesn't matter here,
 * only that it's measured from the current/seek position, not absolute file position. */
private const val BUFFERING_DISPLAY_TARGET_MS = 8_000L

/** Plain spinner used to just sit there indefinitely with zero feedback on how far along the
 * initial buffer actually was - indistinguishable from a genuine hang (reported on-device for a
 * large 4K file that turned out to be stuck, but nothing on screen could tell the user that vs.
 * "just slow"). Percent is buffered-ahead-of-current-position, not bufferedPositionMs/durationMs -
 * the latter is absolute position in the FILE, so right after a seek it just echoes back
 * wherever the user tapped on the seek bar (bufferedPosition briefly resets to ~= the seek
 * target) instead of showing real buffering progress from that point, which is exactly what was
 * reported on-device. */
@Composable
private fun BufferingIndicator(bufferedPositionMs: Long, currentPositionMs: Long, color: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        CircularProgressIndicator(color = color)
        Text(
            stringResource(R.string.player_buffering),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        val bufferedAheadMs = (bufferedPositionMs - currentPositionMs).coerceAtLeast(0)
        if (bufferedAheadMs > 0) {
            val percent = ((bufferedAheadMs.toFloat() / BUFFERING_DISPLAY_TARGET_MS) * 100).toInt().coerceIn(0, 100)
            Text("$percent%", color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun LockedOverlay(iconVisible: Boolean, onTap: () -> Unit, onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // The screen is otherwise fully inert while locked (GestureLayer itself is disabled -
            // that's the whole point of locking), so this is the only way to bring a
            // faded-out unlock icon back once its own auto-hide timer clears it.
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTap() }
    ) {
        AnimatedVisibility(
            visible = iconVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            val unlockSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onUnlock,
                interactionSource = unlockSource,
                modifier = Modifier.padding(24.dp).focusHighlight(unlockSource, color = Color.White)
            ) {
                Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.player_unlock), tint = Color.White)
            }
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
    doubleTapSeekEnabled: Boolean,
    swipeSeekEnabled: Boolean,
    holdToSeekEnabled: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (Long) -> Unit,
    onSeekByCommit: (Long) -> Unit,
    centerToastOffsetPx: Int = 0,
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
    // The pointerInput block below is keyed on (activity, swipeSeekEnabled), not on every
    // recomposition, so a plain closure over the currentPositionMs parameter would freeze it at
    // whatever value it had when that gesture coroutine last (re)started - effectively once per
    // player session. The actual seek this commits is a relative delta against the live player
    // position (onSeekByCommit), so it still lands correctly - but the absolute target time shown
    // in the drag toast drifted further from reality the longer playback ran before the swipe.
    // rememberUpdatedState keeps the read inside the gesture block always current.
    val latestPositionMs by rememberUpdatedState(currentPositionMs)

    var showVolume by remember { mutableStateOf(false) }
    var showBrightness by remember { mutableStateOf(false) }
    var volumeFraction by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        )
    }
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

    // Landscape on some devices has a real display-cutout (front camera) on one side - the same
    // mirror-both-sides approach DetailsScreen already uses for its own safe-area padding, so the
    // brightness/volume pills stay visually centered regardless of which physical edge the cutout
    // is actually on. Zero in portrait.
    val cutoutInsets = WindowInsets.displayCutout
    val gestureIndicatorDensity = androidx.compose.ui.platform.LocalDensity.current
    val gestureIndicatorLayoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val cutoutHorizontalDp = with(gestureIndicatorDensity) {
        maxOf(
            cutoutInsets.getLeft(gestureIndicatorDensity, gestureIndicatorLayoutDirection),
            cutoutInsets.getRight(gestureIndicatorDensity, gestureIndicatorLayoutDirection)
        ).toDp()
    }
    // 24dp was flush enough against the raw screen edge to feel cramped there, doubly so once a
    // cutout is added on top - 40dp base gives the pill some breathing room even with no cutout.
    val gestureIndicatorEdgePadding = 40.dp + cutoutHorizontalDp

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

    Box(modifier = modifier) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Excludes a strip at each edge from every gesture below (tap/double-tap/hold/drag) -
            // same idea as Details' fanart hit-region fix: a swipe that starts right at the physical
            // bezel is easy to land by accident (and competes with the OS's own edge-swipe-back
            // gesture there), so nothing in this player should react to a touch that starts that
            // close to the edge.
            // Vertical too, not just horizontal - a swipe down from the very top edge is meant to
            // open the system notification shade, but without this the vertical brightness/volume
            // drag detector (active in the left/right EDGE_ZONE_FRACTION thirds) claimed it first
            // since the shade's own gesture-recognition strip is thin and this Box sat right under it.
            .padding(horizontal = EDGE_DEAD_ZONE, vertical = EDGE_DEAD_ZONE)
            .pointerInput(Unit, doubleTapSeekEnabled, holdToSeekEnabled) {
                detectTapGestures(
                    onPress = { offset ->
                        if (!holdToSeekEnabled) return@detectTapGestures
                        val forward = offset.x >= size.width / 2f
                        var holdTickJob: Job? = null
                        val armJob = scope.launch {
                            delay(HOLD_SEEK_ARM_DELAY_MS)
                            holdTickJob = scope.launch {
                                while (isActive) {
                                    onDoubleTapSeek(if (forward) seekDurationMs else -seekDurationMs)
                                    val seconds = seekDurationMs / 1000
                                    showSeekToast(
                                        text = "${if (forward) "+" else "-"}$seconds сек",
                                        alignment = if (forward) Alignment.CenterEnd else Alignment.CenterStart,
                                        autoHideMs = HOLD_SEEK_INTERVAL_MS + 150
                                    )
                                    delay(HOLD_SEEK_INTERVAL_MS)
                                }
                            }
                        }
                        tryAwaitRelease()
                        armJob.cancel()
                        holdTickJob?.cancel()
                    },
                    // No-op, not omitted - passing this makes detectTapGestures treat a long hold as
                    // consumed once past the long-press timeout, instead of still firing onTap the
                    // moment the finger eventually lifts (which would toggle the controls visibility
                    // right as a hold-seek gesture ends).
                    onLongPress = {},
                    onTap = { onSingleTap() },
                    onDoubleTap = { offset ->
                        if (!doubleTapSeekEnabled) return@detectTapGestures
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
            .pointerInput(activity, swipeSeekEnabled) {
                var mode = DragMode.NONE
                var startX = 0f
                var accumulatedDx = 0f
                var accumulatedDy = 0f
                var dragStartVolume = 0f
                var dragStartBrightness = 0f
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                detectDragGestures(
                    onDragStart = { offset ->
                        mode = DragMode.NONE
                        startX = offset.x
                        accumulatedDx = 0f
                        accumulatedDy = 0f
                        dragStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.coerceAtLeast(1)
                        dragStartBrightness = activity?.let { currentBrightness(it) } ?: 0.5f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDx += dragAmount.x
                        accumulatedDy += dragAmount.y
                        if (mode == DragMode.NONE) {
                            if (abs(accumulatedDx) > 24f || abs(accumulatedDy) > 24f) {
                                val edgeZone = size.width * EDGE_ZONE_FRACTION
                                val isHorizontalDrag = abs(accumulatedDx) > abs(accumulatedDy)
                                mode = if (isHorizontalDrag) {
                                    // A horizontal drag is never reinterpreted as brightness/volume
                                    // even when swipe-seek is off - it just does nothing, rather than
                                    // falling through to the vertical-only edge checks below.
                                    if (swipeSeekEnabled) DragMode.SEEK else DragMode.NONE
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
                                // Switched to driving player.volume (ExoPlayer's own software
                                // gain) instead of AudioManager's real STREAM_MUSIC to fix a
                                // jumpy on-screen percentage - but that also silently stopped the
                                // gesture from actually changing the device's audible volume at
                                // all (confirmed on-device: "громкость не увеличивается свайпом",
                                // "только физическими [кнопками]" now worked). player.volume is a
                                // multiplier ON TOP OF the real system volume, not a replacement
                                // for it - swiping to 100% player.volume against a low system
                                // volume just plays at 100% of "quiet".
                                //
                                // Back to driving the real STREAM_MUSIC (so the gesture alone can
                                // reach true max loudness again, matching the hardware rocker),
                                // but the displayed percentage now tracks the finger's own
                                // continuous fraction directly rather than being reconstructed
                                // from the resulting whole hardware step - that's what actually
                                // fixes the original jumpy-number complaint without breaking the
                                // gesture's real effect.
                                val fraction = -accumulatedDy / (size.height * VERTICAL_GESTURE_RANGE_FRACTION)
                                val newVolumeFraction = (dragStartVolume + fraction).coerceIn(0f, 1f)
                                val newVolume = (newVolumeFraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                volumeFraction = newVolumeFraction
                                pulseVolume()
                            }
                            DragMode.BRIGHTNESS -> {
                                val fraction = -accumulatedDy / (size.height * VERTICAL_GESTURE_RANGE_FRACTION)
                                val newBrightness = (dragStartBrightness + fraction).coerceIn(0.02f, 1f)
                                activity?.let { setBrightness(it, newBrightness) }
                                brightnessFraction = newBrightness
                                pulseBrightness()
                            }
                            DragMode.SEEK -> {
                                val deltaMs = (accumulatedDx / size.width * 120_000f).toLong()
                                val targetMs = (latestPositionMs + deltaMs).coerceAtLeast(0)
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
    )

        // Was a hard if/else snap (found by this session's audit) - every other transient overlay
        // on this same screen (controls, skip-intro banner) already fades, so these two stood out
        // as the last instant show/hide left in the player. Attached to the OUTER box (not the
        // edge-inset gesture box above) so they stay aligned to the real screen edges regardless of
        // EDGE_DEAD_ZONE.
        AnimatedVisibility(
            visible = showBrightness,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = gestureIndicatorEdgePadding)
        ) {
            GestureIndicator(label = "Яркость", fraction = brightnessFraction)
        }
        AnimatedVisibility(
            visible = showVolume,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = gestureIndicatorEdgePadding)
        ) {
            GestureIndicator(label = "Громкость", fraction = volumeFraction)
        }
        seekToastText?.let { text ->
            LabelToast(
                text,
                modifier = Modifier
                    .align(seekToastAlignment)
                    .padding(horizontal = 96.dp)
                    // Only the drag-seek toast uses Center (hold/double-tap seek use
                    // CenterStart/CenterEnd, already correctly positioned at the screen edges) -
                    // see centerToastOffsetPx's own KDoc at the call site for why this is needed.
                    .then(
                        if (seekToastAlignment == Alignment.Center && centerToastOffsetPx != 0) {
                            Modifier.offset { IntOffset(0, centerToastOffsetPx) }
                        } else {
                            Modifier
                        }
                    )
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
            style = MaterialTheme.typography.titleSmall
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
            style = MaterialTheme.typography.titleMedium
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
