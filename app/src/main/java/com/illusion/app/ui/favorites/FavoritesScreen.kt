package com.illusion.app.ui.favorites

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.illusion.app.R
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.WatchProgressRepository
import com.illusion.app.ui.common.PosterCard
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.posterGridColumns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: FavoritesViewModel = viewModel(
        factory = FavoritesViewModel.factory(libraryRepository, watchProgressRepository)
    )
    val items by viewModel.items.collectAsState()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = com.illusion.app.ui.common.tvSafeContentWindowInsets(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                windowInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.favorites_title)) },
                navigationIcon = {
                    com.illusion.app.ui.common.TvAwareIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.favorites_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = posterGridColumns(),
                modifier = Modifier.fillMaxSize().padding(innerPadding).focusGroup(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(items, key = { it.stableId }) { item ->
                    PosterCard(item = item, onClick = { onOpenItem(item.stableId) }, modifier = Modifier.padding(4.dp).animateItem())
                }
            }
        }
    }
}
