package com.illusion.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BlurOff
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import com.illusion.app.ui.common.dpadFieldNavigation
import com.illusion.app.ui.common.focusHighlight
import java.util.Locale

/**
 * Every Material3 Slider in this file needs the same D-pad fix (verified via javap on the real
 * material3-1.4.0 jar - SliderKt$slideOnKeyEvents$2 treats DirectionUp/Down exactly like
 * Left/Right, silently changing the value instead of moving focus): Down does nothing (there's
 * always a next row below to reach some other way, and blindly moveFocus(Down) risks landing
 * somewhere unrelated with no way back - see the seek bar's own "перескакивает" report), Up moves
 * focus normally. Applied to every slider in the settings panel (subtitle opacity/text size, seek
 * duration, sharpen amount) as well as the main seek bar - confirmed on-device that without this,
 * D-pad down/up on ANY of these sliders was unusable (values jumping, or navigation stuck).
 */
@Composable
private fun Modifier.tvSafeSliderKeys(): Modifier {
    val focusManager = LocalFocusManager.current
    return this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> true
            Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
            else -> false
        }
    }
}

@Composable
fun TopGradientBar(
    title: String,
    episodeLabel: String?,
    onBack: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenAudioTracks: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onOpenSettings: () -> Unit,
    sharpenEnabled: Boolean,
    onToggleSharpen: () -> Unit,
    sleepTimerRemainingMs: Long?,
    onSetSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val backSource = remember { MutableInteractionSource() }
        IconButton(onClick = onBack, interactionSource = backSource, modifier = Modifier.focusHighlight(backSource, color = Color.White)) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.player_back), tint = Color.White)
        }
        Text(
            text = episodeLabel ?: title,
            color = Color.White,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            maxLines = 1
        )
        val subtitlesSource = remember { MutableInteractionSource() }
        IconButton(onClick = onOpenSubtitles, interactionSource = subtitlesSource, modifier = Modifier.focusHighlight(subtitlesSource, color = Color.White)) {
            Icon(Icons.Default.Subtitles, contentDescription = stringResource(R.string.player_subtitles_button), tint = Color.White)
        }
        // Quick on/off next to the subtitles button, deliberately without the settings panel's
        // "this permanently locks aspect-ratio cycling" confirmation dialog - the whole point is a
        // fast, low-friction A/B look at the sharpened vs. unsharpened picture. Anyone who does hit
        // that consequence gets told about it right when it's actually relevant, via the aspect-
        // ratio-blocked dialog (see PlayerScreen's cycleResizeMode()).
        val sharpenSource = remember { MutableInteractionSource() }
        IconButton(onClick = onToggleSharpen, interactionSource = sharpenSource, modifier = Modifier.focusHighlight(sharpenSource, color = Color.White)) {
            // Was a fixed AutoFixHigh glyph (a generic magic-wand "auto enhance" icon, easy to
            // mistake for some other automatic/AI feature) regardless of state, only the tint
            // color changed. Now animates between two BlurOn/BlurOff icons - unslashed while
            // sharpen is actually on, slashed-through while it's off - so the icon itself, not
            // just its color, shows what tapping it does right now.
            Crossfade(targetState = sharpenEnabled, label = "sharpenIcon") { enabled ->
                Icon(
                    if (enabled) Icons.Default.BlurOn else Icons.Default.BlurOff,
                    contentDescription = stringResource(R.string.player_sharpen_quick_toggle),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else Color.White
                )
            }
        }
        val audioSource = remember { MutableInteractionSource() }
        IconButton(onClick = onOpenAudioTracks, interactionSource = audioSource, modifier = Modifier.focusHighlight(audioSource, color = Color.White)) {
            Icon(Icons.Default.Audiotrack, contentDescription = stringResource(R.string.player_audio_tracks_button), tint = Color.White)
        }
        val aspectSource = remember { MutableInteractionSource() }
        IconButton(onClick = onCycleAspectRatio, interactionSource = aspectSource, modifier = Modifier.focusHighlight(aspectSource, color = Color.White)) {
            Icon(Icons.Default.AspectRatio, contentDescription = stringResource(R.string.player_aspect_ratio), tint = Color.White)
        }
        // Moved here from the settings panel per feedback - buried at the bottom of a long scroll
        // it was easy to miss, unlike the season-scoped intro/credits markers which stay there
        // (one-time-per-season actions, not something reached for every session). The countdown
        // shows right on the icon itself (tinted like the sharpen quick-toggle) so its state is
        // visible without opening the dropdown.
        var sleepTimerMenuExpanded by remember { mutableStateOf(false) }
        Box {
            val sleepTimerSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = { sleepTimerMenuExpanded = true },
                interactionSource = sleepTimerSource,
                modifier = Modifier.focusHighlight(sleepTimerSource, color = Color.White)
            ) {
                if (sleepTimerRemainingMs != null) {
                    Text(
                        formatTime(sleepTimerRemainingMs),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Icon(Icons.Default.Timer, contentDescription = stringResource(R.string.player_sleep_timer_button), tint = Color.White)
                }
            }
            // Fixed width on every row - the countdown text's own width otherwise shifts by a
            // pixel or two each second as its digits change (a proportional font renders "1"
            // narrower than "8"), and since DropdownMenu sizes itself to its widest child, that
            // constant sub-pixel wobble in one row was visibly resizing the whole menu every tick.
            DropdownMenu(expanded = sleepTimerMenuExpanded, onDismissRequest = { sleepTimerMenuExpanded = false }) {
                if (sleepTimerRemainingMs != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_sleep_timer_remaining, formatTime(sleepTimerRemainingMs))) },
                        onClick = {},
                        modifier = Modifier.width(220.dp)
                    )
                }
                listOf(15, 30, 45, 60).forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_sleep_timer_minutes, minutes)) },
                        onClick = {
                            onSetSleepTimer(minutes * 60_000L)
                            sleepTimerMenuExpanded = false
                        },
                        modifier = Modifier.width(220.dp)
                    )
                }
                if (sleepTimerRemainingMs != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_sleep_timer_cancel)) },
                        onClick = {
                            onCancelSleepTimer()
                            sleepTimerMenuExpanded = false
                        },
                        modifier = Modifier.width(220.dp)
                    )
                }
            }
        }
        IconButton(onClick = { /* Cast: требует настройки Google Cast SDK (App ID) - см. заметки */ }) {
            Icon(
                Icons.Default.Cast,
                contentDescription = stringResource(R.string.player_cast_unavailable),
                tint = Color.White.copy(alpha = 0.4f)
            )
        }
        val settingsSource = remember { MutableInteractionSource() }
        IconButton(onClick = onOpenSettings, interactionSource = settingsSource, modifier = Modifier.focusHighlight(settingsSource, color = Color.White)) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.player_settings), tint = Color.White)
        }
    }
}

