package com.illusion.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.illusion.app.R
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.ui.common.focusHighlight

/** Full, sortable/filterable browser over every distinct raw .nfo <tag> in the library - see Destination.Tags's own comment for why this exists separately from Search's own capped inline chip row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    libraryRepository: LibraryRepository,
    onSelectTag: (tag: String, label: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: TagsViewModel = viewModel(factory = TagsViewModel.factory(libraryRepository))
    val tags by viewModel.tags.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = com.illusion.app.ui.common.tvSafeContentWindowInsets(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                windowInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.tags_title)) },
                navigationIcon = {
                    com.illusion.app.ui.common.TvAwareIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = filter,
                onValueChange = viewModel::setFilter,
                label = { Text(stringResource(R.string.tags_filter_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                com.illusion.app.ui.common.TvAwareSelectableChip(
                    selected = sortOrder == TagSortOrder.COUNT,
                    onClick = { viewModel.setSortOrder(TagSortOrder.COUNT) },
                    label = { Text(stringResource(R.string.tags_sort_by_count)) }
                )
                com.illusion.app.ui.common.TvAwareSelectableChip(
                    selected = sortOrder == TagSortOrder.ALPHABETICAL,
                    onClick = { viewModel.setSortOrder(TagSortOrder.ALPHABETICAL) },
                    label = { Text(stringResource(R.string.tags_sort_alphabetical)) }
                )
            }
            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                tags.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.tags_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(tags, key = { it.tag }) { entry ->
                        // No longer shows the raw English tag underneath the translated label -
                        // per feedback, an ordinary user shouldn't see any trace of this being a
                        // translation over English data at all, in the label or in the search
                        // query it lands on (see onSelectTag below).
                        ListItem(
                            headlineContent = { Text(entry.label) },
                            trailingContent = { Text(entry.count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.clickable { onSelectTag(entry.tag, entry.label) }
                        )
                    }
                }
            }
        }
    }
}
