package com.illusion.app.ui.addmedia

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.illusion.app.R
import com.illusion.app.data.nfo.NfoWriter
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.security.DevAccessStore
import com.illusion.app.data.smb.SmbClient
import com.illusion.app.data.tmdb.TmdbClient
import com.illusion.app.data.tmdb.TmdbSearchResult
import com.illusion.app.ui.settings.formatBytes
import com.illusion.app.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.illusion.app.ui.common.MenuShape
import com.illusion.app.ui.common.TvAwareTextButton
import com.illusion.app.ui.common.dpadFieldNavigation
import com.illusion.app.ui.common.focusHighlight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMediaScreen(
    sourceRepository: SmbSourceRepository,
    smbClient: SmbClient,
    tmdbClient: TmdbClient,
    nfoWriter: NfoWriter,
    devAccessStore: DevAccessStore,
    onRescanNow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AddMediaViewModel = viewModel(
        factory = AddMediaViewModel.factory(sourceRepository, smbClient, tmdbClient, nfoWriter, devAccessStore)
    )
    val state by viewModel.state.collectAsState()

    // Observes UploadWorker's own progress/result directly via WorkManager - no dedicated Room
    // table for this developer-only, one-item-at-a-time flow (unlike DownloadsScreen, which tracks
    // a whole queue and needs to survive the screen closing). Keyed on the unique work NAME, not on
    // a work id this process happens to hold, so an upload still running after the app was killed
    // and restarted is picked back up here (see AddMediaViewModel.onUploadWorkInfo).
    LaunchedEffect(Unit) {
        WorkScheduler.addMediaUploadWorkInfo(context).collect(viewModel::onUploadWorkInfo)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pickFile(context, uri)
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pickSubtitle(context, uri)
    }

    var showFolderPicker by remember { mutableStateOf(false) }
    if (showFolderPicker) {
        val sourceId = state.selectedSourceId
        if (sourceId != null) {
            SmbFolderPickerDialog(
                sourceRepository = sourceRepository,
                smbClient = smbClient,
                sourceId = sourceId,
                initialPath = "",
                suggestedFolderName = state.destinationFolder.substringAfterLast('\\').takeIf { it.isNotBlank() },
                onPick = { path -> viewModel.setDestinationFolder(path); showFolderPicker = false },
                onDismiss = { showFolderPicker = false }
            )
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = com.illusion.app.ui.common.tvSafeContentWindowInsets(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.addmedia_title)) },
                navigationIcon = {
                    com.illusion.app.ui.common.TvAwareIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.addmedia_back))
                    }
                },
                actions = {
                    if (state.isTmdbConfigured && !state.showTmdbKeyEditor) {
                        com.illusion.app.ui.common.TvAwareIconButton(onClick = viewModel::openTmdbKeyEditor) {
                            Icon(Icons.Default.Key, contentDescription = stringResource(R.string.addmedia_tmdb_key_change))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!state.isTmdbConfigured || state.showTmdbKeyEditor) {
            TmdbKeyEntryStep(
                keyInput = state.tmdbKeyInput,
                onKeyChange = viewModel::setTmdbKeyInput,
                onSave = viewModel::saveTmdbApiKey,
                onCancel = if (state.isTmdbConfigured) viewModel::cancelTmdbKeyEditor else null,
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        when (state.step) {
            AddMediaStep.SETUP -> SetupStep(
                state = state,
                onSelectSource = viewModel::selectSource,
                onSelectKind = viewModel::selectKind,
                onPickFile = { filePicker.launch(arrayOf("video/*")) },
                onShowTitleChange = viewModel::setShowTitleInput,
                onSeasonChange = viewModel::setSeasonNumber,
                onEpisodeChange = viewModel::setEpisodeNumber,
                onSearchQueryChange = viewModel::setSearchQuery,
                onNext = viewModel::goToSearch,
                modifier = Modifier.padding(innerPadding)
            )
            AddMediaStep.SEARCH -> SearchStep(
                state = state,
                onQueryChange = viewModel::setSearchQuery,
                onSearch = viewModel::search,
                onSelect = viewModel::selectResult,
                modifier = Modifier.padding(innerPadding)
            )
            AddMediaStep.CONFIRM -> ConfirmStep(
                state = state,
                onTitleChange = viewModel::updateFetchedTitle,
                onOriginalTitleChange = viewModel::updateFetchedOriginalTitle,
                onYearChange = viewModel::updateFetchedYear,
                onPlotChange = viewModel::updateFetchedPlot,
                onFolderChange = viewModel::setDestinationFolder,
                onFileNameChange = viewModel::setDestinationFileName,
                onBrowseFolder = { showFolderPicker = true },
                onPickSubtitle = { subtitlePicker.launch(arrayOf("*/*")) },
                onRemoveSubtitle = viewModel::clearSubtitle,
                onConfirm = { viewModel.confirmAndUpload(context) },
                modifier = Modifier.padding(innerPadding)
            )
            AddMediaStep.UPLOADING -> UploadingStep(
                state = state,
                onCancel = { viewModel.cancelUpload(context) },
                modifier = Modifier.padding(innerPadding)
            )
            AddMediaStep.DONE -> DoneStep(onRescanNow = onRescanNow, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun TmdbKeyEntryStep(
    keyInput: String,
    onKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.addmedia_not_configured))
        FormTextField(
            value = keyInput,
            onValueChange = onKeyChange,
            label = stringResource(R.string.addmedia_tmdb_key_label),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        com.illusion.app.ui.common.TvAwareButton(onClick = onSave, enabled = keyInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.addmedia_tmdb_key_save))
        }
        if (onCancel != null) {
            TvAwareTextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun SetupStep(
    state: AddMediaUiState,
    onSelectSource: (Long) -> Unit,
    onSelectKind: (MediaKind) -> Unit,
    onPickFile: () -> Unit,
    onShowTitleChange: (String) -> Unit,
    onSeasonChange: (String) -> Unit,
    onEpisodeChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).focusGroup(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        var sourceMenuExpanded by remember { mutableStateOf(false) }
        val selectedSource = state.sources.firstOrNull { it.id == state.selectedSourceId }
        Box {
            com.illusion.app.ui.common.TvAwareOutlinedButton(onClick = { sourceMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedSource?.displayName ?: stringResource(R.string.addmedia_source))
            }
            DropdownMenu(expanded = sourceMenuExpanded, onDismissRequest = { sourceMenuExpanded = false }, shape = MenuShape) {
                state.sources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.displayName) },
                        onClick = { onSelectSource(source.id); sourceMenuExpanded = false }
                    )
                }
            }
        }
        FreeSpaceIndicator(state)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val movieSource = remember { MutableInteractionSource() }
            val episodeSource = remember { MutableInteractionSource() }
            SegmentedButton(
                selected = state.kind == MediaKind.MOVIE,
                onClick = { onSelectKind(MediaKind.MOVIE) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                interactionSource = movieSource,
                modifier = Modifier.focusHighlight(movieSource)
            ) { Text(stringResource(R.string.addmedia_kind_movie)) }
            SegmentedButton(
                selected = state.kind == MediaKind.TV_EPISODE,
                onClick = { onSelectKind(MediaKind.TV_EPISODE) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                interactionSource = episodeSource,
                modifier = Modifier.focusHighlight(episodeSource)
            ) { Text(stringResource(R.string.addmedia_kind_episode)) }
        }

        if (state.kind == MediaKind.TV_EPISODE) {
            FormTextField(
                value = state.showTitleInput,
                onValueChange = onShowTitleChange,
                label = stringResource(R.string.addmedia_show_title),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    value = state.seasonNumber,
                    onValueChange = onSeasonChange,
                    label = stringResource(R.string.addmedia_season),
                    modifier = Modifier.weight(1f)
                )
                FormTextField(
                    value = state.episodeNumber,
                    onValueChange = onEpisodeChange,
                    label = stringResource(R.string.addmedia_episode),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        com.illusion.app.ui.common.TvAwareButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.addmedia_pick_file))
        }
        state.pickedFileName?.let { name ->
            Text(stringResource(R.string.addmedia_picked_file, name), style = MaterialTheme.typography.bodySmall)
        }

        com.illusion.app.ui.common.TvAwareButton(
            onClick = onNext,
            enabled = state.selectedSourceId != null && state.pickedFileUri != null &&
                (state.kind == MediaKind.MOVIE || state.showTitleInput.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.addmedia_next))
        }
    }
}