@Composable
fun CenterTransportControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onTogglePlayPause,
        interactionSource = interactionSource,
        modifier = modifier.size(72.dp).focusHighlight(interactionSource, color = Color.White)
    ) {
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.player_play_pause),
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
    }
}

@Composable
fun BottomGradientBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    thumbnailFrames: ThumbnailFrames?,
    hasNextEpisode: Boolean,
    isLocked: Boolean,
    onSeekTo: (Long) -> Unit,
    onNextEpisode: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasNextEpisode) {
                val nextEpisodeSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = onNextEpisode,
                    interactionSource = nextEpisodeSource,
                    modifier = Modifier.focusHighlight(nextEpisodeSource, color = Color.White)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White)
                    Text(stringResource(R.string.player_next_episode), color = Color.White)
                }
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            // The lock exists to guard against accidental TOUCHES while the phone is in a pocket
            // or being handled - meaningless on a D-pad remote, which can't "accidentally" press
            // anything the same way. Worse than just unnecessary on TV: getting locked left no
            // reliable way back, since the unlock icon's own screen (LockedOverlay) needs D-pad
            // focus that the always-present full-screen root behind it kept stealing back
            // (confirmed on-device: "после блокировки не могу его разблокировать"). Simplest and
            // most correct fix is what it looks like on paper - don't offer a control that solves
            // a touch-only problem, and whose own unlock path isn't D-pad-reachable.
            if (com.illusion.app.ui.common.LocalUiMode.current != com.illusion.app.domain.model.UiMode.TV) {
                val lockSource = remember { MutableInteractionSource() }
                IconButton(onClick = onToggleLock, interactionSource = lockSource, modifier = Modifier.focusHighlight(lockSource, color = Color.White)) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = stringResource(if (isLocked) R.string.player_unlock else R.string.player_lock),
                        tint = Color.White
                    )
                }
            }
        }
        var sliderPosition by remember(currentPositionMs) { mutableFloatStateOf(currentPositionMs.toFloat()) }
        var isDragging by remember { mutableStateOf(false) }

        if (isDragging) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val frame = thumbnailFrames?.let { tf ->
                    val index = (sliderPosition.toLong() / tf.intervalMs.coerceAtLeast(1))
                        .toInt()
                        .coerceIn(0, tf.frames.size - 1)
                    tf.frames.getOrNull(index)
                }
                if (frame != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = frame,
                            contentDescription = null,
                            modifier = Modifier
                                .width(160.dp)
                                .height(90.dp)
                                .border(1.dp, Color.White)
                        )
                        Text(formatTime(sliderPosition.toLong()), color = Color.White)
                    }
                } else {
                    Text(formatTime(sliderPosition.toLong()), color = Color.White)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatTime(currentPositionMs),
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp)
            )
            val sliderInteractionSource = remember { MutableInteractionSource() }
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it; isDragging = true },
                onValueChangeFinished = { isDragging = false; onSeekTo(sliderPosition.toLong()) },
                valueRange = 0f..(durationMs.coerceAtLeast(1).toFloat()),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                interactionSource = sliderInteractionSource,
                // Material3's own Slider key handling (verified via javap on the real
                // material3-1.4.0 jar - SliderKt$slideOnKeyEvents$2) treats DirectionUp/Down
                // exactly like Left/Right - adjusting the seek position, not moving focus. On a
                // D-pad that's actively dangerous here: pressing Down once more after landing on
                // the seek bar (a completely natural "move to the next row" attempt) silently
                // seeks backward instead, confirmed on-device as the movie restarting from the
                // beginning. DirectionDown is swallowed outright here rather than handed to
                // dpadFieldNavigation()'s generic moveFocus(Down) - this is already the
                // bottom-most control, there is nothing below it to move to, and the user's own
                // framing of the fix was exactly right: "если ты уже внизу, на шкале перемотки, то
                // ниже не должен уходить курсор и совершать какие-то действия" - do nothing, not
                // "search for somewhere to go". DirectionUp still moves focus normally - there IS
                // a real row above it. focusHighlight() gives the slider the same visible
                // border/scale every other player control has - its default focus indication was
                // easy to miss entirely.
                modifier = Modifier.weight(1f)
                    .tvSafeSliderKeys()
                    .focusHighlight(sliderInteractionSource, color = Color.White)
            )
            Text(
                formatTime(durationMs),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.width(64.dp)
            )
        }
    }
}

