package com.illusion.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.illusion.app.data.crash.CrashReporter
import com.illusion.app.ui.common.PerforationStrip
import com.illusion.app.ui.navigation.IllusionNavHost
import com.illusion.app.work.WorkScheduler
import com.illusion.app.ui.player.PipController
import com.illusion.app.ui.theme.IllusionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must come before super.onCreate() per the library's own contract - it reads the
        // Theme.Illusion.Splash attributes set on this activity in the manifest and swaps to
        // Theme.Illusion (postSplashScreenTheme) once the splash exits, which happens automatically
        // on first frame drawn - nothing here needs to hold it open manually, this app has no
        // synchronous startup work slow enough to be worth stalling on.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as IllusionApplication
        setContent {
            val accentColor by app.settingsRepository.accentColor.collectAsState(initial = com.illusion.app.domain.model.AccentColor.ILLUSION)
            val themeMode by app.settingsRepository.themeMode.collectAsState(initial = com.illusion.app.domain.model.ThemeMode.SYSTEM)
            IllusionTheme(themeMode = themeMode, accentColor = accentColor) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IllusionNavHost(
                        app = app,
                        modifier = Modifier.fillMaxSize()
                    )
                    CrashReportPrompt()
                    NotificationPermissionRequest()
                    AppSplashOverlay(app)
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipController.isPlayerActive) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(PipController.aspectRatio)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.isInPipMode = isInPictureInPictureMode
    }

    // onStop() only fires here while isInPipMode is still true when the PiP window itself has gone
    // away (normal PiP keeps the activity STARTED the whole time the small window is visible) - see
    // PipController.onPipClosed's KDoc for why this can't just rely on the activity finishing.
    override fun onStop() {
        super.onStop()
        if (PipController.isInPipMode) {
            PipController.onPipClosed?.invoke()
        }
    }
}

/**
 * Continues the OS splash for a short moment on the Compose side, adding the "ИЛЛЮЗИОН" wordmark
 * the native splash can't show - androidx core-splashscreen's windowSplashScreenAnimatedIcon only
 * accepts a vector drawable, and vector drawables have no text support at all, so the wordmark
 * can't be baked into the OS splash itself the way the mark icon is. A rasterized PNG branding
 * image was the platform's other option, but that goes soft at higher densities where the vector
 * mark stays sharp - showing the same background+mark plus real Compose Text instead, right as the
 * OS splash hands off, reads as one continuous splash while keeping the wordmark vector-sharp.
 * The icon itself is static (no entrance animation) and so is the OS splash's own icon
 * (@drawable/ic_mark_splash, not wrapped in an animated-vector) - two independent animations with
 * no shared clock kept landing out of step with each other (the icon visibly jumping in size at
 * the handoff, per feedback) since the OS splash dismisses on first frame drawn, which can land
 * mid-animation. That fix stands - the icon never moves.
 *
 * The wordmark itself, though, flies from its centered splash position to approximately where
 * HomeScreen's real TopAppBar title sits (top-left), then this whole overlay fades out to reveal
 * that real title underneath. This is a self-contained animation, not a true sharedElement
 * transition to HomeScreen's actual title composable - a real shared element was tried first, but
 * on-device testing showed visibly broken output (garbled overlapping text) because HomeScreen's
 * title is already fully composed and settled well before this overlay's exit even starts (nav
 * routes to Tabs almost immediately, long before the delay below elapses), so sharedElement had no
 * real "entering" partner to synchronize against and just snapped bounds instantly instead of
 * animating. Doing the flight self-contained (one Compose clock, no cross-composable coordination)
 * avoids that failure mode entirely, at the cost of the landing position being an approximation
 * (computed from known TopAppBar/status-bar constants below) rather than pixel-perfect.
 */
