package com.seance.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seance.app.R
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.ui.common.PosterCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    continueWatching: List<MediaItemEntity>,
    recentlyAdded: List<MediaItemEntity>,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenFavorites) {
                        Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.favorites_title))
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.history_title))
                    }
                    IconButton(onClick = onOpenDownloads) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.downloads_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (continueWatching.isNotEmpty()) {
                MediaCarousel(
                    title = stringResource(R.string.home_continue_watching),
                    items = continueWatching,
                    onOpenItem = onOpenItem
                )
            }
            MediaCarousel(
                title = stringResource(R.string.home_recently_added),
                items = recentlyAdded,
                onOpenItem = onOpenItem
            )
        }
    }
}

@Composable
private fun MediaCarousel(
    title: String,
    items: List<MediaItemEntity>,
    onOpenItem: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(items) { item ->
                PosterCard(
                    item = item,
                    onClick = { onOpenItem(item.stableId) },
                    modifier = Modifier.width(120.dp)
                )
            }
        }
    }
}