@Composable
fun SkipIntroBanner(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onSkip,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.85f)),
        modifier = modifier
    ) {
        Text(stringResource(R.string.player_skip_intro), color = Color.Black)
    }
}

/** Mirrors [SkipIntroBanner] but for the end-of-episode credits - tapping it just calls the same PlayerViewModel.playNext() the manual "next episode" button already uses, not a new transition. */
@Composable
fun SkipCreditsBanner(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onSkip,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.85f)),
        modifier = modifier
    ) {
        Text(stringResource(R.string.player_skip_credits), color = Color.Black)
    }
}

@Composable
fun ErrorOverlay(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White)
            Spacer(Modifier.padding(4.dp))
            com.illusion.app.ui.common.TvAwareButton(onClick = onRetry) { Text(stringResource(R.string.player_retry)) }
        }
    }
}

@Composable
fun TrackSelectionDialog(
    title: String,
    tracks: List<TrackOption>,
    allowOff: Boolean,
    onSelect: (TrackOption?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (allowOff) {
                    TrackRow(
                        label = stringResource(R.string.player_track_off),
                        selected = tracks.none { it.isSelected },
                        onClick = { onSelect(null) }
                    )
                }
                tracks.forEach { track ->
                    TrackRow(label = track.label, selected = track.isSelected, onClick = { onSelect(track) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) }
        }
    )
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

/**
 * Was a centered `AlertDialog` (a flat list: speed radio rows, then a sharpen toggle, then intro
 * marking, then video info, one after another with no visual grouping) - reworked per user
 * feedback into a translucent panel that slides in from the right instead, edge-to-edge with the
 * player behind it still dimly visible through the scrim, and its content organized into labeled
 * sections instead of one undifferentiated list. Stays mounted at all times (unlike the old
 * conditionally-composed dialog) so [AnimatedVisibility] actually has something to animate in and
 * out - [visible] toggles it instead of the caller adding/removing it from composition.
 */
@Composable
fun PlayerSettingsPanel(
    visible: Boolean,
    currentSpeed: Float,
    videoFormatSummary: String,
    sharpenEnabled: Boolean,
    onSharpenEnabledChange: (Boolean) -> Unit,
    sharpenAmount: Float,
    onSharpenAmountChange: (Float) -> Unit,
    onResetSharpenAmount: () -> Unit,
    aspectRatioLockedBySharpen: Boolean,
    onReloadPlayer: () -> Unit,
    subtitleTextColor: Int,
    onSubtitleTextColorChange: (Int) -> Unit,
    subtitleBackgroundOpacity: Int,
    onSubtitleBackgroundOpacityChange: (Int) -> Unit,
    subtitleTextSizePercent: Int,
    onSubtitleTextSizePercentChange: (Int) -> Unit,
    onResetSubtitleStyle: () -> Unit,
    seekDurationSeconds: Int,
    onSeekDurationSecondsChange: (Int) -> Unit,
    doubleTapSeekEnabled: Boolean,
    onDoubleTapSeekEnabledChange: (Boolean) -> Unit,
    swipeSeekEnabled: Boolean,
    onSwipeSeekEnabledChange: (Boolean) -> Unit,
    holdToSeekEnabled: Boolean,
    onHoldToSeekEnabledChange: (Boolean) -> Unit,
    canMarkIntro: Boolean,
    introMarkedEndMs: Long?,
    onMarkIntroEnd: () -> Unit,
    onClearIntroMarkers: () -> Unit,
    canMarkCredits: Boolean,
    outroMarkedStartMs: Long?,
    onMarkCreditsStart: () -> Unit,
    onClearOutroMarker: () -> Unit,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    // Turning sharpen on permanently switches this player session's video pipeline onto a Media3
    // 1.11.0 code path whose onVideoSizeChanged is a deliberate upstream no-op (TODO b/292111083) -
    // aspect-ratio cycling silently stops working for the rest of the session as a result. Warn
    // before flipping the switch rather than let the user discover it later via a dead button.
    // Kept as a real AlertDialog (unlike the panel below) - a decision like this needs a decisive
    // yes/no interruption, not something that slides away if you tap outside it.
    var showEnableWarning by remember { mutableStateOf(false) }
    if (showEnableWarning) {
        AlertDialog(
            onDismissRequest = { showEnableWarning = false },
            title = { Text(stringResource(R.string.player_sharpen_enable_warning_title)) },
            text = { Text(stringResource(R.string.player_sharpen_enable_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showEnableWarning = false
                    onSharpenEnabledChange(true)
                }) { Text(stringResource(R.string.player_sharpen_enable_warning_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showEnableWarning = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val scrimSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = scrimSource, indication = null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(animationSpec = tween(280), initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(animationSpec = tween(220), targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(Color(0xFF141218).copy(alpha = 0.82f))
                    // Reaching the topmost/bottommost focusable in this panel and pressing
                    // Up/Down once more sent D-pad focus straight through the (semi-transparent)
                    // scrim into the player controls behind it, even though the panel was still
                    // open - confirmed on-device ("меня выкидывает в плеер, хотя настройки
                    // открыты"). Compose's directional focus search operates over the WHOLE
                    // composition, not scoped to this panel, so nothing here stopped it on its
                    // own. onKeyEvent (not onPreviewKeyEvent) bubbles UP from whichever child
                    // handled the key first - by the time it reaches this outermost Column
                    // unconsumed, every real in-panel focus move already had its chance, so
                    // swallowing Up/Down here is exactly "stop trying to leave the panel", not
                    // "block normal navigation within it" - same principle as the seek bar's own
                    // fix, applied at the panel's outer boundary instead of a single control's.
                    .onKeyEvent { event ->
                        visible && event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionDown || event.key == Key.DirectionUp)
                    }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_settings), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val closeSource = remember { MutableInteractionSource() }
                    // Nothing claimed focus when this panel appeared - whatever button opened it
                    // (the gear icon in the top bar, now hidden behind the scrim) kept focus, so
                    // D-pad presses had no visible target inside the panel at all (confirmed
                    // on-device: "окно появляется, но я не могу с ним взаимодействовать"). The
                    // close button is the panel's first real control - claiming focus on it the
                    // moment the panel becomes visible gives D-pad navigation somewhere to start.
                    val closeButtonFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                    LaunchedEffect(visible) {
                        if (visible) runCatching { closeButtonFocusRequester.requestFocus() }
                    }
                    IconButton(
                        onClick = onDismiss,
                        interactionSource = closeSource,
                        modifier = Modifier
                            .focusRequester(closeButtonFocusRequester)
                            .focusHighlight(closeSource, color = Color.White)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.player_close), tint = Color.White)
                    }
                }

                CollapsiblePanelSection(stringResource(R.string.player_settings_section_speed)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    speeds.forEach { speed ->
                        FilterChip(
                            selected = speed == currentSpeed,
                            onClick = { onSelect(speed) },
                            label = { Text("${speed}x") },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color.White,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                }

                CollapsiblePanelSection(stringResource(R.string.player_settings_section_subtitles)) {
                Text(
                    stringResource(R.string.player_subtitle_color),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)) {
                    listOf(
                        Color.White, Color.Yellow, Color(0xFF00E5FF), Color(0xFF7CFC00), Color(0xFFFF5252)
                    ).forEach { swatch ->
                        val swatchArgb = swatch.toArgb()
                        val selected = swatchArgb == subtitleTextColor
                        val swatchSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(swatch)
                                .clickable(interactionSource = swatchSource, indication = null) { onSubtitleTextColorChange(swatchArgb) }
                                .then(
                                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape) else Modifier
                                )
                        )
                    }
                }
                Text(
                    stringResource(R.string.player_subtitle_background_opacity, subtitleBackgroundOpacity),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.Slider(
                    value = subtitleBackgroundOpacity.toFloat(),
                    onValueChange = { onSubtitleBackgroundOpacityChange(it.roundToInt()) },
                    valueRange = 0f..100f,
                    steps = 9,
                    modifier = Modifier.tvSafeSliderKeys()
                )
                Text(
                    stringResource(R.string.player_subtitle_text_size, subtitleTextSizePercent),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.Slider(
                    value = subtitleTextSizePercent.toFloat(),
                    onValueChange = { onSubtitleTextSizePercentChange(it.roundToInt()) },
                    valueRange = 50f..200f,
                    steps = 14,
                    modifier = Modifier.tvSafeSliderKeys()
                )
                TextButton(onClick = onResetSubtitleStyle, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.player_subtitle_style_reset))
                }
                }

                // Entirely touch-gesture concepts (double-tap/swipe/hold-to-seek, and the seek
                // duration slider that only ever feeds those gestures - there's no D-pad-triggered
                // rewind/fast-forward anywhere in this app to apply it to either) - meaningless
                // dead controls on a remote, which can't double-tap/swipe/hold a screen it doesn't
                // touch. The whole section is nothing but these, so it's hidden outright on TV
                // rather than emptied out control-by-control.
                if (com.illusion.app.ui.common.LocalUiMode.current != com.illusion.app.domain.model.UiMode.TV) {
                CollapsiblePanelSection(stringResource(R.string.player_settings_section_gestures)) {
                Text(
                    stringResource(R.string.player_seek_duration, seekDurationSeconds),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.Slider(
                    value = seekDurationSeconds.toFloat(),
                    onValueChange = { onSeekDurationSecondsChange(it.roundToInt()) },
                    valueRange = 5f..30f,
                    steps = 4,
                    modifier = Modifier.tvSafeSliderKeys()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_double_tap_seek), color = Color.White, modifier = Modifier.weight(1f))
                    com.illusion.app.ui.common.TvAwareSwitch(checked = doubleTapSeekEnabled, onCheckedChange = onDoubleTapSeekEnabledChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_swipe_seek), color = Color.White, modifier = Modifier.weight(1f))
                    com.illusion.app.ui.common.TvAwareSwitch(checked = swipeSeekEnabled, onCheckedChange = onSwipeSeekEnabledChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_hold_to_seek), color = Color.White, modifier = Modifier.weight(1f))
                    com.illusion.app.ui.common.TvAwareSwitch(checked = holdToSeekEnabled, onCheckedChange = onHoldToSeekEnabledChange)
                }
                }
                }

                CollapsiblePanelSection(stringResource(R.string.player_settings_section_image)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_sharpen_toggle), color = Color.White, modifier = Modifier.weight(1f))
                    com.illusion.app.ui.common.TvAwareSwitch(
                        checked = sharpenEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) showEnableWarning = true else onSharpenEnabledChange(false)
                        }
                    )
                }
                if (sharpenEnabled) {
                    // onSharpenAmountChange persists to DataStore, which PlayerViewModel's own
                    // sharpenAmount collector reacts to by calling player.setVideoEffects() again -
                    // a genuinely heavy operation (rebuilds the whole video effects pipeline).
                    // Committing straight from onValueChange fired it dozens of times per second
                    // while dragging, which was enough to deadlock the renderer thread entirely -
                    // confirmed on-device as a full picture+audio freeze and ExoPlayer's own "Player
                    // stuck playing with no progress for 10000ms" watchdog error. Local drag state
                    // (same pattern as the seek bar above) keeps the live percentage readout
                    // responsive while dragging, but only actually commits - and so only triggers
                    // one real setVideoEffects() call - once the user lets go.
                    var sharpenDragValue by remember(sharpenAmount) { mutableStateOf(sharpenAmount) }
                    Text(
                        stringResource(R.string.player_sharpen_amount, (sharpenDragValue * 100).roundToInt()),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    androidx.compose.material3.Slider(
                        value = sharpenDragValue,
                        onValueChange = { sharpenDragValue = it },
                        onValueChangeFinished = { onSharpenAmountChange(sharpenDragValue) },
                        valueRange = 0.1f..1f,
                        steps = 8,
                        modifier = Modifier.tvSafeSliderKeys()
                    )
                    TextButton(onClick = onResetSharpenAmount, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.player_sharpen_amount_reset))
                    }
                }
                if (aspectRatioLockedBySharpen) {
                    Text(
                        stringResource(R.string.player_aspect_ratio_locked),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(onClick = onReloadPlayer, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.player_reload))
                    }
                }
                }

                // The section itself is collapsed by default (CollapsiblePanelSection), which
                // already gates visibility - an inner switch on top of that was a redundant second
                // gate the user had to also flip after expanding the section.
                CollapsiblePanelSection(stringResource(R.string.player_settings_section_technical)) {
                Text(videoFormatSummary, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }

                if (canMarkIntro) {
                    PanelSectionLabel(stringResource(R.string.player_settings_section_intro))
                    if (introMarkedEndMs != null) {
                        Text(
                            stringResource(R.string.player_intro_marked_at, formatTime(introMarkedEndMs)),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onMarkIntroEnd) {
                            Text(stringResource(R.string.player_mark_intro_end))
                        }
                        if (introMarkedEndMs != null) {
                            TextButton(onClick = onClearIntroMarkers) {
                                Text(stringResource(R.string.player_clear_intro_markers))
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.player_mark_intro_end_hint),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (canMarkCredits) {
                    PanelSectionLabel(stringResource(R.string.player_settings_section_credits))
                    if (outroMarkedStartMs != null) {
                        Text(
                            stringResource(R.string.player_credits_marked_at, formatTime(outroMarkedStartMs)),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onMarkCreditsStart) {
                            Text(stringResource(R.string.player_mark_credits_start))
                        }
                        if (outroMarkedStartMs != null) {
                            TextButton(onClick = onClearOutroMarker) {
                                Text(stringResource(R.string.player_clear_outro_marker))
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.player_mark_credits_start_hint),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelSectionLabel(text: String) {
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
    Text(
        text,
        color = Color.White.copy(alpha = 0.6f),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** Same divider+label as [PanelSectionLabel], but the label itself is the toggle for an
 * [AnimatedVisibility] section below it - per feedback, this whole panel (speed/subtitles/
 * gestures/image/technical-info, all at once) read as too much to scan through every time it
 * opened, most of it for settings someone sets once and never touches again. Collapsed by
 * default; a chevron marks which way it folds. */
@Composable
private fun CollapsiblePanelSection(text: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
    val headerSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = headerSource, indication = null) { expanded = !expanded }
            .focusHighlight(headerSource)
            .padding(bottom = 8.dp)
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f)
        )
    }
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(content = content)
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
