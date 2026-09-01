package com.illusion.app.ui.home

import androidx.compose.foundation.background
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
import com.illusion.app.domain.model.UiMode
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.LaunchedEffect
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
    collections: List<com.illusion.app.data.repository.LibraryRepository.CollectionSummary>,
    onRefreshRandomPicks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenItem: (String) -> Unit,
    hasNewContent: Boolean = false,
    onRescanNow: () -> Unit = {},
    onDismissNewContentBanner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        // Scaffold's own default (WindowInsets.systemBars) would otherwise reserve bottom
        // navigation-bar space a second time on top of what IllusionNavHost's outer Scaffold
        // already reserves for its real NavigationBar (same fix as LibraryScreen - see its own
        // comment on this line for the full explanation), leaving a dead gap between this
        // screen's scrollable content and the visible nav bar underneath it.
        contentWindowInsets = com.illusion.app.ui.common.tvSafeContentWindowInsets(),
        topBar = {
            TopAppBar(
                windowInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = {
                    // Framed top/bottom by the same crimson perforated-strip motif as the splash
                    // wordmark (MainActivity.kt's AppSplashOverlay) and the launcher mark itself -
                    // width(IntrinsicSize.Min) makes the Column (and so the strips inside it, which
                    // fillMaxWidth) measure to the text's own width rather than the whole app bar.
                    // TV-only: NOT applied there - it forces the Column to always claim its full
                    // intrinsic width regardless of what TopAppBar actually has left to give it,
                    // which is fine on phone (title slot is always wide enough) but on TV the
                    // left NavigationRail eats real width first, so the title's forced full-size
                    // measurement silently overflowed INTO the 5 action icons instead of shrinking
                    // - confirmed on-device (the search icon overlapping the app name letters).
                    val isTv = com.illusion.app.ui.common.LocalUiMode.current == UiMode.TV
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = if (isTv) Modifier else Modifier.width(IntrinsicSize.Min)
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
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        PerforationStrip(
                            holeColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().height(2.dp)
                        )
                    }
                },
                actions = {
                    // Explicit spacing (TopAppBar's own actions slot has none by default) - on TV
                    // mode specifically the 5 icons rendered edge-to-edge, touching the app name
                    // text next to them (confirmed on-device from photos) - tv-material's
                    // IconButton sizing/focus-scale reads as noticeably tighter than plain
                    // Material3 IconButton, which had enough of its own built-in padding to look
                    // fine on phone without this.
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.nav_search), Icons.Default.Search, onOpenSearch)
                        com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.favorites_title), Icons.Default.Favorite, onOpenFavorites)
                        com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.history_title), Icons.Default.History, onOpenHistory)
                        com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.downloads_title), Icons.Default.Download, onOpenDownloads)
                        com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.settings_title), Icons.Default.Settings, onOpenSettings)
                    }
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
            if (hasNewContent) {
                NewContentBanner(
                    onRescanNow = onRescanNow,
                    onDismiss = onDismissNewContentBanner,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
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
            if (collections.isNotEmpty()) {
                CollectionCarousel(collections = collections, onOpenItem = onOpenItem)
            }
        }
    }
}

/**
 * Nudges the user to rescan when [com.illusion.app.data.scan.LibraryScanner.hasNewContent]'s cheap
 * NAS-listing check (run once on app launch, see the Splash destination in IllusionNavHost) found
 * a video file not yet in the library index - without this, the only way to notice new content
 * added outside the app (or via the developer-only add-media flow) is to remember to check
 * Settings by hand. Dismissible on its own, and also cleared automatically once any rescan
 * completes by any means (see NewContentNotifier's own KDoc).
 */
@Composable
private fun NewContentBanner(onRescanNow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        ) {
            Text(
                stringResource(R.string.home_new_content_banner),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            com.illusion.app.ui.common.TvAwareButton(onClick = onRescanNow) {
                Text(stringResource(R.string.home_new_content_banner_action))
            }
            com.illusion.app.ui.common.TvAwareIconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_new_content_banner_dismiss)
                )
            }
        }
    }
}

/**
 * One card per franchise (see LibraryRepository.observeCollections' own KDoc for why this row
 * exists) - tapping jumps straight to the representative item's own Details screen, which already
 * renders every member of the same collection in its own "Коллекция" row (see DetailsScreen), so
 * this deliberately doesn't need a dedicated collection-browsing screen of its own to be useful.
 */
@Composable
private fun CollectionCarousel(
    collections: List<com.illusion.app.data.repository.LibraryRepository.CollectionSummary>,
    onOpenItem: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.home_collections),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.focusGroup()
        ) {
            items(collections, key = { it.name }) { collection ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(posterCardMinWidth())
                ) {
                    androidx.compose.foundation.layout.Box {
                        PosterCard(
                            item = collection.representative,
                            onClick = { onOpenItem(collection.representative.stableId) },
                            modifier = Modifier.width(posterCardMinWidth())
                        )
                        Text(
                            stringResource(R.string.home_collection_item_count, collection.itemCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    androidx.compose.foundation.shape.RoundedCornerShape(50)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        collection.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
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
        // A LazyRow keeps its scroll position across recomposition by default - fine for
        // continueWatching/collections (their content only ever grows/shrinks in place), but for
        // a refreshable row (onRefresh != null, i.e. random picks) a scrolled-to-the-end position
        // stayed scrolled to the end after "Обновить" swapped in a whole new shuffled list, which
        // read as "nothing happened" since the visible cards were now just whatever landed at
        // that same index in the new list. Per feedback, only refreshable rows reset to the start
        // on every new list.
        val listState = rememberLazyListState()
        if (onRefresh != null) {
            LaunchedEffect(items) { listState.scrollToItem(0) }
        }
        LazyRow(
            state = listState,
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
