package com.seance.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seance.app.R
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.domain.model.Category
import com.seance.app.domain.model.SortOrder
import com.seance.app.ui.common.PosterCard
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
    onOpenItem: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onCategoryChange: (Category) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(categoryTitle(category)) },
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
        ) {
            if (category == Category.CARTOONS || category == Category.CARTOON_SERIES) {
                CartoonCategoryToggle(
                    category = category,
                    onCategoryChange = { scrollToTop(); onCategoryChange(it) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
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

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (items.isEmpty()) {
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
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(items, key = { it.stableId }) { item ->
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
            SegmentedButton(
                selected = category == option,
                onClick = {
                    haptics.segmentTick()
                    onCategoryChange(option)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(categoryTitle(option))
            }
        }
    }
}

@Composable
private fun SortMenu(sortOrder: SortOrder, onSortOrderChange: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        AssistChip(onClick = { expanded = true }, label = { Text(sortLabel(sortOrder)) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(sortLabel(order)) },
                    onClick = {
                        haptics.segmentTick()
                        onSortOrderChange(order)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun sortLabel(order: SortOrder): String = when (order) {
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
        AssistChip(onClick = { expanded = true }, label = { Text(selected ?: label) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = {
                    haptics.segmentTick()
                    onSelected(null)
                    expanded = false
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        haptics.segmentTick()
                        onSelected(option)
                        expanded = false
                    }
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