/** Free space on the selected SMB source's volume - lets the developer see up front whether the NAS has room before picking/uploading a large file. */
@Composable
private fun FreeSpaceIndicator(state: AddMediaUiState, modifier: Modifier = Modifier) {
    when {
        state.isLoadingFreeSpace -> Text(
            stringResource(R.string.addmedia_free_space_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        state.freeSpaceBytes != null -> Text(
            stringResource(R.string.addmedia_free_space, formatBytes(state.freeSpaceBytes)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        state.freeSpaceError != null -> Text(
            stringResource(R.string.addmedia_free_space_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier
        )
    }
}

@Composable
private fun SearchStep(
    state: AddMediaUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (TmdbSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                label = stringResource(R.string.addmedia_search_query),
                modifier = Modifier.weight(1f)
            )
            com.illusion.app.ui.common.TvAwareButton(onClick = onSearch) { Text(stringResource(R.string.addmedia_search_button)) }
        }

        if (state.isSearching || state.isFetchingDetails) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.fetchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (!state.isSearching && state.searchResults.isEmpty() && state.searchError == null) {
            Text(stringResource(R.string.addmedia_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusGroup()) {
            items(state.searchResults, key = { it.id }) { result ->
                val cardSource = remember { MutableInteractionSource() }
                Card(
                    onClick = { onSelect(result) },
                    interactionSource = cardSource,
                    modifier = Modifier.fillMaxWidth().focusHighlight(cardSource)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        result.posterPath?.let { path ->
                            AsyncImage(
                                model = "https://image.tmdb.org/t/p/w92$path",
                                contentDescription = result.displayTitle,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                        Column {
                            Text(result.displayTitle, style = MaterialTheme.typography.bodyLarge)
                            result.displayYear?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmStep(
    state: AddMediaUiState,
    onTitleChange: (String) -> Unit,
    onOriginalTitleChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onPlotChange: (String) -> Unit,
    onFolderChange: (String) -> Unit,
    onFileNameChange: (String) -> Unit,
    onBrowseFolder: () -> Unit,
    onPickSubtitle: () -> Unit,
    onRemoveSubtitle: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fetched = state.fetched ?: return
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.addmedia_confirm_title), style = MaterialTheme.typography.titleMedium)
        FormTextField(value = fetched.title, onValueChange = onTitleChange, label = stringResource(R.string.addmedia_field_title), modifier = Modifier.fillMaxWidth())
        FormTextField(value = fetched.originalTitle ?: "", onValueChange = onOriginalTitleChange, label = stringResource(R.string.addmedia_field_original_title), modifier = Modifier.fillMaxWidth())
        FormTextField(value = fetched.year?.toString() ?: "", onValueChange = onYearChange, label = stringResource(R.string.addmedia_field_year), modifier = Modifier.fillMaxWidth())
        FormTextField(value = fetched.plot ?: "", onValueChange = onPlotChange, label = stringResource(R.string.addmedia_field_plot), minLines = 3, modifier = Modifier.fillMaxWidth())

        if (fetched.genres.isNotEmpty()) Text(fetched.genres.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        fetched.rating?.let { Text("★ %.1f".format(it), style = MaterialTheme.typography.bodySmall) }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormTextField(
                value = state.destinationFolder,
                onValueChange = onFolderChange,
                label = stringResource(R.string.addmedia_destination_folder),
                modifier = Modifier.weight(1f)
            )
            com.illusion.app.ui.common.TvAwareOutlinedButton(onClick = onBrowseFolder) { Text(stringResource(R.string.addmedia_browse)) }
        }
        FormTextField(value = state.destinationFileName, onValueChange = onFileNameChange, label = stringResource(R.string.addmedia_destination_file), modifier = Modifier.fillMaxWidth())

        if (state.pickedSubtitleName != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.addmedia_picked_subtitle, state.pickedSubtitleName),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TvAwareTextButton(onClick = onRemoveSubtitle) { Text(stringResource(R.string.addmedia_remove_subtitle)) }
            }
        } else {
            com.illusion.app.ui.common.TvAwareOutlinedButton(onClick = onPickSubtitle, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.addmedia_pick_subtitle))
            }
        }

        FreeSpaceIndicator(state)
        val freeSpace = state.freeSpaceBytes
        if (freeSpace != null && state.pickedFileSize > freeSpace) {
            Text(
                stringResource(R.string.addmedia_free_space_insufficient),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (state.prepareError != null) Text(state.prepareError, color = MaterialTheme.colorScheme.error)

        com.illusion.app.ui.common.TvAwareButton(onClick = onConfirm, enabled = !state.isPreparing, modifier = Modifier.fillMaxWidth()) {
            if (state.isPreparing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.addmedia_upload_button))
            }
        }
    }
}

@Composable
private fun UploadingStep(state: AddMediaUiState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(if (state.verifyingUpload) R.string.addmedia_verifying else R.string.addmedia_uploading))
        if (state.destinationFileName.isNotBlank()) {
            Text(state.destinationFileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val fraction = if (state.uploadTotalBytes > 0) (state.uploadedBytes.toFloat() / state.uploadTotalBytes).coerceIn(0f, 1f) else 0f
        if (state.verifyingUpload) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text("${state.uploadedBytes / 1_000_000} / ${state.uploadTotalBytes / 1_000_000} МБ")
        state.uploadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TvAwareTextButton(onClick = onCancel) { Text(stringResource(R.string.addmedia_upload_cancel)) }
    }
}

@Composable
private fun DoneStep(onRescanNow: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.addmedia_upload_done), style = MaterialTheme.typography.titleMedium)
        com.illusion.app.ui.common.TvAwareButton(onClick = onRescanNow) { Text(stringResource(R.string.addmedia_rescan_now)) }
    }
}

/**
 * Browses the real folder tree of [sourceId] over SMB (reuses [SmbConnection.listDirectory], the
 * same call the scanner itself uses) so the developer picks an actual existing destination instead
 * of typing a raw path blind. Typing a name into "новая папка" and tapping "+" only extends the
 * in-dialog path client-side - the folder itself is created later, when [SmbConnection.mkdirs] runs
 * as part of the real write in `AddMediaViewModel.confirmAndUpload`.
 */
@Composable
private fun SmbFolderPickerDialog(
    sourceRepository: SmbSourceRepository,
    smbClient: SmbClient,
    sourceId: Long,
    initialPath: String,
    suggestedFolderName: String? = null,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by remember { mutableStateOf(initialPath.trim('\\')) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var newFolderName by remember { mutableStateOf(suggestedFolderName.orEmpty()) }
    // Paths created client-side via the "+" button that don't exist on the NAS yet - mkdirs only
    // runs at real upload time (confirmAndUpload), so listing these over SMB would always 404 and
    // previously tripped the not-found fallback straight back to the share root.
    val virtualPaths = remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(currentPath) {
        isLoading = true
        loadError = null
        if (currentPath in virtualPaths.value) {
            android.util.Log.d("AddMediaScreen", "folder picker: '$currentPath' is a not-yet-created folder, skipping list")
            folders = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        android.util.Log.d("AddMediaScreen", "folder picker: listing sourceId=$sourceId path='$currentPath'")
        val outcome = runCatching {
            withContext(Dispatchers.IO) {
                val info = sourceRepository.connectionInfoById(sourceId) ?: error("Источник SMB недоступен")
                android.util.Log.d("AddMediaScreen", "folder picker: connectionInfo rootPath='${info.rootPath}' share='${info.share}'")
                smbClient.connect(info).use { connection ->
                    val listing = connection.listDirectory(currentPath)
                    android.util.Log.d(
                        "AddMediaScreen",
                        "folder picker: got ${listing.files.size} files, ${listing.directoryPaths.size} dirs: ${listing.directoryPaths}"
                    )
                    listing.directoryPaths.map { it.substringAfterLast('\\') }.sorted()
                }
            }
        }
        outcome.exceptionOrNull()?.let { android.util.Log.e("AddMediaScreen", "folder picker: list failed", it) }
        // A path that doesn't exist yet (e.g. the very first open, still on a not-yet-created
        // suggested folder name) would otherwise dead-end on an error with no way to see the real
        // tree - fall back to the source root once instead, rather than making the developer guess
        // to hit "Вверх" themselves.
        if (outcome.isFailure && currentPath.isNotBlank()) {
            currentPath = ""
            return@LaunchedEffect
        }
        folders = outcome.getOrDefault(emptyList())
        loadError = outcome.exceptionOrNull()?.message
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(currentPath.ifBlank { "\\" }) },
        text = {
            Column {
                if (currentPath.isNotBlank()) {
                    TvAwareTextButton(onClick = { currentPath = currentPath.substringBeforeLast('\\', "") }) {
                        Text(stringResource(R.string.addmedia_folder_picker_up))
                    }
                }
                when {
                    isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    loadError != null -> Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    folders.isEmpty() -> Text(stringResource(R.string.addmedia_folder_picker_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 280.dp).focusGroup()) {
                        items(folders, key = { it }) { name ->
                            val rowSource = remember { MutableInteractionSource() }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = rowSource,
                                        indication = androidx.compose.material3.ripple()
                                    ) { currentPath = if (currentPath.isBlank()) name else "$currentPath\\$name" }
                                    .focusHighlight(rowSource)
                                    .padding(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(name)
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = stringResource(R.string.addmedia_folder_picker_new_folder),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TvAwareTextButton(
                        onClick = {
                            val newPath = if (currentPath.isBlank()) newFolderName.trim() else "$currentPath\\${newFolderName.trim()}"
                            virtualPaths.value = virtualPaths.value + newPath
                            currentPath = newPath
                            newFolderName = ""
                        },
                        enabled = newFolderName.isNotBlank()
                    ) { Text("+") }
                }
            }
        },
        confirmButton = {
            TvAwareTextButton(onClick = { onPick(currentPath) }) { Text(stringResource(R.string.addmedia_folder_picker_choose)) }
        },
        dismissButton = {
            TvAwareTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * Every text field on this screen: a plain [OutlinedTextField] plus the focus treatment the rest of
 * the app's forms already use (see `ui/common/TvFocus.kt`) - on TV a D-pad user otherwise can't
 * tell which field holds focus, and Up/Down inside a field doesn't move on to the next one. Inert
 * on phone, where this developer-only flow is normally used.
 */
@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = minLines,
        singleLine = singleLine,
        interactionSource = interactionSource,
        modifier = modifier.focusHighlight(interactionSource).dpadFieldNavigation()
    )
}
