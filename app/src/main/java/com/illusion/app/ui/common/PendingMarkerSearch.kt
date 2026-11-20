package com.illusion.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import com.illusion.app.data.credits.AutoPostCreditsQueue
import com.illusion.app.data.intro.AutoIntroQueue
import com.illusion.app.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The offer to search for markers in what a library scan found: how many seasons and films are
 * waiting (AutoIntroQueue / AutoPostCreditsQueue), and a button that starts the background search
 * over them. Shown on the scan's result screen ([dismissible] - "not now" hides it there) and in
 * Settings, where it stays for as long as anything is waiting - that's the way back for whoever
 * said "not now" or wasn't looking at the result screen at all.
 *
 * Asked rather than started outright (which is what the scan did until 2026-11-20): a first scan
 * of a big library queues hours of reading the server. Draws nothing when nothing is waiting.
 * While the search runs it turns into a progress note - the counts fall as the queues drain.
 */
@Composable
fun PendingMarkerSearchCard(refreshKey: Any?, modifier: Modifier = Modifier, dismissible: Boolean = false) {
    val context = LocalContext.current.applicationContext
    val workInfos by remember { WorkScheduler.pendingMarkerSearchWorkInfo(context) }.collectAsState(initial = emptyList())
    val running = workInfos.any { !it.state.isFinished }
    // Re-read whenever the work moves on (a finished worker, a progress tick) - that's when the
    // queues have changed.
    val pending by produceState(0 to 0, refreshKey, workInfos) {
        value = withContext(Dispatchers.IO) { AutoIntroQueue(context).count() to AutoPostCreditsQueue(context).count() }
    }
    var dismissed by remember(refreshKey) { mutableStateOf(false) }
    val (seasons, films) = pending
    if (seasons + films == 0) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(stringResource(R.string.markers_pending_title), style = MaterialTheme.typography.titleSmall)
        Text(
            when {
                running -> stringResource(R.string.markers_pending_running, seasons, films)
                dismissed -> stringResource(R.string.markers_pending_later_hint)
                else -> stringResource(R.string.markers_pending_text, seasons, films)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (!running && !dismissed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                TvAwareButton(onClick = { WorkScheduler.enqueuePendingMarkerSearch(context) }) {
                    Text(stringResource(R.string.markers_pending_start))
                }
                if (dismissible) {
                    TvAwareTextButton(onClick = { dismissed = true }) {
                        Text(stringResource(R.string.markers_pending_later))
                    }
                }
            }
        }
    }
}
