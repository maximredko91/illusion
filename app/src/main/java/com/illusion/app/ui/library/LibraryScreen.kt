package com.illusion.app.ui.library

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.domain.model.Category
import com.illusion.app.domain.model.SortOrder
import com.illusion.app.domain.model.defaultAscending
import com.illusion.app.ui.common.PosterCard
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.posterGridColumns
import com.illusion.app.ui.common.segmentTick
import com.illusion.app.ui.common.tick
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    category: Category,
    items: List<MediaItemEntity>,
    isLoading: Boolean,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    sortAscending: Boolean,
    onSortAscendingChange: (Boolean) -> Unit,
    genreFilter: String?,
    onGenreFilterChange: (String?) -> Unit,
    availableGenres: List<String>,
    yearFilter: Int?,
    onYearFilterChange: (Int?) -> Unit,
    availableYears: List<Int>,
    countryFilter: String?,
    onCountryFilterChange: (String?) -> Unit,
    availableCountries: List<String>,
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
    // Real root cause (explained to the user, this isn't a tuning tweak): a Scaffold topBar +
    // TopAppBarDefaults.*ScrollBehavior collapses the bar via nested scroll, which consumes part
    // of each drag's DELTA to shrink the bar - but the fling VELOCITY the grid receives at release
    // is computed from raw pointer motion regardless of how much delta the bar ate, so a fling that
    // started while the bar was mid-collapse could land the grid moving faster than the drag itself
    // ever visibly moved. No amount of picking a different *ScrollBehavior fixed this - it's how
    // Compose splits delta vs. velocity across nested scroll, full stop. Fix: the header is no
    // longer a separate nested-scroll-connected component at all - it's now the grid's own first
    // item (see the `item(span = ...)` below), so it scrolls at the exact same 1:1 rate as every
    // card underneath it and rides off the top exactly like the rest of the content does. No
    // separate collapse state, no delta/velocity split, nothing left to desync.
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
            SortMenu(
                sortOrder,
                onSortOrderChange = { scrollToTop(); onSortOrderChange(it) },
                ascending = sortAscending,
                onAscendingChange = { scrollToTop(); onSortAscendingChange(it) }
            )
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
            if (availableCountries.isNotEmpty()) {
                FilterMenu(
                    label = stringResource(R.string.library_country),
                    selected = countryFilter,
                    options = availableCountries,
                    onSelected = { scrollToTop(); onCountryFilterChange(it) }
                )
            }
        }
    }

    // No longer read by a TopAppBar's `windowInsets` param (there's no TopAppBar here anymore) -
    // applied directly as windowInsetsPadding() on the header content below instead.
    val topBarInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets()

    // In landscape a punch-hole camera cutout sits on a *side* edge, not the top - the header
    // above already dodges it (topBarInsets unions in displayCutout), but that's the grid's own
    // first item, which scrolls away with everything else. Every ordinary poster card below it
    // used a flat 8dp start/end contentPadding with no idea the cutout existed, so once scrolled
    // past the header, cards could sit directly behind the hole - there was always room to shift
    // them clear of it (this device's TV-mode NavigationRail already claims the true left edge,
    // leaving slack between it and the cutout), the grid just never accounted for it.
    val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val gridStartPadding = 8.dp + cutoutPadding.calculateStartPadding(layoutDirection)
    val gridEndPadding = 8.dp + cutoutPadding.calculateEndPadding(layoutDirection)

    // Scroll-to-top FAB: appears once the user has scrolled a few rows down, so they can jump
    // straight back to the top of a long library instead of flinging repeatedly - per feedback,
    // getting back to the start (or catching the sort/filter row again, which now scrolls away
    // with everything else as ordinary grid content) had no shortcut before this.
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 6 }
    }

    // The former TopAppBar's content, manually laid out (no TopAppBar composable - see this
    // function's own top comment for why) - rendered as the grid's own first item so it's
    // ordinary scrolling content, not a separately nested-scroll-driven component. Also reused
    // (called directly, not as a grid item) above the loading spinner/empty state below, so it's
    // still visible - just non-scrolling, same as before - while there's no grid to be part of.
    val header: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(topBarInsets)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp)
            ) {
                if (isLandscape) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(categoryTitle(category), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        sortFilterRow()
                    }
                } else {
                    Text(
                        categoryTitle(category),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.nav_search), Icons.Default.Search, onOpenSearch)
                com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.favorites_title), Icons.Default.Favorite, onOpenFavorites)
                com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.history_title), Icons.Default.History, onOpenHistory)
                com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.downloads_title), Icons.Default.Download, onOpenDownloads)
                com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.settings_title), Icons.Default.Settings, onOpenSettings)
            }
            if (category == Category.CARTOONS || category == Category.CARTOON_SERIES) {
                CartoonCategoryToggle(
                    category = category,
                    onCategoryChange = { scrollToTop(); onCategoryChange(it) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (!isLandscape) {
                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    sortFilterRow()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        // Without this, Scaffold's own default (WindowInsets.systemBars) reserves bottom
        // navigation-bar space AGAIN on top of what the outer Scaffold in IllusionNavHost already
        // reserves for its real NavigationBar - the ambient WindowInsets aren't consumed by that
        // outer Scaffold (its own contentWindowInsets = WindowInsets(0,0,0,0) only affects ITS
        // content's local padding value, not the ambient insets every descendant Scaffold reads
        // fresh), so this screen's grid stopped scrolling short of the visible nav bar, leaving a
        // dead unpainted gap between the last row and the real bar underneath it.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                val haptics = LocalHapticFeedback.current
                // Circular, not the default M3 rounded-square FAB shape - matches every other
                // floating icon control already in the app (Details' back/home/favorite circles,
                // the fanart zoom close button), which read as the app's own convention rather
                // than a stock Material control dropped in unchanged.
                FloatingActionButton(
                    onClick = {
                        haptics.tick()
                        coroutineScope.launch { gridState.animateScrollToItem(0) }
                    },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    // Default FAB colors (primaryContainer/onPrimaryContainer) don't track a
                    // user-picked accent color - IllusionTheme only overrides primary/secondary/
                    // tertiary, leaving the *Container roles at Material3's own baseline
                    // derivation, so this FAB stayed the same purple-ish gray no matter which
                    // accent was selected. Using primary/onPrimary directly makes it react.
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.library_scroll_to_top))
                }
            }
        }
    ) { innerPadding ->
        // Crossfades only the loading/empty/grid branch itself (keyed on that 3-way state, not
        // on `items`) - Room's query is async even when fast, so switching to this tab a beat
        // before the first emission lands would otherwise hard-cut from spinner to grid with no
        // animation of its own once the (separately animated) tab-switch transition has already
        // finished playing.
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Crossfade(
            targetState = if (isLoading) 0 else if (items.isEmpty()) 1 else 2,
            modifier = Modifier.fillMaxSize()
        ) { state ->
            when (state) {
                0 -> Column(modifier = Modifier.fillMaxSize()) {
                    header()
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                1 -> Column(modifier = Modifier.fillMaxSize()) {
                    header()
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                }
                else -> LazyVerticalGrid(
                    columns = posterGridColumns(),
                    state = gridState,
                    // Only the header item (first in this grid) carries the status-bar/cutout
                    // inset padding - a fast upward fling past the top triggers Compose's default
                    // stretch-overscroll effect, which visually scales the whole grid (header
                    // included) during the stretch, briefly shrinking that padding below the real
                    // inset and exposing the second row's card top edge under the transparent
                    // status bar/cutout for a frame before it springs back. Disabling overscroll
                    // just for this grid removes the stretch instead of trying to make the inset
                    // padding itself survive being scaled.
                    overscrollEffect = null,
                    // Was a custom FlingBehavior damping initial velocity to ~70% (meant to
                    // feel "softer") - reverted per feedback: damping velocity roughly
                    // squares the lost distance (spline decay), so a fling that used to
                    // carry across a couple rows now died almost immediately, reading as
                    // "the grid got heavy, I have to swipe hard to get anywhere" rather
                    // than smoother. Platform default fling is the correct baseline here.
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(bottom = 8.dp, start = gridStartPadding, end = gridEndPadding)
                ) {
                    // The header rides away with the rest of the scroll (see this function's
                    // top comment) - full-width span so it doesn't get squeezed into one column.
                    item(span = { GridItemSpan(maxLineSpan) }) { header() }
                    items(items, key = { it.stableId }) { item ->
                        // No animateItem() here (unlike Favorites/History/Downloads) -
                        // sorting/filtering this grid reorders/reshuffles most or all of
                        // its items at once, and animating every card's move across a
                        // multi-column grid simultaneously produced visible dark bar
                        // artifacts sweeping across rows mid-transition, not a clean
                        // reflow. A plain instant re-layout has no such glitch.
                        PosterCard(
                            item = item,
                            onClick = { onOpenItem(item.stableId) },
                            modifier = Modifier.padding(4.dp),
                            showRatingBadge = sortOrder == SortOrder.RATING
                        )
                    }
                }
            }
        }
        // The header carries the status-bar/cutout inset (see its own comment above) but only
        // while it's actually the topmost visible content - it's the grid's own first item, so
        // scrolling past it rides it away like everything else, and the poster cards that take
        // its place have nothing of their own painting that same top strip. Without this, cards
        // scrolled up under the transparent status bar sat directly behind the system icons with
        // no scrim, unreadable either way. A plain opaque strip pinned above the grid, sized to
        // the same inset, covers it regardless of scroll position.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(topBarInsets)
                .background(MaterialTheme.colorScheme.background)
                .align(Alignment.TopCenter)
        )
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
private fun SortMenu(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    ascending: Boolean,
    onAscendingChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        // The direction is spelled out right in the chip's own text - a bare arrow icon means
        // nothing until you've already learned what it does, but "Рейтинг: сначала высокий" is
        // legible on first look, and this text is always visible (unlike a tooltip, which
        // touch/D-pad users have no reliable way to trigger before tapping).
        AssistChip(
            onClick = { expanded = true },
            label = { Text(sortDirectionLabel(sortOrder, ascending)) },
            interactionSource = triggerSource,
            modifier = Modifier.focusHighlight(triggerSource)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                val itemSource = remember { MutableInteractionSource() }
                val isCurrent = order == sortOrder
                // The currently active row shows which direction it's sorted in and tapping it
                // again flips that direction in place (menu stays open - a quick toggle you might
                // want to hit more than once shouldn't require reopening the menu every time).
                // Other rows preview the direction switching to them would land on (each order's
                // own natural default, per SortOrder.defaultAscending) and tapping switches to it,
                // closing the menu same as before this feature existed.
                val rowAscending = if (isCurrent) ascending else order.defaultAscending
                DropdownMenuItem(
                    text = { Text(sortLabel(order)) },
                    trailingIcon = {
                        Icon(
                            if (rowAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = stringResource(
                                if (rowAscending) R.string.library_sort_ascending else R.string.library_sort_descending
                            ),
                            tint = if (isCurrent) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    },
                    onClick = {
                        haptics.segmentTick()
                        if (isCurrent) {
                            onAscendingChange(!ascending)
                        } else {
                            onSortOrderChange(order)
                            expanded = false
                        }
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
    SortOrder.YEAR -> stringResource(R.string.sort_year)
    SortOrder.TITLE -> stringResource(R.string.sort_title)
    SortOrder.RATING -> stringResource(R.string.sort_rating)
}

/** Spells out what the current direction actually means for this sort order, e.g. "Год: сначала новые" - see the SortMenu chip's own comment for why this can't just be a bare arrow icon. */
@Composable
private fun sortDirectionLabel(order: SortOrder, ascending: Boolean): String {
    val resId = when (order) {
        SortOrder.YEAR -> if (ascending) R.string.sort_year_asc else R.string.sort_year_desc
        SortOrder.RATING -> if (ascending) R.string.sort_rating_asc else R.string.sort_rating_desc
        SortOrder.TITLE -> if (ascending) R.string.sort_title_asc else R.string.sort_title_desc
    }
    return stringResource(resId)
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
