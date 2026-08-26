package com.illusion.app.ui.scan

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.illusion.app.R
import com.illusion.app.data.scan.ScanProgress
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.work.LibraryScanWorker
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

private enum class ScanPhase { STARTING, LISTING, INDEXING, SUCCEEDED, FAILED }

@Composable
fun ScanProgressScreen(
    workId: String,
    onComplete: () -> Unit,
    /** Leaves this screen for the library while the scan (a WorkManager job, entirely independent of this screen) keeps running in the background - per feedback, someone who just wants to watch something shouldn't be stuck staring at a progress bar until it finishes. */
    onDismiss: () -> Unit,
    /** False for the very first scan straight out of onboarding - the library is still empty, so there's nothing to "watch while it scans" yet. */
    allowDismiss: Boolean = true,
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
        Box(modifier = Modifier.fillMaxSize()) {
            // Purely decorative - a plain flat background made the previous version of this screen
            // read as empty/placeholder-like during a long scan, per feedback asking for "some
            // background imagery" here. A soft two-color glow (primary/tertiary, both already
            // theme- and accent-aware) rather than a photo/illustration asset, since a scan can
            // start from any of this app's accent colors and light/dark themes - a fixed image
            // would clash with half of them.
            ScanBackground()
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

            // Each phase's text (and the SUCCEEDED/FAILED button) used to hard-cut in with no
            // transition of its own - per feedback this read as too abrupt between STARTING/
            // LISTING/INDEXING/done. Crossfade (not AnimatedContent) since these phases differ in
            // line count/whether a button is present - a fade needs no shared layout to reconcile
            // between them, unlike a slide/size transform would. Keyed on `phase` alone, not
            // `progress` - the numbers ticking up within LISTING/INDEXING must NOT restart this
            // fade on every single update.
            // Default Crossfade duration (300ms) plus an instant, unanimated height jump between
            // phases of different content (a one-line STARTING text vs. LISTING's two lines vs.
            // SUCCEEDED's extra button) together still read as an abrupt cut - per feedback.
            // Slower tween + animateContentSize so the surrounding box resizes smoothly alongside
            // the fade instead of snapping to the new phase's height the instant it's targeted.
            // alignment = Center (not the default TopStart) - the outer Column centers this whole
            // Crossfade horizontally based on its current (animating) width every frame, while
            // animateContentSize's own default TopStart anchor keeps the content pinned to its
            // OWN top-left corner as that width shrinks/grows - those two competing centerings
            // made the SUCCEEDED text visibly slide in from the left edge instead of just fading,
            // per feedback. Matching alignments removes the conflict.
            Crossfade(
                targetState = phase,
                animationSpec = tween(500),
                modifier = Modifier.animateContentSize(alignment = Alignment.Center),
                label = "scanPhase"
            ) { currentPhase ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (currentPhase == ScanPhase.INDEXING && progress != null) {
                        LinearProgressIndicator(
                            progress = { progress.filesScanned.toFloat() / progress.filesTotal },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        )
                    } else if (currentPhase != ScanPhase.SUCCEEDED && currentPhase != ScanPhase.FAILED) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                    }

                    when (currentPhase) {
                        ScanPhase.STARTING -> Text(
                            stringResource(R.string.scan_progress_starting),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        // `progress` (unlike `currentPhase`) is read live from the enclosing scope,
                        // not frozen to whatever it was when this branch's fade-out started -
                        // Crossfade keeps recomposing the outgoing content for the duration of the
                        // animation, and by then a later recomposition may have already moved
                        // `progress` on to null (phase advanced past LISTING/INDEXING to SUCCEEDED/
                        // FAILED). The old `progress!!` here crashed on exactly that transient
                        // window (real NPE, confirmed via a crash report on-device). Null-safe now -
                        // worst case is this fading-out text renders one frame blank, never a crash.
                        ScanPhase.LISTING -> progress?.let { p ->
                            Text(
                                stringResource(R.string.scan_progress_source, p.sourceIndex + 1, p.sourceCount, p.currentSourceName),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            Text(
                                stringResource(R.string.scan_progress_listing, p.filesScanned),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ScanPhase.INDEXING -> progress?.let { p ->
                            Text(
                                stringResource(R.string.scan_progress_source, p.sourceIndex + 1, p.sourceCount, p.currentSourceName),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            Text(
                                stringResource(R.string.scan_progress_files, p.filesScanned, p.filesTotal),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ScanPhase.SUCCEEDED -> {
                            val total = workInfo?.outputData?.getInt(LibraryScanWorker.KEY_TOTAL_INDEXED, 0) ?: 0
                            // fillMaxWidth + textAlign (not just the Column's own
                            // horizontalAlignment) - the enclosing Crossfade's animateContentSize
                            // animates the container's width from the previous phase's (narrower)
                            // content up to this text's own natural width, and centering relative
                            // to a still-growing container briefly rendered this pinned toward the
                            // left, only snapping to true center once the width animation caught
                            // up. Centering within a width that's already full from the first
                            // frame has nothing left to animate around.
                            Text(
                                stringResource(R.string.scan_progress_done_count, total),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            workInfo?.outputData?.getString(LibraryScanWorker.KEY_PARTIAL_ERROR)?.let { partialError ->
                                Text(
                                    stringResource(R.string.scan_progress_partial_error, partialError),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
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
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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
                }
            }

            if (isScanning) {
                ScanTipsCarousel(modifier = Modifier.padding(top = 32.dp))
                Row(modifier = Modifier.padding(top = 16.dp)) {
                    // Distinct from "уйти" (below) - that leaves the scan running in the background,
                    // this actually cancels the WorkManager job. Sources already fully scanned
                    // earlier in this same run keep whatever they already persisted (see
                    // LibraryScanner's own per-source upsertAll timing) - nothing to roll back,
                    // same end state as the app being killed mid-scan.
                    val stopSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = {
                            com.illusion.app.work.WorkScheduler.cancelOneTimeScan(context)
                            onDismiss()
                        },
                        interactionSource = stopSource,
                        modifier = Modifier.focusHighlight(stopSource)
                    ) {
                        Text(stringResource(R.string.scan_progress_stop))
                    }
                    if (allowDismiss) {
                        val dismissSource = remember { MutableInteractionSource() }
                        TextButton(
                            onClick = onDismiss,
                            interactionSource = dismissSource,
                            modifier = Modifier.focusHighlight(dismissSource)
                        ) {
                            Text(stringResource(R.string.scan_progress_dismiss))
                        }
                    }
                }
            }
            }
        }
    }
}

/** Two soft, off-center glows behind the scan content - see the call site's own comment for why this is a gradient, not a static image asset. drawBehind (not Modifier.background(Brush)) because the gradient's center/radius need the canvas's actual pixel size, only available inside a DrawScope. */
@Composable
private fun ScanBackground(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    androidx.compose.foundation.layout.Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.22f), androidx.compose.ui.graphics.Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.15f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(tertiary.copy(alpha = 0.18f), androidx.compose.ui.graphics.Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.8f),
                        radius = size.maxDimension * 0.5f
                    )
                )
            }
    )
}

/**
 * Slowly breathing brand mark - a scan can take a while on a big share, a plain progress bar on
 * an otherwise empty screen read as the app having frozen. Purely decorative, no data behind it.
 * Was a generic Material "library" icon in a rounded primaryContainer box - swapped for the app's
 * own perforated-film mark (same drawable as the launcher icon/splash) per feedback that the
 * stock icon looked out of place next to the rest of the app's branding, and this screen (the app
 * actively reading through files) is a fitting place for the film motif specifically.
 */
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
    Image(
        painter = painterResource(R.drawable.ic_mark_splash),
        contentDescription = null,
        modifier = modifier
            .padding(bottom = 24.dp)
            .size(96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
    )
}

/**
 * Rotates through a "did you know" tip about the app every few seconds - a scan can take a while
 * with nothing else on screen to read in the meantime. Auto-advance alone (a Crossfade, no
 * gesture) was tried per earlier feedback about the pager's slide reading as abrupt, but per
 * later feedback the user wanted to be able to page through the (now longer) tip list at their
 * own pace too, not just wait through the fixed interval - back to a real HorizontalPager, whose
 * own default page-to-page transition is a plain slide rather than anything more jarring, which
 * is what actually prompted the original swap.
 */
@Composable
private fun ScanTipsCarousel(modifier: Modifier = Modifier) {
    val tips = stringArrayResource(R.array.scan_tips)
    val pagerState = rememberPagerState(pageCount = { tips.size })

    // repeatOnLifecycle (not a plain LaunchedEffect) - backgrounding the app during a scan and
    // returning used to leave the old pager visibly torn between two tips (its delay() kept
    // ticking with no frames to animate across while stopped). Restarting fresh on RESUME avoids
    // reintroducing that with whatever timer state accumulated while backgrounded.
    //
    // Keyed on tips.size (a stable Int), NOT on `tips` itself - stringArrayResource returns a
    // fresh Array<String> on every call, and a plain Kotlin Array uses reference equality, so
    // keying on the array directly restarted this LaunchedEffect on every single recomposition of
    // this composable. During active scanning that's many times a second (the progress ticker
    // recomposes the whole screen on every file discovered) - each restart cancelled whatever
    // animateScrollToPage() was already mid-flight, which is exactly why the tip carousel was seen
    // animating partway and then freezing stuck between two tips instead of completing the slide,
    // repeating on every subsequent auto-advance too. tips.size never changes at runtime (a fixed
    // string-array resource), so this effect now only restarts on a real lifecycle change.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(tips.size, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(6000)
                // Continues auto-advancing from wherever the user last swiped to, rather than
                // fighting a manual page choice - animateScrollToPage always targets "one past
                // pagerState's own current page", so a manual swipe just becomes the new baseline.
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % tips.size)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Fixed height (not wrap-content) so the card doesn't resize/jump between a short tip and
        // a longer one.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                // Was 110dp with 20dp padding (70dp of actual content height) - the longest tip in
                // scan_tips wraps to 4 lines on a narrow phone at bodyMedium and got clipped mid-
                // word at that height. Bumped both the box and the text size down a notch so even
                // that tip has real margin, not just enough for the tips that happened to be short.
                .height(130.dp)
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tips[page],
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
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
