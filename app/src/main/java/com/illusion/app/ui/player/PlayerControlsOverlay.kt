package com.illusion.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Deblur
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import com.illusion.app.ui.common.dpadFieldNavigation
import com.illusion.app.ui.common.focusHighlight
import java.util.Locale
import com.illusion.app.ui.common.MenuShape

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
    // A one-track file opened a dialog listing that single track and nothing else - the button is
    // simply disabled instead when there's nothing to choose between.
    audioTrackCount: Int,
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // Was a two-stop 0.7 -> transparent scrim only as tall as the row itself: over a
            // bright frame (a daylight sky) the white icons and the title sat on almost no
            // scrim at all and were genuinely hard to read on-device. Three stops with a denser
            // top and extra bottom padding give the gradient real height to fade out over.
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.45f), Color.Transparent)
                )
            )
            .padding(horizontal = 8.dp)
            .padding(top = 8.dp, bottom = 28.dp)
    ) {
        // On a phone in portrait the action row (7 icons + back) is wider than the screen, so the
        // title's weight(1f) resolved to zero width and the title simply wasn't drawn at all -
        // confirmed on-device. Below this threshold the title moves to its own line under the
        // controls instead of competing with them for the same row.
        val compact = maxWidth < 560.dp
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            val backSource = remember { MutableInteractionSource() }
            IconButton(onClick = onBack, interactionSource = backSource, modifier = Modifier.focusHighlight(backSource, color = Color.White)) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.player_back), tint = Color.White)
            }
                if (!compact) {
                    PlayerTitle(episodeLabel ?: title, Modifier.weight(1f).padding(start = 4.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            val subtitlesSource = remember { MutableInteractionSource() }
            IconButton(onClick = onOpenSubtitles, interactionSource = subtitlesSource, modifier = Modifier.focusHighlight(subtitlesSource, color = Color.White)) {
                Icon(Icons.Default.Subtitles, contentDescription = stringResource(R.string.player_subtitles_button), tint = Color.White)
            }
            val audioSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onOpenAudioTracks,
                enabled = audioTrackCount > 1,
                interactionSource = audioSource,
                modifier = Modifier.focusHighlight(audioSource, color = Color.White)
            ) {
                Icon(
                    Icons.Default.Audiotrack,
                    contentDescription = stringResource(R.string.player_audio_tracks_button),
                    tint = if (audioTrackCount > 1) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
            Spacer(Modifier.width(if (compact) 0.dp else 10.dp))
            // Quick on/off next to the subtitles button, deliberately without the settings panel's
            // "this permanently locks aspect-ratio cycling" confirmation dialog - the whole point is a
            // fast, low-friction A/B look at the sharpened vs. unsharpened picture. Anyone who does hit
            // that consequence gets told about it right when it's actually relevant, via the aspect-
            // ratio-blocked dialog (see PlayerScreen's cycleResizeMode()).
            val sharpenSource = remember { MutableInteractionSource() }
            IconButton(onClick = onToggleSharpen, interactionSource = sharpenSource, modifier = Modifier.focusHighlight(sharpenSource, color = Color.White)) {
                // Was a fixed AutoFixHigh glyph (a generic magic-wand "auto enhance" icon, easy to
                // mistake for some other automatic/AI feature) regardless of state, only the tint
                // color changed; then a BlurOn/BlurOff pair, so the icon itself - not just its
                // color - shows what tapping it does right now. BlurOn/BlurOff (a plain dotted square when off) didn't read as "sharpness" at all
                // on-device - now Deblur (dots resolving into a sharp edge) while it's on, tinted with
                // the accent, and the plainly-blurred BlurOn glyph while it's off.
                Crossfade(targetState = sharpenEnabled, label = "sharpenIcon") { enabled ->
                    Icon(
                        if (enabled) Icons.Default.Deblur else Icons.Default.BlurOn,
                        contentDescription = stringResource(R.string.player_sharpen_quick_toggle),
                        tint = if (enabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
                    )
                }
            }
            val aspectSource = remember { MutableInteractionSource() }
            IconButton(onClick = onCycleAspectRatio, interactionSource = aspectSource, modifier = Modifier.focusHighlight(aspectSource, color = Color.White)) {
                Icon(Icons.Default.AspectRatio, contentDescription = stringResource(R.string.player_aspect_ratio), tint = Color.White)
            }
            Spacer(Modifier.width(if (compact) 0.dp else 10.dp))
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
                DropdownMenu(expanded = sleepTimerMenuExpanded, onDismissRequest = { sleepTimerMenuExpanded = false }, shape = MenuShape) {
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
            // Deliberately enabled = false, not just a dimmed tint on a live button: it looked
            // identical in weight to the working controls next to it, and tapping it did nothing with
            // no explanation. Disabled it also stops taking D-pad focus on the way to Settings.
            IconButton(onClick = {}, enabled = false) {
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
            if (compact) {
                PlayerTitle(episodeLabel ?: title, Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp))
            }
        }
    }
}

/** Shared by both TopGradientBar layouts - see the `compact` note there. */
@Composable
private fun PlayerTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        // Plain default body text read as incidental text over the video rather than as the
        // title; a heavier style plus a soft drop shadow keeps it legible on a bright frame.
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 1f), blurRadius = 6f)
        ),
        modifier = modifier,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
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
        // A bare white glyph disappeared into bright frames (sky, snow) - a soft dark disc behind
        // it keeps the primary control visible on any content without adding a heavy chrome look.
        modifier = modifier
            .size(72.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            .focusHighlight(interactionSource, color = Color.White)
    ) {
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.player_play_pause),
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BottomGradientBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    thumbnailFrames: ThumbnailFrames?,
    hasNextEpisode: Boolean,
    isLocked: Boolean,
    onSeekTo: (Long) -> Unit,
    onSeekDragging: () -> Unit = {},
    // Reports the position currently being dragged to (null when not dragging) so the caller can
    // render the scrub thumbnail/time preview itself - see this composable's own historical KDoc
    // below on why the preview can no longer be an internal part of this bar's own layout.
    onSeekDragPositionChange: (Long?) -> Unit = {},
    onNextEpisode: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    // NOT remember(currentPositionMs) - the position ticker keeps advancing currentPositionMs
    // every 500ms during playback even while the user is actively dragging (the real seek only
    // happens on release, in onValueChangeFinished), so keying remember on it re-synced
    // sliderPosition back to the still-playing position mid-drag, fighting the drag gesture's
    // own onValueChange updates. The thumb/track visibly snapped between the two positions each
    // tick - reported as the seek bar "doubling" while scrubbing. Only sync from the real
    // playback position when the user isn't actively holding the slider.
    var sliderPosition by remember { mutableFloatStateOf(currentPositionMs.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(currentPositionMs, isDragging) {
        if (!isDragging) sliderPosition = currentPositionMs.toFloat()
    }

    // The scrub thumbnail/time preview used to be rendered right here (first as an ordinary child
    // laid out above the time/slider row, later as an absolutely-positioned overlay measured
    // against this bar's own height via a custom zero-size Modifier.layout{} trick) - both
    // versions had real bugs: the first let the preview's appearing/disappearing change this
    // bar's own measured height, which shifted the buffering spinner/play-pause button centered
    // in a sibling Box sized off that height (reported as "спиннер скачет при перемотке"); the
    // zero-size-layout fix for THAT bug then mispositioned the preview itself on-device (reported
    // showing pinned to the right instead of centered above the bar). Rather than keep patching an
    // increasingly clever custom layout, the preview is now rendered by the CALLER entirely -
    // PlayerScreen already measures its own bottomBarHeightPx independently for other reasons, so
    // positioning an overlay off that measurement, in a Box that isn't nested inside this bar's
    // own layout at all, can't ever feed back into this bar's own size no matter what the preview
    // itself measures as.
    LaunchedEffect(sliderPosition, isDragging) {
        onSeekDragPositionChange(if (isDragging) sliderPosition.toLong() else null)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Mirrors the top bar's fix: a three-stop scrim with real height, so the lock button
            // and the timecodes in the upper (previously near-transparent) part of this bar are
            // readable over a bright frame too.
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.85f))
                )
            )
            .padding(horizontal = 12.dp)
            .padding(top = 24.dp, bottom = 8.dp)
    ) {
        // Was an always-composed row (an empty Spacer when there's no next episode) that also
        // held the lock button off on its own in the corner, visually detached from everything
        // else. Only rendered when it has real content now; the lock moved down into the seek row.
        if (hasNextEpisode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val nextEpisodeSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = onNextEpisode,
                    interactionSource = nextEpisodeSource,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .focusHighlight(nextEpisodeSource, color = Color.White)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White)
                    Text(stringResource(R.string.player_next_episode), color = Color.White)
                }
                Spacer(Modifier.weight(1f))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // The lock exists to guard against accidental TOUCHES while the phone is in a pocket
            // or being handled - meaningless on a D-pad remote, which can't "accidentally" press
            // anything the same way. Worse than just unnecessary on TV: getting locked left no
            // reliable way back, since the unlock icon's own screen (LockedOverlay) needs D-pad
            // focus that the always-present full-screen root behind it kept stealing back
            // (confirmed on-device: "после блокировки не могу его разблокировать").
            if (com.illusion.app.ui.common.LocalUiMode.current != com.illusion.app.domain.model.UiMode.TV) {
                val lockSource = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onToggleLock,
                    interactionSource = lockSource,
                    modifier = Modifier.focusHighlight(lockSource, color = Color.White)
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = stringResource(if (isLocked) R.string.player_unlock else R.string.player_lock),
                        tint = Color.White
                    )
                }
            }
            Text(
                formatTime(currentPositionMs),
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.End,
                softWrap = false,
                modifier = Modifier.widthIn(min = 64.dp)
            )
            val sliderInteractionSource = remember { MutableInteractionSource() }
            // Releasing a held scrub (or even a single tap-adjust) sometimes sent D-pad focus
            // flying off to the top-right icon row instead of staying on the seek bar - confirmed
            // on-device, most reliably reproduced by holding Left/Right rather than a quick press.
            // The likely trigger was the thumbnail-preview box appearing/disappearing with
            // isDragging, which used to shift this whole row's on-screen position at the exact
            // moment focus state is most fragile (mid key-repeat) - the preview is now rendered
            // entirely by the caller instead (see this composable's own top-level comment on
            // onSeekDragPositionChange), so this row no longer moves at all, but the explicit
            // re-claim stays as a harmless safety net
            // rather than trusting that removing the original trigger fully closes the door on it.
            val sliderFocusRequester = remember { FocusRequester() }
            // Only reacts to an actual true->false transition (a real drag just ending) - a plain
            // "if (!isDragging)" would also fire on this composable's very first entry into
            // composition (isDragging starts false), fighting PlayerScreen's own initial-focus
            // LaunchedEffect for the play/pause button and non-deterministically stealing that
            // default entry point away from it.
            var wasDragging by remember { mutableStateOf(false) }
            LaunchedEffect(isDragging) {
                if (wasDragging && !isDragging) runCatching { sliderFocusRequester.requestFocus() }
                wasDragging = isDragging
            }
            val sliderColors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
            val accentColor = MaterialTheme.colorScheme.primary
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it; isDragging = true; onSeekDragging() },
                onValueChangeFinished = { isDragging = false; onSeekTo(sliderPosition.toLong()) },
                valueRange = 0f..(durationMs.coerceAtLeast(1).toFloat()),
                colors = sliderColors,
                interactionSource = sliderInteractionSource,
                // Material3 1.4's default thumb is a tall vertical bar that stands well above and
                // below the track and reads as a separate element on top of the video. A small
                // accent dot (growing slightly while dragging) sits on the track itself, the way
                // a video scrubber normally looks.
                thumb = {
                    val thumbDiameter by animateDpAsState(
                        targetValue = if (isDragging) 16.dp else 12.dp,
                        label = "seekThumbSize"
                    )
                    Box(
                        Modifier
                            .size(20.dp)
                            .wrapContentSize(Alignment.Center)
                            .size(thumbDiameter)
                            .background(accentColor, CircleShape)
                    )
                },
                // Custom track for two reasons the default one can't cover: bufferedPositionMs was
                // being passed into this composable and never drawn anywhere (no "loaded ahead"
                // band at all, unusual for a streaming player), and Material3 1.4's default track
                // paints a stop indicator dot at the far end (verified in SliderDefaults' own
                // bytecode - drawStopIndicator/getTrackStopIndicatorSize), which over video reads
                // as a stray artifact next to the duration rather than as a control.
                track = { sliderState ->
                    val playedFraction = sliderState.coercedValueAsFraction.coerceIn(0f, 1f)
                    val bufferedFraction = if (durationMs > 0) {
                        (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                        val centerY = size.height / 2f
                        val stroke = size.height
                        drawLine(
                            Color.White.copy(alpha = 0.3f),
                            Offset(0f, centerY), Offset(size.width, centerY),
                            strokeWidth = stroke, cap = StrokeCap.Round
                        )
                        if (bufferedFraction > 0f) {
                            drawLine(
                                Color.White.copy(alpha = 0.5f),
                                Offset(0f, centerY), Offset(size.width * bufferedFraction, centerY),
                                strokeWidth = stroke, cap = StrokeCap.Round
                            )
                        }
                        if (playedFraction > 0f) {
                            drawLine(
                                accentColor,
                                Offset(0f, centerY), Offset(size.width * playedFraction, centerY),
                                strokeWidth = stroke, cap = StrokeCap.Round
                            )
                        }
                    }
                },
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
                // The custom track fills the slider's full width (the default M3 track kept its
                // own inset), which left the bar butting straight up against the timecodes.
                modifier = Modifier.weight(1f)
                    .padding(horizontal = 12.dp)
                    .focusRequester(sliderFocusRequester)
                    .tvSafeSliderKeys()
                    .focusHighlight(sliderInteractionSource, color = Color.White)
            )
            // Only the total duration was ever shown - "how much is left" is the number people
            // actually want mid-film, so tapping this cell switches between the two.
            var showRemaining by remember { mutableStateOf(false) }
            val remainingSource = remember { MutableInteractionSource() }
            Text(
                if (showRemaining) "-" + formatTime((durationMs - currentPositionMs).coerceAtLeast(0))
                else formatTime(durationMs),
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = remainingSource,
                        indication = null,
                        onClickLabel = stringResource(R.string.player_show_remaining_time)
                    ) { showRemaining = !showRemaining }
                    .focusHighlight(remainingSource, color = Color.White)
                    .widthIn(min = 64.dp)
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
                    .background(Color.Black.copy(alpha = 0.6f))
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
                    .width(340.dp)
                    // Was translucent (alpha 0.82) - the player's own top-bar icons, the lock and
                    // the duration timecode showed straight through the panel's own rows, so its
                    // text sat on moving video. Opaque now; the scrim alone keeps the "player is
                    // still behind this" feel.
                    .background(Color(0xFF141218))
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
                    // Short title: the full "Настройки воспроизведения" wrapped onto two lines in
                    // this panel's width. The long form stays as the gear icon's contentDescription.
                    Text(
                        stringResource(R.string.player_settings_panel_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
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
                // Was a FlowRow of intrinsically-sized chips - they wrapped 3+3 at uneven widths
                // with "2.0x" hanging alone. A fixed two-row grid of equal cells instead.
                speeds.chunked(3).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        row.forEach { speed ->
                            FilterChip(
                                selected = speed == currentSpeed,
                                onClick = { onSelect(speed) },
                                label = {
                                    Text(
                                        "${speed}x",
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = Color.White,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                PanelSlider(
                    value = subtitleBackgroundOpacity.toFloat(),
                    onValueChange = { onSubtitleBackgroundOpacityChange(it.roundToInt()) },
                    valueRange = 0f..100f,
                    steps = 9
                )
                Text(
                    stringResource(R.string.player_subtitle_text_size, subtitleTextSizePercent),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                PanelSlider(
                    value = subtitleTextSizePercent.toFloat(),
                    onValueChange = { onSubtitleTextSizePercentChange(it.roundToInt()) },
                    valueRange = 50f..200f,
                    steps = 14
                )
                PanelResetButton(stringResource(R.string.player_subtitle_style_reset), onResetSubtitleStyle)
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
                PanelSlider(
                    value = seekDurationSeconds.toFloat(),
                    onValueChange = { onSeekDurationSecondsChange(it.roundToInt()) },
                    valueRange = 5f..30f,
                    steps = 4
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
                // Economical performance mode already force-disables the actual shader
                // (PlayerViewModel's own sharpenEnabled collector - see PerformanceMode's own
                // KDoc), but leaving this switch merely no-op-interactive read as "you can still
                // turn it on" - tapping it flipped the switch, then it silently snapped back off
                // once the collector recomputed, with no clear feedback either way. Disabling it
                // outright removes that ambiguity.
                val sharpenDisabledByPerformanceMode = com.illusion.app.ui.common.LocalEconomicalMode.current
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_sharpen_toggle), color = Color.White, modifier = Modifier.weight(1f))
                    com.illusion.app.ui.common.TvAwareSwitch(
                        checked = sharpenEnabled,
                        enabled = !sharpenDisabledByPerformanceMode,
                        onCheckedChange = onSharpenEnabledChange
                    )
                }
                if (sharpenDisabledByPerformanceMode) {
                    Text(
                        stringResource(R.string.player_sharpen_disabled_by_performance_mode),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
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
                    PanelSlider(
                        value = sharpenDragValue,
                        onValueChange = { sharpenDragValue = it },
                        onValueChangeFinished = { onSharpenAmountChange(sharpenDragValue) },
                        valueRange = 0.1f..1f,
                        steps = 8
                    )
                    PanelResetButton(stringResource(R.string.player_sharpen_amount_reset), onResetSharpenAmount)
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

/**
 * Every slider inside the settings panel. The default Material3 slider (tall bar thumb, tick dots
 * drawn along the track) looked nothing like the seek bar's own scrubber and read as a dotted,
 * broken line at a glance - this keeps the step behaviour but draws the same clean track + accent
 * dot the seek bar uses.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PanelSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val accentColor = MaterialTheme.colorScheme.primary
    val colors = SliderDefaults.colors(
        thumbColor = accentColor,
        activeTrackColor = accentColor,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            Box(
                Modifier
                    .size(20.dp)
                    .wrapContentSize(Alignment.Center)
                    .size(12.dp)
                    .background(accentColor, CircleShape)
            )
        },
        track = { sliderState ->
            val fraction = sliderState.coercedValueAsFraction.coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val centerY = size.height / 2f
                drawLine(
                    Color.White.copy(alpha = 0.3f),
                    Offset(0f, centerY), Offset(size.width, centerY),
                    strokeWidth = size.height, cap = StrokeCap.Round
                )
                if (fraction > 0f) {
                    drawLine(
                        accentColor,
                        Offset(0f, centerY), Offset(size.width * fraction, centerY),
                        strokeWidth = size.height, cap = StrokeCap.Round
                    )
                }
            }
        },
        modifier = modifier.tvSafeSliderKeys()
    )
}

/** One shared look for every "reset to defaults" action in the panel. */
@Composable
private fun PanelResetButton(text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        interactionSource = source,
        modifier = Modifier.padding(top = 4.dp).focusHighlight(source)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PanelSectionLabel(text: String) {
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
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
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
    val headerSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = headerSource, indication = null) { expanded = !expanded }
            .focusHighlight(headerSource)
            .padding(bottom = 8.dp)
    ) {
        // A section title used to be dimmer and smaller than the labels inside it, so the panel
        // read from the inside out. The header is the loudest thing in its own section now.
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f)
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
