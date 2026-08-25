package com.illusion.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.illusion.app.ui.common.focusHighlight
import java.util.Locale

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
            Icon(
                Icons.Default.AutoFixHigh,
                contentDescription = stringResource(R.string.player_sharpen_quick_toggle),
                tint = if (sharpenEnabled) MaterialTheme.colorScheme.primary else Color.White
            )
        }
        val audioSource = remember { MutableInteractionSource() }
        IconButton(onClick = onOpenAudioTracks, interactionSource = audioSource, modifier = Modifier.focusHighlight(audioSource, color = Color.White)) {
            Icon(Icons.Default.Audiotrack, contentDescription = stringResource(R.string.player_audio_tracks_button), tint = Color.White)
        }
        val aspectSource = remember { MutableInteractionSource() }
        IconButton(onClick = onCycleAspectRatio, interactionSource = aspectSource, modifier = Modifier.focusHighlight(aspectSource, color = Color.White)) {
            Icon(Icons.Default.AspectRatio, contentDescription = stringResource(R.string.player_aspect_ratio), tint = Color.White)
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
            val lockSource = remember { MutableInteractionSource() }
            IconButton(onClick = onToggleLock, interactionSource = lockSource, modifier = Modifier.focusHighlight(lockSource, color = Color.White)) {
                Icon(
                    if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = stringResource(if (isLocked) R.string.player_unlock else R.string.player_lock),
                    tint = Color.White
                )
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
                modifier = Modifier.weight(1f)
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
            Button(onClick = onRetry) { Text(stringResource(R.string.player_retry)) }
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
    showTechnicalInfo: Boolean,
    onShowTechnicalInfoChange: (Boolean) -> Unit,
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
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_settings), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val closeSource = remember { MutableInteractionSource() }
                    IconButton(onClick = onDismiss, interactionSource = closeSource, modifier = Modifier.focusHighlight(closeSource, color = Color.White)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.player_close), tint = Color.White)
                    }
                }

                PanelSectionLabel(stringResource(R.string.player_settings_section_speed))
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

                PanelSectionLabel(stringResource(R.string.player_settings_section_subtitles))
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
                    steps = 9
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
                    steps = 14
                )
                TextButton(onClick = onResetSubtitleStyle, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.player_subtitle_style_reset))
                }

                PanelSectionLabel(stringResource(R.string.player_settings_section_gestures))
                Text(
                    stringResource(R.string.player_seek_duration, seekDurationSeconds),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.Slider(
                    value = seekDurationSeconds.toFloat(),
                    onValueChange = { onSeekDurationSecondsChange(it.roundToInt()) },
                    valueRange = 5f..30f,
                    steps = 4
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_double_tap_seek), color = Color.White, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = doubleTapSeekEnabled, onCheckedChange = onDoubleTapSeekEnabledChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_swipe_seek), color = Color.White, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = swipeSeekEnabled, onCheckedChange = onSwipeSeekEnabledChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_hold_to_seek), color = Color.White, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = holdToSeekEnabled, onCheckedChange = onHoldToSeekEnabledChange)
                }

                PanelSectionLabel(stringResource(R.string.player_settings_section_image))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_sharpen_toggle), color = Color.White, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = sharpenEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) showEnableWarning = true else onSharpenEnabledChange(false)
                        }
                    )
                }
                if (sharpenEnabled) {
                    Text(
                        stringResource(R.string.player_sharpen_amount, (sharpenAmount * 100).roundToInt()),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    androidx.compose.material3.Slider(
                        value = sharpenAmount,
                        onValueChange = onSharpenAmountChange,
                        valueRange = 0.1f..1f,
                        steps = 8
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
                // A separate, off-by-default toggle rather than always showing the text below the
                // sharpen switch - codec/resolution/HDR details aren't something most viewers ever
                // look for, and it cluttered this panel by default for everyone who never asked.
                PanelSectionLabel(stringResource(R.string.player_settings_section_technical))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_show_technical_info), color = Color.White, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = showTechnicalInfo,
                        onCheckedChange = onShowTechnicalInfoChange
                    )
                }
                if (showTechnicalInfo) {
                    Text(videoFormatSummary, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
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
