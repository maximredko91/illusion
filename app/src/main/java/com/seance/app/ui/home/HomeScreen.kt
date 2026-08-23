package com.seance.app.ui.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seance.app.R
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.ui.common.PosterCard
import com.seance.app.ui.common.focusHighlight
import com.seance.app.ui.common.posterCardMinWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    continueWatching: List<ContinueWatchingItem>,
    randomPicks: List<MediaItemEntity>,
    onRefreshRandomPicks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        // Scaffold's own default (WindowInsets.systemBars) would otherwise reserve bottom
        // navigation-bar space a second time on top of what SeanceNavHost's outer Scaffold
        // already reserves for its real NavigationBar (same fix as LibraryScreen - see its own
        // comment on this line for the full explanation), leaving a dead gap between this
        // screen's scrollable content and the visible nav bar underneath it.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = com.seance.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    com.seance.app.ui.common.TooltipIconButton(stringResource(R.string.nav_search), Icons.Default.Search, onOpenSearch)
                    com.seance.app.ui.common.TooltipIconButton(stringResource(R.string.favorites_title), Icons.Default.Favorite, onOpenFavorites)
                    com.seance.app.ui.common.TooltipIconButton(stringResource(R.string.history_title), Icons.Default.History, onOpenHistory)
                    com.seance.app.ui.common.TooltipIconButton(stringResource(R.string.downloads_title), Icons.Default.Download, onOpenDownloads)
                    com.seance.app.ui.common.TooltipIconButton(stringResource(R.string.settings_title), Icons.Default.Settings, onOpenSettings)
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (continueWatching.isNotEmpty()) {
                MediaCarousel(
                    title = stringResource(R.string.home_continue_watching),
                    items = continueWatching.map { it.item },
                    onOpenItem = onOpenItem,
                    progressByStableId = remember(continueWatching) {
                        continueWatching.mapNotNull { entry -> entry.progressFraction?.let { entry.item.stableId to it } }.toMap()
                    }
                )
            }
            MediaCarousel(
                title = stringResource(R.string.home_random_picks),
                items = randomPicks,
                onOpenItem = onOpenItem,
                onRefresh = onRefreshRandomPicks
            )
        }
    }
}

@Composable
private fun MediaCarousel(
    title: String,
    items: List<MediaItemEntity>,
    onOpenItem: (String) -> Unit,
    onRefresh: (() -> Unit)? = null,
    progressByStableId: Map<String, Float> = emptyMap()
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Text(title, modifier = Modifier.weight(1f))
            if (onRefresh != null) {
                com.seance.app.ui.common.TooltipIconButton(
                    stringResource(R.string.home_random_picks_refresh),
                    Icons.Default.Refresh,
                    onRefresh
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.focusGroup()
        ) {
            items(items, key = { it.stableId }) { item ->
                PosterCard(
                    item = item,
                    onClick = { onOpenItem(item.stableId) },
                    modifier = Modifier.width(posterCardMinWidth()),
                    progressFraction = progressByStableId[item.stableId]
                )
            }
        }
    }
}
