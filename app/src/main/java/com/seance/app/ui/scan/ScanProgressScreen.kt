package com.seance.app.ui.scan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.seance.app.R
import com.seance.app.data.scan.ScanProgress
import com.seance.app.ui.common.focusHighlight
import com.seance.app.work.LibraryScanWorker
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

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
    val isScanning = phase != ScanPhase.SUCCEEDED && phase != ScanPhase.FAILED

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isScanning) {
                ScanIllustration()
            }

            Text(stringResource(R.string.scan_progress_title))

            if (phase == ScanPhase.INDEXING && progress != null) {
                LinearProgressIndicator(
                    progress = { progress.filesScanned.toFloat() / progress.filesTotal },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
            } else if (isScanning) {
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
                    workInfo?.outputData?.getString(LibraryScanWorker.KEY_PARTIAL_ERROR)?.let { partialError ->
                        Text(
                            stringResource(R.string.scan_progress_partial_error, partialError),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    val continueSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = onComplete,
                        interactionSource = continueSource,
                        modifier = Modifier.padding(top = 16.dp).focusHighlight(continueSource)
                    ) {
                        Text(stringResource(R.string.scan_progress_continue))
                    }
                }
                ScanPhase.FAILED -> {
                    val errorMessage = workInfo?.outputData?.getString(LibraryScanWorker.KEY_ERROR)
                    Text(
                        errorMessage ?: stringResource(R.string.scan_progress_failed),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    val continueSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = onComplete,
                        interactionSource = continueSource,
                        modifier = Modifier.padding(top = 16.dp).focusHighlight(continueSource)
                    ) {
                        Text(stringResource(R.string.scan_progress_continue))
                    }
                }
            }

            if (isScanning) {
                ScanTipsCarousel(modifier = Modifier.padding(top = 32.dp))
            }
        }
    }
}

/** Slowly breathing library icon - a scan can take a while on a big share, a plain progress bar on an otherwise empty screen read as the app having frozen. Purely decorative, no data behind it. */
@Composable
private fun ScanIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scanIllustration")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanIllustrationScale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanIllustrationAlpha"
    )
    Box(
        modifier = modifier
            .padding(bottom = 24.dp)
            .size(96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.VideoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp)
        )
    }
}

/**
 * Rotates through a "did you know" tip about the app every few seconds - a scan can take a while
 * with nothing else on screen to read in the meantime. A real HorizontalPager (not just a timed
 * fade) so the user can swipe through tips at their own pace instead of waiting out the auto-
 * advance interval - per feedback that the fixed-timer version felt too fast to actually read.
 */
@Composable
private fun ScanTipsCarousel(modifier: Modifier = Modifier) {
    val tips = stringArrayResource(R.array.scan_tips)
    val pagerState = rememberPagerState(pageCount = { tips.size })

    LaunchedEffect(tips) {
        while (isActive) {
            delay(6000)
            val nextPage = (pagerState.currentPage + 1) % tips.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Fixed height (not wrap-content) so the card doesn't resize/jump as the user swipes
        // between a short tip and a longer one.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
        ) { page ->
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(
                    tips[page],
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            repeat(tips.size) { page ->
                val isCurrent = page == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }
}
