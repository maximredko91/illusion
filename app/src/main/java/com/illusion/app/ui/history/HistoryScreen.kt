package com.illusion.app.ui.history

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.illusion.app.R
import com.illusion.app.data.image.posterModel
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.WatchProgressRepository
import com.illusion.app.ui.common.ThumbnailImage
import com.illusion.app.ui.common.focusHighlight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(libraryRepository, watchProgressRepository)
    )
    val entries by viewModel.entries.collectAsState()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru")) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var pendingRemoveEntry by remember { mutableStateOf<HistoryEntry?>(null) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = com.illusion.app.ui.common.tvSafeContentWindowInsets(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                windowInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    com.illusion.app.ui.common.TvAwareIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        com.illusion.app.ui.common.TvAwareIconButton(onClick = { showClearAllConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_clear_all))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(entries, key = { it.item.stableId + "_" + it.progress.updatedAt }) { entry ->
                    val rowSource = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .focusHighlight(rowSource)
                            .clickable(interactionSource = rowSource, indication = LocalIndication.current) { onOpenItem(entry.item.stableId) }
                            .padding(8.dp)
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
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                dateFormatter.format(
                                    Instant.ofEpochMilli(entry.progress.updatedAt).atZone(ZoneId.systemDefault())
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val percent = if (entry.progress.durationMs > 0) {
                                (entry.progress.positionMs * 100 / entry.progress.durationMs).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            Text(
                                if (entry.progress.watched) {
                                    stringResource(R.string.history_watched)
                                } else {
                                    stringResource(R.string.history_progress, percent)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!entry.progress.watched) {
                                LinearProgressIndicator(
                                    progress = { percent / 100f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                        com.illusion.app.ui.common.TvAwareIconButton(onClick = { pendingRemoveEntry = entry }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_remove_item))
                        }
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
            confirmButton = {
                val confirmSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        showClearAllConfirm = false
                        viewModel.clearAll()
                    },
                    interactionSource = confirmSource,
                    modifier = Modifier.focusHighlight(confirmSource)
                ) {
                    Text(stringResource(R.string.history_clear_confirm_action))
                }
            },
            dismissButton = {
                val cancelSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showClearAllConfirm = false },
                    interactionSource = cancelSource,
                    modifier = Modifier.focusHighlight(cancelSource)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    pendingRemoveEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemoveEntry = null },
            title = { Text(stringResource(R.string.history_remove_confirm_title)) },
            text = { Text(stringResource(R.string.history_remove_confirm_message, entry.item.title)) },
            confirmButton = {
                val confirmSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        pendingRemoveEntry = null
                        viewModel.deleteEntry(entry.item.stableId)
                    },
                    interactionSource = confirmSource,
                    modifier = Modifier.focusHighlight(confirmSource)
                ) {
                    Text(stringResource(R.string.history_remove_confirm_action))
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
