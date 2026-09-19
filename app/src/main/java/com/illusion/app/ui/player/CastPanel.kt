package com.illusion.app.ui.player

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import com.illusion.app.data.cast.DlnaDevice
import com.illusion.app.ui.common.TvAwareButton
import com.illusion.app.ui.common.TvAwareIconButton
import com.illusion.app.ui.common.TvAwareTextButton
import com.illusion.app.ui.common.focusHighlight

/**
 * Picker for a DLNA renderer to cast to, and - once one is playing - the remote controls for it.
 *
 * One dialog for both, because they're the same conversation: while casting, the only useful
 * actions are the transport controls and stopping, not picking a second device. Seeking is by
 * ±10 s buttons rather than a draggable bar: the renderer's own position only comes back every
 * couple of seconds (see [PlayerViewModel.startCastPolling]), so a bar would fight the poll.
 */
@Composable
fun CastDialog(
    state: CastUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelectDevice: (DlnaDevice) -> Unit,
    onSelectGoogleDevice: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onStopCast: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_cast_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.isCasting) {
                    CastTransportControls(state, onTogglePlayPause, onSeekBy)
                } else {
                    CastDeviceList(state, onSelectDevice, onSelectGoogleDevice)
                }
            }
        },
        confirmButton = {
            if (state.isCasting) {
                TvAwareTextButton(onClick = onStopCast) { Text(stringResource(R.string.player_cast_stop)) }
            } else {
                TvAwareTextButton(onClick = onRefresh, enabled = !state.isSearching && !state.isConnecting) {
                    Text(stringResource(R.string.player_cast_refresh))
                }
            }
        },
        dismissButton = {
            TvAwareTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun CastDeviceList(state: CastUiState, onSelectDevice: (DlnaDevice) -> Unit, onSelectGoogleDevice: (String) -> Unit) {
    val noDevices = state.devices.isEmpty() && state.googleDevices.isEmpty()
    when {
        state.isConnecting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.player_cast_connecting))
        }
        state.isSearching && noDevices -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.player_cast_searching))
        }
        noDevices -> Text(
            stringResource(R.string.player_cast_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> LazyColumn(modifier = Modifier.heightIn(max = 260.dp).focusGroup()) {
            items(state.googleDevices, key = { "cast:${it.id}" }) { device ->
                TvAwareButton(
                    onClick = { onSelectGoogleDevice(device.id) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Tv, contentDescription = null)
                        Column {
                            Text(device.name)
                            Text("Google Cast", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            items(state.devices, key = { "dlna:${it.udn}" }) { device ->
                val interactionSource = remember { MutableInteractionSource() }
                TvAwareButton(
                    onClick = { onSelectDevice(device) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().focusHighlight(interactionSource)
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null)
                        Column {
                            Text(device.displayName)
                            Text("DLNA", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CastTransportControls(state: CastUiState, onTogglePlayPause: () -> Unit, onSeekBy: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.player_cast_playing_on, state.deviceName))
        if (state.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${formatCastTime(state.positionMs)} / ${formatCastTime(state.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TvAwareIconButton(onClick = { onSeekBy(-10_000L) }) {
                    Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.player_cast_rewind))
                }
                TvAwareIconButton(onClick = onTogglePlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.player_cast_pause else R.string.player_cast_play
                        )
                    )
                }
                TvAwareIconButton(onClick = { onSeekBy(10_000L) }) {
                    Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.player_cast_forward))
                }
            }
        }
    }
}

internal fun formatCastTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
