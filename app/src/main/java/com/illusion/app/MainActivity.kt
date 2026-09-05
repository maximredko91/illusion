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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
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
import com.illusion.app.data.smb.LocalNetworkPermission
import com.illusion.app.ui.common.PerforationStrip
import com.illusion.app.ui.navigation.IllusionNavHost
import com.illusion.app.ui.player.PipController
import com.illusion.app.ui.theme.IllusionTheme
import com.illusion.app.ui.update.UpdatePrompt
import com.illusion.app.ui.update.UpdateViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

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
                    LocalNetworkPermissionStartupRequest(app)
                    AppSplashOverlay()
                    val updateViewModel: UpdateViewModel = viewModel(
                        viewModelStoreOwner = this@MainActivity,
                        factory = UpdateViewModel.factory(app, app.updateChecker, app.localUpdateChecker, app.settingsRepository)
                    )
                    LaunchedEffect(Unit) { updateViewModel.checkForUpdate() }
                    UpdatePrompt(updateViewModel)
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
    //
    // The else branch covers leaving the app WITHOUT PiP ever having been entered at all -
    // onPictureInPictureModeChanged() fires synchronously during a successful
    // enterPictureInPictureMode() call, well before the activity reaches onStop(), so isInPipMode
    // is a reliable signal here for "did PiP actually start" (not just "was requested"). Confirmed
    // on-device: swiping up to the recents/app-switcher overview left a video's audio running with
    // no PiP window ever showing - see PipController.onBackgroundedWithoutPip's own KDoc.
    override fun onStop() {
        super.onStop()
        if (PipController.isInPipMode) {
            PipController.onPipClosed?.invoke()
        } else {
            PipController.onBackgroundedWithoutPip?.invoke()
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
 * Everything here is static (no entrance animation on the icon or the wordmark) and so is the OS
 * splash's own icon (@drawable/ic_mark_splash, not wrapped in an animated-vector) - two independent
 * animations with no shared clock kept landing out of step with each other (the icon visibly
 * jumping in size at the handoff, per feedback) since the OS splash dismisses on first frame drawn,
 * which can land mid-animation. Nothing to fall out of sync once nothing moves.
 *
 * A wordmark-flies-to-Home's-TopAppBar-title animation was tried here at length (self-contained
 * position animation, several rounds of chasing a "perforation strips pulling apart from the text"
 * artifact that kept reappearing live on-device in ways no screen recording or frame-by-frame pixel
 * measurement ever reproduced) and was reverted at the user's explicit request back to this fully
 * static version - don't re-attempt it without being asked again.
 */
@Composable
private fun AppSplashOverlay() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(650)
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = fadeOut(tween(280))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.splash_bg)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_mark_splash),
                contentDescription = null
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
            ) {
                PerforationStrip(
                    holeColor = colorResource(R.color.splash_bg),
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
                Text(
                    stringResource(R.string.app_name).uppercase(),
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = colorResource(R.color.illusion_ink_on_bg),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    letterSpacing = 0.22.em
                )
                PerforationStrip(
                    holeColor = colorResource(R.color.splash_bg),
                    modifier = Modifier.fillMaxWidth().height(4.dp)
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

/**
 * Onboarding and the SMB add/edit forms only ever request [LocalNetworkPermission] as a side
 * effect of the developer tapping "test connection"/"save" on those specific screens - a source
 * added before this permission existed, or restored from backup, or with the permission later
 * revoked in system Settings, has no other prompt anywhere: Home's poster loading and the
 * background LibraryScanWorker can only check-and-fail (a Worker has no Activity to show a
 * permission dialog from at all), so without this the user would just see silent SMB timeouts
 * with no way to know why. One-shot per process start, and only when there's an actual source to
 * use the permission for - an empty library goes through onboarding's own request instead.
 */
@Composable
private fun LocalNetworkPermissionStartupRequest(app: IllusionApplication) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!LocalNetworkPermission.isGranted(context) &&
            app.smbSourceRepository.observeSources().first().isNotEmpty()
        ) {
            launcher.launch(LocalNetworkPermission.PERMISSION)
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
        onDismissRequest = { CrashReporter.clearAll(context); pendingFile = null },
        title = { Text(stringResource(R.string.crash_report_title)) },
        text = { Text(stringResource(R.string.crash_report_message)) },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(android.content.Intent.createChooser(CrashReporter.shareIntent(file), null))
                CrashReporter.clearAll(context)
                pendingFile = null
            }) { Text(stringResource(R.string.crash_report_send)) }
        },
        dismissButton = {
            TextButton(onClick = { CrashReporter.clearAll(context); pendingFile = null }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
