package com.seance.app.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Cast
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seance.app.R
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.player_back), tint = Color.White)
        }
        Text(
            text = episodeLabel ?: title,
            color = Color.White,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            maxLines = 1
        )
        IconButton(onClick = onOpenSubtitles) {
            Icon(Icons.Default.Subtitles, contentDescription = stringResource(R.string.player_subtitles_button), tint = Color.White)
        }
        IconButton(onClick = onOpenAudioTracks) {
            Icon(Icons.Default.Audiotrack, contentDescription = stringResource(R.string.player_audio_tracks_button), tint = Color.White)
        }
        IconButton(onClick = onCycleAspectRatio) {
            Icon(Icons.Default.AspectRatio, contentDescription = stringResource(R.string.player_aspect_ratio), tint = Color.White)
        }
        IconButton(onClick = { /* Cast: требует настройки Google Cast SDK (App ID) - см. заметки */ }) {
            Icon(
                Icons.Default.Cast,
                contentDescription = stringResource(R.string.player_cast_unavailable),
                tint = Color.White.copy(alpha = 0.4f)
            )
        }
        IconButton(onClick = onOpenSettings) {
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
    IconButton(onClick = onTogglePlayPause, modifier = modifier.size(72.dp)) {
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
                TextButton(onClick = onNextEpisode) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White)
                    Text(stringResource(R.string.player_next_episode), color = Color.White)
                }
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onToggleLock) {
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
            Text(formatTime(currentPositionMs), color = Color.White, modifier = Modifier.width(56.dp))
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it; isDragging = true },
                onValueChangeFinished = { isDragging = false; onSeekTo(sliderPosition.toLong()) },
                valueRange = 0f..(durationMs.coerceAtLeast(1).toFloat()),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.weight(1f)
            )
            Text(formatTime(durationMs), color = Color.White, modifier = Modifier.width(56.dp))
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

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    videoFormatSummary: String,
    sharpenEnabled: Boolean,
    onSharpenEnabledChange: (Boolean) -> Unit,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    // Turning sharpen on permanently switches this player session's video pipeline onto a Media3
    // 1.11.0 code path whose onVideoSizeChanged is a deliberate upstream no-op (TODO b/292111083) -
    // aspect-ratio cycling silently stops working for the rest of the session as a result. Warn
    // before flipping the switch rather than let the user discover it later via a dead button.
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_speed)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                speeds.forEach { speed ->
                    TrackRow(label = "${speed}x", selected = speed == currentSpeed, onClick = { onSelect(speed) })
                }
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.player_sharpen_toggle), modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = sharpenEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) showEnableWarning = true else onSharpenEnabledChange(false)
                        }
                    )
                }
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.player_video_info_title), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Text(videoFormatSummary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) }
        }
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
