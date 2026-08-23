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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.illusion.app.data.crash.CrashReporter
import com.illusion.app.ui.navigation.IllusionNavHost
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
            val accentColor by app.settingsRepository.accentColor.collectAsState(initial = com.illusion.app.domain.model.AccentColor.DEFAULT)
            val themeMode by app.settingsRepository.themeMode.collectAsState(initial = com.illusion.app.domain.model.ThemeMode.SYSTEM)
            IllusionTheme(themeMode = themeMode, accentColor = accentColor) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IllusionNavHost(
                        app = app,
                        modifier = Modifier.fillMaxSize()
                    )
                    CrashReportPrompt()
                    NotificationPermissionRequest()
                    AppSplashOverlay()
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
 * accepts a vector/animated-vector drawable, and vector drawables have no text support at all, so
 * the wordmark can't be baked into the OS splash itself the way the mark icon is. A rasterized PNG
 * branding image was the platform's other option, but that goes soft at higher densities where the
 * vector mark stays sharp - showing the same background+mark plus real Compose Text instead, right
 * as the OS splash hands off, reads as one continuous splash while keeping the wordmark vector-sharp.
 * Static (no re-run of the OS splash's own fade/scale-in) since that already played once.
 */
@Composable
private fun AppSplashOverlay() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(550)
        visible = false
    }
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(280))) {
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
            Text(
                stringResource(R.string.app_name).uppercase(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(PaddingValues(bottom = 60.dp)),
                color = colorResource(R.color.illusion_ink_on_bg),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                letterSpacing = 0.22.em
            )
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
