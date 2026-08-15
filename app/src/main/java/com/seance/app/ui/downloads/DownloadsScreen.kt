package com.seance.app.ui.downloads

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seance.app.R
import com.seance.app.data.download.DownloadStorage
import com.seance.app.data.image.posterModel
import com.seance.app.data.local.entity.DownloadEntity
import com.seance.app.data.local.entity.DownloadStatus
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.settings.SettingsRepository
import com.seance.app.ui.common.ThumbnailImage
import com.seance.app.ui.common.focusHighlight

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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    val backSource = remember { MutableInteractionSource() }
                    IconButton(onClick = onBack, interactionSource = backSource, modifier = Modifier.focusHighlight(backSource)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
                    }
                },
                actions = {
                    val folderSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { context.startActivity(DownloadStorage.openFolderIntent(context, downloadsFolder)) },
                        interactionSource = folderSource,
                        modifier = Modifier.focusHighlight(folderSource)
                    ) {
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
                            Text(entry.item.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                downloadStatusLabel(entry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (entry.download.status == DownloadStatus.DOWNLOADING || entry.download.status == DownloadStatus.QUEUED) {
                                LinearProgressIndicator(
                                    progress = { downloadProgressFraction(entry.download) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                        val removeSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { viewModel.removeDownload(context, entry.download.stableId) },
                            interactionSource = removeSource,
                            modifier = Modifier.focusHighlight(removeSource)
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
