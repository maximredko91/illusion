package com.illusion.app.ui.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.illusion.app.domain.model.UiMode
import com.illusion.app.ui.common.LocalUiMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.illusion.app.R
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.ui.common.PosterCard
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.posterGridColumns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    libraryRepository: LibraryRepository,
    settingsRepository: SettingsRepository,
    initialQuery: String? = null,
    initialDisplayQuery: String? = null,
    onOpenItem: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenTags: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(libraryRepository, settingsRepository, initialQuery, initialDisplayQuery)
    )
    val query by viewModel.displayQuery.collectAsState()
    val results by viewModel.results.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.nav_search)) },
                actions = {
                    com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.favorites_title), Icons.Default.Favorite, onOpenFavorites)
                    com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.history_title), Icons.Default.History, onOpenHistory)
                    com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.downloads_title), Icons.Default.Download, onOpenDownloads)
                    com.illusion.app.ui.common.TooltipIconButton(stringResource(R.string.settings_title), Icons.Default.Settings, onOpenSettings)
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val focusManager = LocalFocusManager.current
            val focusRequester = remember { FocusRequester() }
            val keyboardController = LocalSoftwareKeyboardController.current
            val uiMode = LocalUiMode.current
            val context = LocalContext.current
            // The system speech-recognizer UI Intent (not the SpeechRecognizer class) - it
            // delegates to whatever app resolves it (typically the Google app), which handles its
            // own mic permission/UI internally, so this doesn't need RECORD_AUDIO in the manifest.
            val speechRecognitionIntent = remember {
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            // Some devices (esp. some TV boxes/custom ROMs) have nothing installed that can
            // handle this intent at all - the icon is hidden rather than shown-but-silently-
            // does-nothing when tapped.
            val voiceSearchAvailable = remember {
                speechRecognitionIntent.resolveActivity(context.packageManager) != null
            }
            val voiceSearchLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.let { viewModel.setQuery(it) }
            }
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.commitSearch()
                    keyboardController?.hide()
                }),
                trailingIcon = if (voiceSearchAvailable) {
                    {
                        com.illusion.app.ui.common.TooltipIconButton(
                            label = stringResource(R.string.search_voice),
                            icon = Icons.Default.Mic,
                            onClick = { voiceSearchLauncher.launch(speechRecognitionIntent) }
                        )
                    }
                } else null,
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
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        TextButton(onClick = onOpenTags) {
                            Text(stringResource(R.string.search_all_tags))
                        }
                    }
                    if (recentSearches.isEmpty()) {
                        Text(
                            stringResource(R.string.search_empty_query),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        )
                    } else {
                        RecentSearchesList(
                            recentSearches = recentSearches,
                            onSelect = { viewModel.setQuery(it) },
                            onRemove = viewModel::removeRecentSearch,
                            onClearAll = viewModel::clearRecentSearches
                        )
                    }
                }
            } else if (results.isEmpty()) {
                Text(
                    stringResource(R.string.search_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            } else {
                val gridState = rememberLazyGridState()
                // Results are rebuilt wholesale on every keystroke (debounced) - without an
                // explicit reset the grid could be left scrolled deep into the PREVIOUS query's
                // results, which read as "jumping to the bottom of the list" the moment new,
                // shorter results replaced them underneath the same scroll offset.
                LaunchedEffect(query) { gridState.scrollToItem(0) }
                LazyVerticalGrid(
                    columns = posterGridColumns(),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(results, key = { it.stableId }) { item ->
                        PosterCard(
                            item = item,
                            onClick = {
                                viewModel.commitSearch()
                                onOpenItem(item.stableId)
                            },
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchesList(
    recentSearches: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.search_recent_title), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onClearAll) {
                Text(stringResource(R.string.search_recent_clear_all))
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(recentSearches, key = { it }) { recent ->
                ListItem(
                    headlineContent = { Text(recent) },
                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                    trailingContent = {
                        com.illusion.app.ui.common.TvAwareIconButton(onClick = { onRemove(recent) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_recent_remove)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(recent) }
                )
            }
        }
    }
}
