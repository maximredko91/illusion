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

    LaunchedEffect(Unit) {
        viewModel.installIntent.collect { intent -> runCatching { context.startActivity(intent) } }
    }
    LaunchedEffect(Unit) {
        viewModel.permissionSettingsIntent.collect { intent -> runCatching { context.startActivity(intent) } }
    }

    val update = uiState.update
    when {
        uiState.downloadedFile != null && !installPromptDismissed -> InstallReadyDialog(
            onInstall = { viewModel.install() },
            onLater = { installPromptDismissed = true }
        )
        uiState.isDownloading -> DownloadProgressDialog(progress = uiState.downloadProgress)
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
        onDismissRequest = onLater,
        title = { Text("Доступно обновление ${update.versionName}") },
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
                TextButton(onClick = onSkip) { Text("Пропустить версию") }
                TextButton(onClick = onUpdate) { Text("Обновить") }
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text("Позже") }
        }
    )
}

@Composable
private fun DownloadProgressDialog(progress: Float?) {
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
        confirmButton = {}
    )
}

@Composable
private fun InstallReadyDialog(onInstall: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Обновление готово") },
        text = { Text("Новая версия скачана. Установить сейчас?") },
        confirmButton = { TextButton(onClick = onInstall) { Text("Установить") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Позже") } }
    )
}
