package com.illusion.app.ui.downloads

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.illusion.app.R
import com.illusion.app.data.download.DownloadStorage
import com.illusion.app.data.image.posterModel
import com.illusion.app.data.local.entity.DownloadEntity
import com.illusion.app.data.local.entity.DownloadStatus
import com.illusion.app.data.repository.DownloadRepository
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.ui.common.ThumbnailImage
import com.illusion.app.ui.common.focusHighlight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloadRepository: DownloadRepository,
    libraryRepository: LibraryRepository,
    settingsRepository: SettingsRepository,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: DownloadsViewModel = viewModel(
        factory = DownloadsViewModel.factory(downloadRepository, libraryRepository)
    )
    val entries by viewModel.entries.collectAsState()
    val downloadsFolder by settingsRepository.downloadsFolderUri.collectAsState(initial = null)
    // Only completed downloads get a confirmation - deleting one removes an actual local file, the
    // same stakes as the confirmation History already asks for on a plain watch-progress removal.
    // Cancelling a still-downloading/queued entry stays a single tap (already visually distinct -
    // its icon is Close, not Delete - and there's no finished file to lose).
    var pendingRemoveEntry by remember { mutableStateOf<DownloadEntry?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    com.illusion.app.ui.common.TvAwareIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
                    }
                },
                actions = {
                    com.illusion.app.ui.common.TvAwareIconButton(onClick = { context.startActivity(DownloadStorage.openFolderIntent(context, downloadsFolder)) }) {
                        Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.settings_downloads_open_folder))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.downloads_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(entries, key = { it.download.stableId }) { entry ->
                    val rowSource = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .focusHighlight(rowSource)
                            .clickable(
                                enabled = entry.download.status == DownloadStatus.COMPLETED,
                                interactionSource = rowSource,
                                indication = LocalIndication.current
                            ) { onOpenItem(entry.item.stableId) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val poster = entry.item.posterModel
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .aspectRatio(2f / 3f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            ThumbnailImage(model = poster, contentDescription = entry.item.title)
                        }
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                entry.item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                downloadStatusLabel(entry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Recovered after a data clear/reinstall (DownloadRepository.recoverOrphanedDownloads) -
                            // title/year are only a guess from the folder name, no poster/plot/genres exist for it,
                            // so it's worth being upfront that this isn't a normal library entry.
                            if (entry.item.isOrphanedDownload) {
                                Text(
                                    stringResource(R.string.downloads_recovered),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (entry.download.status == DownloadStatus.DOWNLOADING || entry.download.status == DownloadStatus.QUEUED) {
                                LinearProgressIndicator(
                                    progress = { downloadProgressFraction(entry.download) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                        if (entry.download.status == DownloadStatus.FAILED) {
                            com.illusion.app.ui.common.TvAwareIconButton(onClick = { viewModel.retryDownload(context, entry.download.stableId) }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.downloads_resume))
                            }
                        }
                        com.illusion.app.ui.common.TvAwareIconButton(
                            onClick = {
                                if (entry.download.status == DownloadStatus.COMPLETED) {
                                    pendingRemoveEntry = entry
                                } else {
                                    viewModel.removeDownload(context, entry.download.stableId)
                                }
                            }
                        ) {
                            Icon(
                                if (entry.download.status == DownloadStatus.DOWNLOADING) Icons.Default.Close else Icons.Default.Delete,
                                contentDescription = stringResource(R.string.downloads_remove)
                            )
                        }
                    }
                }
            }
        }
    }

    pendingRemoveEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemoveEntry = null },
            title = { Text(stringResource(R.string.downloads_remove_confirm_title)) },
            text = { Text(stringResource(R.string.downloads_remove_confirm_message, entry.item.title)) },
            confirmButton = {
                val confirmSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        pendingRemoveEntry = null
                        viewModel.removeDownload(context, entry.download.stableId)
                    },
                    interactionSource = confirmSource,
                    modifier = Modifier.focusHighlight(confirmSource)
                ) {
                    Text(stringResource(R.string.downloads_remove))
                }
            },
            dismissButton = {
                val cancelSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { pendingRemoveEntry = null },
                    interactionSource = cancelSource,
                    modifier = Modifier.focusHighlight(cancelSource)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun downloadProgressFraction(download: DownloadEntity): Float {
    if (download.totalBytes <= 0) return 0f
    return (download.downloadedBytes.toFloat() / download.totalBytes.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun downloadStatusLabel(entry: DownloadEntry): String {
    val download = entry.download
    return when (download.status) {
        DownloadStatus.QUEUED -> stringResource(R.string.downloads_queued)
        DownloadStatus.DOWNLOADING -> {
            val percent = (downloadProgressFraction(download) * 100).toInt()
            val sizeLabel = "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}"
            if (entry.bytesPerSecond > 0) {
                stringResource(R.string.downloads_progress_detailed, percent, sizeLabel, formatSpeed(entry.bytesPerSecond))
            } else {
                stringResource(R.string.downloads_progress, percent, sizeLabel)
            }
        }
        DownloadStatus.COMPLETED -> stringResource(R.string.downloads_completed, formatBytes(download.totalBytes))
        DownloadStatus.FAILED -> download.errorMessage ?: stringResource(R.string.downloads_failed)
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f ГБ".format(mb / 1024) else "%.0f МБ".format(mb)
}

private fun formatSpeed(bytesPerSecond: Long): String {
    val mbps = bytesPerSecond / (1024.0 * 1024.0)
    return if (mbps >= 1) "%.1f МБ/с".format(mbps) else "%.0f КБ/с".format(bytesPerSecond / 1024.0)
}
