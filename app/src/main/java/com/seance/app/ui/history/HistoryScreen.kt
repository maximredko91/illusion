package com.seance.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seance.app.R
import com.seance.app.data.image.posterModel
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.WatchProgressRepository
import com.seance.app.ui.common.ThumbnailImage
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
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
                items(entries, key = { it.item.stableId + it.progress.updatedAt }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenItem(entry.item.stableId) }
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
                            Text(entry.item.title, style = MaterialTheme.typography.bodyLarge)
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
                    }
                }
            }
        }
    }
}
