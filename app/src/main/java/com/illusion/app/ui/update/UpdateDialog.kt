package com.illusion.app.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import com.illusion.app.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.illusion.app.data.update.UpdateInfo

/**
 * Hosted once at the top level (see MainActivity) so it can appear over any screen. Three stages,
 * driven entirely by [UpdateViewModel.state]: "what's new" (offer to update/postpone/skip),
 * downloading (progress), and "ready to install".
 */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsState()
    var installPromptDismissed by remember(uiState.downloadedFile) { mutableStateOf(false) }
    // Both of these used to swallow a failed startActivity() with a bare runCatching{} - on a
    // device with no package-installer UI at all (some Android TV boxes), pressing "Установить"
    // then did visibly nothing, leaving the user to assume it hadn't worked and back out/retry the
    // whole update from scratch. Surfacing the failure explicitly at least tells them why, instead
    // of a silent no-op that looked indistinguishable from a hang.
    var launchFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.installIntent.collect { intent ->
            runCatching { context.startActivity(intent) }.onFailure { launchFailed = true }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.permissionSettingsIntent.collect { intent ->
            runCatching { context.startActivity(intent) }.onFailure { launchFailed = true }
        }
    }
    if (launchFailed) {
        AlertDialog(
            onDismissRequest = { launchFailed = false },
            title = { Text(stringResource(R.string.update_launch_failed_title)) },
            text = { Text(stringResource(R.string.update_launch_failed_text)) },
            confirmButton = { TextButton(onClick = { launchFailed = false }) { Text(stringResource(R.string.update_launch_failed_ok)) } }
        )
    }

    val update = uiState.update
    // A mandatory release (see UpdateInfo.mandatory's own KDoc) skips every "not now" exit at
    // every stage - no Skip/Later on the offer, no Later once downloaded, and the dialog itself
    // can't be dismissed by tapping outside/back. Deliberately doesn't block the rest of the app
    // (that would need its own dedicated blocking screen) - just removes every way to defer this
    // one dialog without acting on it.
    val mandatory = update?.mandatory == true
    when {
        // Checked first so a failed download is never silently masked by another branch (it used
        // to just fall through straight back to WhatsNewDialog below, with the failure itself
        // dropped on the floor - see UpdateViewModel.dismissError's own KDoc).
        uiState.error != null -> DownloadErrorDialog(
            message = uiState.error!!,
            mandatory = mandatory,
            onRetry = { viewModel.dismissError(); viewModel.startDownload() },
            onDismiss = { viewModel.dismissError() },
            onOpenReleasesPage = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            ("https://github.com/maximredko91/illusion/releases/latest").toUri()
                        )
                    )
                }
            }
        )
        uiState.downloadedFile != null && !installPromptDismissed -> InstallReadyDialog(
            mandatory = mandatory,
            onInstall = { viewModel.install() },
            onLater = { installPromptDismissed = true }
        )
        uiState.isDownloading -> DownloadProgressDialog(
            progress = uiState.downloadProgress,
            onCancel = { viewModel.cancelDownload() }
        )
        update != null -> WhatsNewDialog(
            update = update,
            onUpdate = { viewModel.startDownload() },
            onLater = { viewModel.dismissForNow() },
            onSkip = { viewModel.skipThisVersion() }
        )
    }
}

@Composable
private fun WhatsNewDialog(update: UpdateInfo, onUpdate: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = if (update.mandatory) {{}} else onLater,
        title = {
            Text(
                stringResource(
                    if (update.mandatory) R.string.update_mandatory_title else R.string.update_available_title,
                    update.versionName
                )
            )
        },
        text = {
            if (update.releaseNotes.isBlank()) {
                Text(stringResource(R.string.update_no_release_notes))
            } else {
                Text(
                    update.releaseNotes,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            Row {
                if (!update.mandatory) TextButton(onClick = onSkip) { Text(stringResource(R.string.update_skip_version)) }
                TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_action_update)) }
            }
        },
        dismissButton = if (update.mandatory) null else {
            { TextButton(onClick = onLater) { Text(stringResource(R.string.update_action_later)) } }
        }
    )
}

@Composable
private fun DownloadProgressDialog(progress: Float?, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.update_downloading_title)) },
        text = {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.update_download_percent, (progress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        // Was no way out of this dialog at all if the download stalled (real report: "висит на
        // 1%") short of force-closing the app - now bounded anyway by callTimeout on the HTTP
        // client (see UpdateDownloadWorker), but this is the immediate, user-driven escape hatch.
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/**
 * If in-app download keeps failing (a persistent network/timeout issue on the device's own
 * connection, not a one-off) retrying forever never gets the user anywhere - especially bad for a
 * mandatory release, which otherwise has no other escape hatch. Surfaces the same manual-download
 * fallback [UpdatePrompt]'s launchFailed dialog already points to (GitHub releases page), so
 * there's always a way out even when the automatic path is stuck.
 */
@Composable
private fun DownloadErrorDialog(
    message: String,
    mandatory: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenReleasesPage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_download_error_title)) },
        text = {
            Column {
                Text(message)
                if (mandatory) {
                    Text(
                        stringResource(R.string.update_download_error_manual_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onRetry) { Text(stringResource(R.string.update_action_retry)) } },
        dismissButton = {
            Row {
                if (mandatory) {
                    TextButton(onClick = onOpenReleasesPage) { Text(stringResource(R.string.update_open_github)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        }
    )
}

@Composable
private fun InstallReadyDialog(mandatory: Boolean, onInstall: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = if (mandatory) {{}} else onLater,
        title = { Text(stringResource(R.string.update_ready_title)) },
        text = { Text(stringResource(R.string.update_ready_text)) },
        confirmButton = { TextButton(onClick = onInstall) { Text(stringResource(R.string.update_action_install)) } },
        dismissButton = if (mandatory) null else {
            { TextButton(onClick = onLater) { Text(stringResource(R.string.update_action_later)) } }
        }
    )
}