@Composable
private fun AppSplashOverlay(app: IllusionApplication) {
    var visible by remember { mutableStateOf(true) }
    // Only worth flying the wordmark toward Home's corner if we're actually about to land on Home -
    // mirrors the exact same check Destination.Splash's own LaunchedEffect makes. First run
    // (no sources yet -> Onboarding) or a resumed scan (-> ScanProgress) just fades out in place,
    // same as before this feature.
    var willLandOnHome by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val runningScanWorkId = WorkScheduler.runningOneTimeScanWorkId(app)
        willLandOnHome = runningScanWorkId == null && app.smbSourceRepository.observeSources().first().isNotEmpty()
    }

    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    // Standard M3 TopAppBar height - matches HomeScreen's TopAppBar (no custom height there).
    val topAppBarHeightPx = with(density) { 64.dp.toPx() }
    val targetLeftPx = with(density) { 16.dp.toPx() }
    val targetCenterYPx = statusBarTopPx + topAppBarHeightPx / 2f
    // The Text/PerforationStrip below are laid out at their real, final, Home-matching values
    // (14sp/2dp strip/2dp padding - see HomeScreen.kt's title slot) from the very first frame and
    // never change size at all - only position animates. Three earlier approaches all fought this
    // in different ways: animating real fontSize/padding every frame reshaped the text each frame,
    // expensive enough (stacked on Home's own cold-start work happening at the same moment) to
    // visibly stutter; a graphicsLayer scale on the original 20sp/4dp/8dp splash values looked fine
    // mid-flight but didn't match Home's real proportions at the end; scaling the correct final
    // values down to match measured smooth and pixel-accurate on every frame extracted from a
    // recording, but still read as a glitchy "pop" live on-device - screen recording apparently
    // doesn't capture whatever was actually going wrong. No scale factor at all sidesteps the whole
    // class of bug rather than chasing it further - just a straight line to the target position.
    var startCenter by remember { mutableStateOf<Offset?>(null) }
    var columnWidthPx by remember { mutableStateOf(0f) }
    val flightProgress = remember { Animatable(0f) }

    LaunchedEffect(startCenter, willLandOnHome) {
        if (startCenter == null) return@LaunchedEffect
        delay(500)
        if (willLandOnHome) {
            flightProgress.animateTo(1f, animationSpec = tween(450, easing = FastOutSlowInEasing))
            delay(80)
        } else {
            delay(150)
        }
        visible = false
    }

    // No crossfade at all when landing on Home - even a short one exposed a real (not just
    // perceived) pixel-level mismatch between the landed wordmark and HomeScreen's actual title
    // (their vertical metrics don't come out byte-identical despite using the same declared sp/dp
    // values - Text line-height/font-metrics rounding is finicky like that), which during any
    // blend window visibly read as the perforated strip's top/bottom lines pulling apart before
    // settling. An instant cut has no blend window for that mismatch to show in at all - since the
    // landed frame and Home's real frame are close, a hard swap reads as arriving, not swapping.
    // The non-Home fallback (plain disappear, nothing real underneath to line up with) keeps its
    // smooth fade.
    val exit = if (willLandOnHome) ExitTransition.None else fadeOut(tween(220))
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = exit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.splash_bg)),
            contentAlignment = Alignment.Center
        ) {
            // Fades out MUCH faster than the wordmark's own 450ms flight (fully gone within the
            // first ~17% of it, not linearly over the whole flight) - pixel-measured across dense
            // native-framerate camera footage of the live device (screen-capture recordings didn't
            // reproduce this at all) that the flight path crosses directly over this static icon's
            // own left strip roughly a quarter of the way through the flight, and the icon has its
            // own copy of the same crimson perforated-strip motif (ic_mark.xml, flanking the "И"
            // glyph). A x3 fade (gone by 33%) still measured ~28% opacity at that ~24%-progress
            // crossing point - visibly non-transparent, reading as one strip splitting into two;
            // x6 finishes well before the crossing with margin to spare. The icon itself never
            // moves or resizes (that's a deliberate, separate decision - see this file's own notes
            // on the OS-splash handoff), only alpha changes here, so this doesn't reopen that.
            Image(
                painter = painterResource(R.drawable.ic_mark_splash),
                contentDescription = null,
                modifier = Modifier.graphicsLayer { alpha = (1f - flightProgress.value * 6f).coerceIn(0f, 1f) }
            )
            // Framed top/bottom by the same crimson perforated-strip motif as the launcher mark
            // (ic_mark.xml) and the collection "current" frame in PosterCard.kt - ties the
            // wordmark visually to the icon's own film-edge detail instead of being plain text.
            // width(IntrinsicSize.Min) makes the Column (and so the strips inside it, which
            // fillMaxWidth) measure to the wordmark's own width rather than the full screen.
            Column(
                horizontalAlignment = CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(PaddingValues(bottom = 60.dp))
                    .width(IntrinsicSize.Min)
                    .onGloballyPositioned { coords ->
                        if (startCenter == null) {
                            val pos = coords.positionInWindow()
                            startCenter = Offset(pos.x + coords.size.width / 2f, pos.y + coords.size.height / 2f)
                            columnWidthPx = coords.size.width.toFloat()
                        }
                    }
                    // State read deferred to this lambda (not the composable body) so only the
                    // compositing layer updates per animation frame, not a full recomposition -
                    // same reasoning as LocalShimmerProgress elsewhere in this codebase. Position
                    // only - no scale (see this composable's own KDoc for why).
                    .graphicsLayer {
                        val center = startCenter
                        if (center != null) {
                            val p = flightProgress.value
                            val targetCenterXPx = targetLeftPx + columnWidthPx / 2f
                            translationX = (targetCenterXPx - center.x) * p
                            translationY = (targetCenterYPx - center.y) * p
                        }
                    }
            ) {
                // Hidden for the whole flight itself, visible only while fully static - before the
                // flight starts and once it's fully landed. A thin strip moving fast is exactly the
                // kind of element that blurs/smears differently from solid text under real motion
                // (camera footage of the live device kept showing the strips looking detached from
                // the text mid-flight, something no frame-by-frame pixel measurement of that same
                // footage could ever catch, since a genuinely rigid transform can't move a child
                // relative to its siblings - this is a real-motion-perception issue, not a layout
                // bug). Removing the strips from the object in motion sidesteps that class of
                // problem entirely rather than continuing to chase it.
                val stripsVisible = flightProgress.value <= 0f || flightProgress.value >= 1f
                PerforationStrip(
                    holeColor = colorResource(R.color.splash_bg),
                    modifier = Modifier.fillMaxWidth().height(2.dp).graphicsLayer { alpha = if (stripsVisible) 1f else 0f }
                )
                Text(
                    stringResource(R.string.app_name).uppercase(),
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = colorResource(R.color.illusion_ink_on_bg),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 0.12.em,
                    maxLines = 1
                )
                PerforationStrip(
                    holeColor = colorResource(R.color.splash_bg),
                    modifier = Modifier.fillMaxWidth().height(2.dp).graphicsLayer { alpha = if (stripsVisible) 1f else 0f }
                )
            }
        }
    }
}

/** One-shot request for POST_NOTIFICATIONS (API 33+) so a background library rescan's result notification (see ScanNotifications) can actually show - a denial just means that notification silently doesn't appear, nothing else in the app depends on it. */
@Composable
private fun NotificationPermissionRequest() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Offers to share the previous run's crash log, if any - checked once per process start, not a recurring nag once dismissed or sent. */
@Composable
private fun CrashReportPrompt() {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf(CrashReporter.pendingReport(context)) }
    val file = pendingFile ?: return

    AlertDialog(
        onDismissRequest = { CrashReporter.clear(file); pendingFile = null },
        title = { Text(stringResource(R.string.crash_report_title)) },
        text = { Text(stringResource(R.string.crash_report_message)) },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(android.content.Intent.createChooser(CrashReporter.shareIntent(file), null))
                CrashReporter.clear(file)
                pendingFile = null
            }) { Text(stringResource(R.string.crash_report_send)) }
        },
        dismissButton = {
            TextButton(onClick = { CrashReporter.clear(file); pendingFile = null }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
