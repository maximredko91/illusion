package com.seance.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.seance.app.data.crash.CrashReporter
import com.seance.app.ui.navigation.SeanceNavHost
import com.seance.app.ui.player.PipController
import com.seance.app.ui.theme.SeanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SeanceApplication
        setContent {
            val accentColor by app.settingsRepository.accentColor.collectAsState(initial = com.seance.app.domain.model.AccentColor.DEFAULT)
            SeanceTheme(accentColor = accentColor) {
                SeanceNavHost(
                    app = app,
                    modifier = Modifier.fillMaxSize()
                )
                CrashReportPrompt()
                NotificationPermissionRequest()
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
