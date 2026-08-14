package com.seance.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.seance.app.R
import com.seance.app.data.scan.ScanProgress
import com.seance.app.work.LibraryScanWorker
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest

private enum class ScanPhase { STARTING, LISTING, INDEXING, SUCCEEDED, FAILED }

@Composable
fun ScanProgressScreen(
    workId: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var workInfo by remember { mutableStateOf<WorkInfo?>(null) }

    LaunchedEffect(workId) {
        WorkManager.getInstance(context)
            .getWorkInfoByIdFlow(UUID.fromString(workId))
            .collectLatest { info -> workInfo = info }
    }

    val progress = workInfo?.progress?.let { ScanProgress.fromData(it) }
    val phase = when {
        workInfo?.state == WorkInfo.State.SUCCEEDED -> ScanPhase.SUCCEEDED
        workInfo?.state?.isFinished == true -> ScanPhase.FAILED
        progress == null -> ScanPhase.STARTING
        progress.filesTotal == ScanProgress.TOTAL_UNKNOWN -> ScanPhase.LISTING
        else -> ScanPhase.INDEXING
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.scan_progress_title))

            if (phase == ScanPhase.INDEXING && progress != null) {
                LinearProgressIndicator(
                    progress = { progress.filesScanned.toFloat() / progress.filesTotal },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
            } else if (phase != ScanPhase.SUCCEEDED && phase != ScanPhase.FAILED) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            }

            when (phase) {
                ScanPhase.STARTING -> Text(
                    stringResource(R.string.scan_progress_starting),
                    modifier = Modifier.padding(top = 8.dp)
                )
                ScanPhase.LISTING -> {
                    Text(
                        stringResource(
                            R.string.scan_progress_source,
                            progress!!.sourceIndex + 1,
                            progress.sourceCount,
                            progress.currentSourceName
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(stringResource(R.string.scan_progress_listing, progress.filesScanned))
                }
                ScanPhase.INDEXING -> {
                    Text(
                        stringResource(
                            R.string.scan_progress_source,
                            progress!!.sourceIndex + 1,
                            progress.sourceCount,
                            progress.currentSourceName
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(stringResource(R.string.scan_progress_files, progress.filesScanned, progress.filesTotal))
                }
                ScanPhase.SUCCEEDED -> {
                    val total = workInfo?.outputData?.getInt(LibraryScanWorker.KEY_TOTAL_INDEXED, 0) ?: 0
                    Text(
                        stringResource(R.string.scan_progress_done_count, total),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Button(onClick = onComplete, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.scan_progress_continue))
                    }
                }
                ScanPhase.FAILED -> {
                    Text(
                        stringResource(R.string.scan_progress_failed),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Button(onClick = onComplete, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.scan_progress_continue))
                    }
                }
            }
        }
    }
}
