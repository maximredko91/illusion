package com.illusion.app.ui.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.illusion.app.R
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.ui.common.PerforationStrip
import com.illusion.app.ui.common.PosterCard
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.posterCardMinWidth

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
        // navigation-bar space a second time on top of what IllusionNavHost's outer Scaffold
        // already reserves for its real NavigationBar (same fix as LibraryScreen - see its own
        // comment on this line for the full explanation), leaving a dead gap between this
        // screen's scrollable content and the visible nav bar underneath it.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = {
                    // Framed top/bottom by the same crimson perforated-strip motif as the splash
                    // wordmark (MainActivity.kt's AppSplashOverlay) and the launcher mark itself -
                    // width(IntrinsicSize.Min) makes the Column (and so the strips inside it, which
                    // fillMaxWidth) measure to the text's own width rather than the whole app bar.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(IntrinsicSize.Min)
                    ) {
                        PerforationStrip(
                            holeColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().height(2.dp)
                        )
                        Text(
                            stringResource(R.string.app_name).uppercase(),
                            modifier = Modifier.padding(vertical = 2.dp),
                            // Same face as the splash wordmark (MainActivity.kt's AppSplashOverlay)
                            // it flies in from - a plain default-style Text here made the landing
                            // read as a font/case swap rather than a clean touchdown. Smaller than
                            // the splash's 20sp and maxLines=1 - at that size, uppercase plus the
                            // same letter-spacing wrapped onto two lines in the TopAppBar's much
                            // narrower title slot (shared with 5 action icons), unlike the splash's
                            // full-width center stage.
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            fontSize = 14.sp,
                            letterSpacing = 0.12.em,
                            maxLines = 1
                        )
                        PerforationStrip(
                            holeColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().height(2.dp)
                        )
                    }
                },
                actions = {
                    com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.nav_search), Icons.Default.Search, onOpenSearch)
                    com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.favorites_title), Icons.Default.Favorite, onOpenFavorites)
                    com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.history_title), Icons.Default.History, onOpenHistory)
                    com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.downloads_title), Icons.Default.Download, onOpenDownloads)
                    com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.settings_title), Icons.Default.Settings, onOpenSettings)
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
                com.illusion.app.ui.common.TooltipIconButton(
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
