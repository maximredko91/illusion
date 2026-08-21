package com.seance.app.ui.search

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.seance.app.domain.model.UiMode
import com.seance.app.ui.common.LocalUiMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seance.app.R
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.ui.common.PosterCard
import com.seance.app.ui.common.focusHighlight
import com.seance.app.ui.common.posterGridColumns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    libraryRepository: LibraryRepository,
    onOpenItem: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(libraryRepository))
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = com.seance.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.nav_search)) },
                actions = {
                    com.seance.app.ui.common.TooltipIconButton(stringResource(R.string.favorites_title), Icons.Default.Favorite, onOpenFavorites)
                    com.seance.app.ui.common.TooltipIconButton(stringResource(R.string.history_title), Icons.Default.History, onOpenHistory)
                    com.seance.app.ui.common.TooltipIconButton(stringResource(R.string.downloads_title), Icons.Default.Download, onOpenDownloads)
                    com.seance.app.ui.common.TooltipIconButton(stringResource(R.string.settings_title), Icons.Default.Settings, onOpenSettings)
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val focusManager = LocalFocusManager.current
            val focusRequester = remember { FocusRequester() }
            val keyboardController = LocalSoftwareKeyboardController.current
            val uiMode = LocalUiMode.current
            // Auto-focus+keyboard only on phone: TV has no soft keyboard to pop and its
            // D-pad focus should land wherever the remote navigated from, not be yanked
            // onto this field on screen entry.
            if (uiMode != UiMode.TV) {
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .focusRequester(focusRequester)
                    // An empty field has no text to move a cursor through, but
                    // BasicTextField still swallows DPAD_LEFT/UP for cursor handling
                    // rather than yielding to focus search - stranding D-pad/remote users
                    // on the field with no way back to the TV nav rail above/left of it.
                    // Once there's a query, cursor movement through the text takes
                    // priority again, matching normal text-editing expectations.
                    //
                    // DPAD_DOWN is swallowed the same way regardless of query state - a
                    // single-line field has no "line below" to move a cursor into, but
                    // BasicTextField still consumes the event rather than yielding, which
                    // stranded focus on the field with the whole results grid unreachable
                    // (confirmed on-device: DOWN from the field did nothing at all).
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else when (event.key) {
                            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                            Key.DirectionLeft -> query.isEmpty() && focusManager.moveFocus(FocusDirection.Left)
                            Key.DirectionUp -> query.isEmpty() && focusManager.moveFocus(FocusDirection.Up)
                            else -> false
                        }
                    }
            )
            if (query.isBlank()) {
                Text(
                    stringResource(R.string.search_empty_query),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            } else if (results.isEmpty()) {
                Text(
                    stringResource(R.string.search_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = posterGridColumns(),
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(results, key = { it.stableId }) { item ->
                        PosterCard(item = item, onClick = { onOpenItem(item.stableId) }, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}
