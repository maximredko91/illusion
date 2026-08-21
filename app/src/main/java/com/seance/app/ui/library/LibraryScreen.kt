package com.seance.app.ui.library

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seance.app.R
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.domain.model.Category
import com.seance.app.domain.model.SortOrder
import com.seance.app.ui.common.PosterCard
import com.seance.app.ui.common.focusHighlight
import com.seance.app.ui.common.posterGridColumns
import com.seance.app.ui.common.segmentTick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    category: Category,
    items: List<MediaItemEntity>,
    isLoading: Boolean,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    genreFilter: String?,
    onGenreFilterChange: (String?) -> Unit,
    availableGenres: List<String>,
    yearFilter: Int?,
    onYearFilterChange: (Int?) -> Unit,
    availableYears: List<Int>,
    gridState: LazyGridState,
    onOpenItem: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSearch: () -> Unit,
    onCategoryChange: (Category) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // gridState is hoisted by the caller (one per category, kept alive across tab switches via a
    // local Crossfade instead of Navigation Compose's saveState/restoreState) so scroll position
    // survives switching away and back.
    // Only jump to the top when the user actively changes a filter/sort/category - never on
    // recomposition from returning via back navigation, which should restore where they were.
    // Scrolling immediately on click would race the new sort order's items arriving from Room
    // (async query) - LazyVerticalGrid's key-based item tracking then keeps whatever was on
    // screen in view instead of honoring the scroll, so the list silently stayed where the user
    // was. Deferring the actual scroll to a LaunchedEffect keyed on `items` guarantees it only
    // runs once the newly-sorted/filtered list has actually landed.
    var pendingScrollToTop by remember { mutableStateOf(false) }
    val scrollToTop: () -> Unit = { pendingScrollToTop = true }
    LaunchedEffect(items) {
        if (pendingScrollToTop) {
            pendingScrollToTop = false
            gridState.scrollToItem(0)
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Same SortMenu/FilterMenu row, just rendered in two different places depending on
    // orientation (see below) - landscape has much less vertical room than portrait, so this row
    // moves into the top bar itself (next to the title) instead of taking a whole separate row
    // underneath it.
    val sortFilterRow: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            SortMenu(sortOrder, onSortOrderChange = { scrollToTop(); onSortOrderChange(it) })
            if (availableGenres.isNotEmpty()) {
                FilterMenu(
                    label = stringResource(R.string.library_genre),
                    selected = genreFilter,
                    options = availableGenres,
                    onSelected = { scrollToTop(); onGenreFilterChange(it) }
                )
            }
            if (availableYears.isNotEmpty()) {
                FilterMenu(
                    label = stringResource(R.string.library_year),
                    selected = yearFilter?.toString(),
                    options = availableYears.map { it.toString() },
                    onSelected = { scrollToTop(); onYearFilterChange(it?.toIntOrNull()) }
                )
            }
        }
    }

    // Hides the top bar as the grid scrolls forward (title/icons slide up and out) and brings it
    // straight back the moment the user scrolls the other way, even before reaching the top - the
    // grid already reclaims real vertical room on a phone, doubly so in landscape (see the sort/
    // filter row folding into the title above), so letting the bar get out of the way while
    // browsing is worth the animation, not just decoration.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    // Computed once here, in LibraryScreen's own (rarely-recomposing) scope, and passed down as a
    // plain value - calling this directly inside the `topBar` lambda below queried the real
    // View-system insets (ViewCompat.getRootWindowInsets, not a cheap Compose snapshot read) on
    // every recomposition that lambda goes through, which includes every scroll-driven recompose
    // TopAppBar's own collapsing/color-interpolation triggers while scrolling the grid.
    val topBarInsets = com.seance.app.ui.common.rememberLatchedStatusBarsInsets()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                windowInsets = topBarInsets,
                scrollBehavior = scrollBehavior,
                title = {
                    if (isLandscape) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(categoryTitle(category))
                            sortFilterRow()
                        }
                    } else {
                        Text(categoryTitle(category))
                    }
                },
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
        ) {
            if (category == Category.CARTOONS || category == Category.CARTOON_SERIES) {
                CartoonCategoryToggle(
                    category = category,
                    onCategoryChange = { scrollToTop(); onCategoryChange(it) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // In landscape this row already lives in the top bar next to the title instead (see
            // sortFilterRow above) - a whole separate row underneath would just be redundant, and
            // landscape has much less vertical room to give it anyway.
            if (!isLandscape) {
                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    sortFilterRow()
                }
            }

            // Crossfades only the loading/empty/grid branch itself (keyed on that 3-way state, not
            // on `items`) - Room's query is async even when fast, so switching to this tab a beat
            // before the first emission lands would otherwise hard-cut from spinner to grid with no
            // animation of its own once the (separately animated) tab-switch transition has already
            // finished playing.
            Crossfade(targetState = if (isLoading) 0 else if (items.isEmpty()) 1 else 2) { state ->
                    when (state) {
                        0 -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        1 -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(
                                    if (genreFilter != null || yearFilter != null) {
                                        R.string.library_empty_filtered
                                    } else {
                                        R.string.library_empty
                                    }
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> LazyVerticalGrid(
                            columns = posterGridColumns(),
                            state = gridState,
                            modifier = Modifier.fillMaxSize().focusGroup(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(items, key = { it.stableId }) { item ->
                                PosterCard(
                                    item = item,
                                    onClick = { onOpenItem(item.stableId) },
                                    modifier = Modifier.padding(4.dp).animateItem(),
                                    showRatingBadge = sortOrder == SortOrder.RATING
                                )
                            }
                        }
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartoonCategoryToggle(
    category: Category,
    onCategoryChange: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val options = listOf(Category.CARTOONS, Category.CARTOON_SERIES)
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            val segmentSource = remember { MutableInteractionSource() }
            SegmentedButton(
                selected = category == option,
                onClick = {
                    haptics.segmentTick()
                    onCategoryChange(option)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                interactionSource = segmentSource,
                modifier = Modifier.focusHighlight(segmentSource)
            ) {
                Text(categoryTitle(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SortMenu(sortOrder: SortOrder, onSortOrderChange: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        AssistChip(
            onClick = { expanded = true },
            label = { Text(sortLabel(sortOrder)) },
            interactionSource = triggerSource,
            modifier = Modifier.focusHighlight(triggerSource)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(sortLabel(order)) },
                    onClick = {
                        haptics.segmentTick()
                        onSortOrderChange(order)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

@Composable
internal fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.DATE_ADDED -> stringResource(R.string.sort_date_added)
    SortOrder.YEAR -> stringResource(R.string.sort_year)
    SortOrder.TITLE -> stringResource(R.string.sort_title)
    SortOrder.RATING -> stringResource(R.string.sort_rating)
}

@Composable
private fun FilterMenu(
    label: String,
    selected: String?,
    options: List<String>,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.library_filter_all)
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        AssistChip(
            onClick = { expanded = true },
            label = { Text(selected ?: label) },
            interactionSource = triggerSource,
            modifier = Modifier.focusHighlight(triggerSource)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val allSource = remember { MutableInteractionSource() }
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = {
                    haptics.segmentTick()
                    onSelected(null)
                    expanded = false
                },
                interactionSource = allSource,
                modifier = Modifier.focusHighlight(allSource)
            )
            options.forEach { option ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        haptics.segmentTick()
                        onSelected(option)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

@Composable
private fun categoryTitle(category: Category): String = when (category) {
    Category.MOVIES -> stringResource(R.string.category_movies)
    Category.TV_SHOWS -> stringResource(R.string.category_tv_shows)
    Category.CARTOONS -> stringResource(R.string.category_cartoons)
    Category.CARTOON_SERIES -> stringResource(R.string.category_cartoon_series)
}
