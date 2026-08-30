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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
            title = { Text("Не удалось запустить установку") },
            text = { Text("На этом устройстве нет приложения для установки APK. Скачайте файл вручную со страницы релизов на GitHub и установите через файловый менеджер.") },
            confirmButton = { TextButton(onClick = { launchFailed = false }) { Text("Понятно") } }
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
            onRetry = { viewModel.dismissError(); viewModel.startDownload() },
            onDismiss = { viewModel.dismissError() }
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
        title = { Text(if (update.mandatory) "Требуется обновление ${update.versionName}" else "Доступно обновление ${update.versionName}") },
        text = {
            if (update.releaseNotes.isBlank()) {
                Text("Список изменений не указан.")
            } else {
                Text(
                    update.releaseNotes,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            Row {
                if (!update.mandatory) TextButton(onClick = onSkip) { Text("Пропустить версию") }
                TextButton(onClick = onUpdate) { Text("Обновить") }
            }
        },
        dismissButton = if (update.mandatory) null else {
            { TextButton(onClick = onLater) { Text("Позже") } }
        }
    )
}

@Composable
private fun DownloadProgressDialog(progress: Float?, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Загрузка обновления") },
        text = {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${(progress * 100).toInt()}%",
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
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } }
    )
}

@Composable
private fun DownloadErrorDialog(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Не удалось скачать обновление") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onRetry) { Text("Повторить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun InstallReadyDialog(mandatory: Boolean, onInstall: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = if (mandatory) {{}} else onLater,
        title = { Text("Обновление готово") },
        text = { Text("Новая версия скачана. Установить сейчас?") },
        confirmButton = { TextButton(onClick = onInstall) { Text("Установить") } },
        dismissButton = if (mandatory) null else {
            { TextButton(onClick = onLater) { Text("Позже") } }
        }
    )
}
