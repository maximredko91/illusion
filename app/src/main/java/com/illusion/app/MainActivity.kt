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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.illusion.app.data.crash.CrashReporter
import com.illusion.app.data.smb.LocalNetworkPermission
import com.illusion.app.ui.navigation.IllusionNavHost
import com.illusion.app.ui.player.PipController
import com.illusion.app.ui.theme.IllusionTheme
import com.illusion.app.ui.update.UpdatePrompt
import com.illusion.app.ui.update.UpdateViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    /**
     * Пульт на Android TV: экран плеера регистрирует здесь свой обработчик и первым получает
     * нажатия - см. [com.illusion.app.ui.player.PlayerKeyEvents]. Без этого на устройстве без
     * тачскрина панель управления показать было нечем.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (com.illusion.app.ui.player.PlayerKeyEvents.handler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

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
            // enableEdgeToEdge() выше сам решает, светлыми или тёмными рисовать значки строки состояния, и
            // смотрит при этом на ночной режим СИСТЕМЫ, а не на тему, выбранную в самом приложении. При
            // любом расхождении значки совпадали по цвету с фоном и пропадали: система в светлой теме плюс
            // «Чёрная» в настройках давали тёмные часы на чёрном фоне (оставался виден один значок зарядки,
            // он зелёный сам по себе), обратное сочетание - белые на белом. Привязываем их к теме
            // приложения - той же логикой, что в IllusionTheme.
            val darkTheme = when (themeMode) {
                com.illusion.app.domain.model.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.illusion.app.domain.model.ThemeMode.LIGHT -> false
                com.illusion.app.domain.model.ThemeMode.DARK,
                com.illusion.app.domain.model.ThemeMode.BLACK -> true
            }
            val view = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.SideEffect {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            IllusionTheme(themeMode = themeMode, accentColor = accentColor) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IllusionNavHost(
                        app = app,
                        modifier = Modifier.fillMaxSize()
                    )
                    CrashReportPrompt()
                    NotificationPermissionRequest()
                    LocalNetworkPermissionStartupRequest(app)
                    com.illusion.app.ui.common.AppSplashOverlay(app)
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
