package com.seance.app.ui.addmedia

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import com.seance.app.R
import com.seance.app.data.nfo.NfoWriter
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.smb.SmbClient
import com.seance.app.data.tmdb.TmdbClient
import com.seance.app.data.tmdb.TmdbSearchResult
import com.seance.app.work.UploadWorker
import com.seance.app.work.WorkScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMediaScreen(
    sourceRepository: SmbSourceRepository,
    smbClient: SmbClient,
    tmdbClient: TmdbClient,
    nfoWriter: NfoWriter,
    onRescanNow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AddMediaViewModel = viewModel(
        factory = AddMediaViewModel.factory(sourceRepository, smbClient, tmdbClient, nfoWriter)
    )
    val state by viewModel.state.collectAsState()

    // Observes UploadWorker's own progress/result directly via WorkManager - no dedicated Room
    // table for this developer-only, one-item-at-a-time flow (unlike DownloadsScreen, which tracks
    // a whole queue and needs to survive the screen closing).
    LaunchedEffect(state.uploadWorkId) {
        val workId = state.uploadWorkId ?: return@LaunchedEffect
        WorkManager.getInstance(context).getWorkInfoByIdFlow(workId).collect { info ->
            if (info == null) return@collect
            val uploaded = info.progress.getLong(UploadWorker.KEY_UPLOADED, -1L)
            val total = info.progress.getLong(UploadWorker.KEY_TOTAL, -1L)
            if (uploaded >= 0) viewModel.onUploadProgress(uploaded, total)
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> viewModel.onUploadFinished(true, null)
                WorkInfo.State.FAILED -> viewModel.onUploadFinished(false, info.outputData.getString(UploadWorker.KEY_ERROR))
                else -> Unit
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pickFile(context, uri)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.addmedia_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.addmedia_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!tmdbClient.isConfigured) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.addmedia_not_configured), modifier = Modifier.padding(24.dp))
            }
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
                onConfirm = { viewModel.confirmAndUpload(context) },
                modifier = Modifier.padding(innerPadding)
            )
            AddMediaStep.UPLOADING -> UploadingStep(state = state, modifier = Modifier.padding(innerPadding))
            AddMediaStep.DONE -> DoneStep(onRescanNow = onRescanNow, modifier = Modifier.padding(innerPadding))
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
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        var sourceMenuExpanded by remember { mutableStateOf(false) }
        val selectedSource = state.sources.firstOrNull { it.id == state.selectedSourceId }
        Box {
            OutlinedButton(onClick = { sourceMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedSource?.displayName ?: stringResource(R.string.addmedia_source))
            }
            DropdownMenu(expanded = sourceMenuExpanded, onDismissRequest = { sourceMenuExpanded = false }) {
                state.sources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.displayName) },
                        onClick = { onSelectSource(source.id); sourceMenuExpanded = false }
                    )
                }
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.kind == MediaKind.MOVIE,
                onClick = { onSelectKind(MediaKind.MOVIE) },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text(stringResource(R.string.addmedia_kind_movie)) }
            SegmentedButton(
                selected = state.kind == MediaKind.TV_EPISODE,
                onClick = { onSelectKind(MediaKind.TV_EPISODE) },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text(stringResource(R.string.addmedia_kind_episode)) }
        }

        if (state.kind == MediaKind.TV_EPISODE) {
            OutlinedTextField(
                value = state.showTitleInput,
                onValueChange = onShowTitleChange,
                label = { Text(stringResource(R.string.addmedia_show_title)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.seasonNumber,
                    onValueChange = onSeasonChange,
                    label = { Text(stringResource(R.string.addmedia_season)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.episodeNumber,
                    onValueChange = onEpisodeChange,
                    label = { Text(stringResource(R.string.addmedia_episode)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.addmedia_pick_file))
        }
        state.pickedFileName?.let { name ->
            Text(stringResource(R.string.addmedia_picked_file, name), style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = onNext,
            enabled = state.selectedSourceId != null && state.pickedFileUri != null &&
                (state.kind == MediaKind.MOVIE || state.showTitleInput.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.addmedia_next))
        }
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
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.addmedia_search_query)) },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onSearch) { Text(stringResource(R.string.addmedia_search_button)) }
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

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.searchResults, key = { it.id }) { result ->
                Card(onClick = { onSelect(result) }, modifier = Modifier.fillMaxWidth()) {
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
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fetched = state.fetched ?: return
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.addmedia_confirm_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = fetched.title, onValueChange = onTitleChange, label = { Text(stringResource(R.string.addmedia_field_title)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = fetched.originalTitle ?: "", onValueChange = onOriginalTitleChange, label = { Text(stringResource(R.string.addmedia_field_original_title)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = fetched.year?.toString() ?: "", onValueChange = onYearChange, label = { Text(stringResource(R.string.addmedia_field_year)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = fetched.plot ?: "", onValueChange = onPlotChange, label = { Text(stringResource(R.string.addmedia_field_plot)) }, minLines = 3, modifier = Modifier.fillMaxWidth())

        if (fetched.genres.isNotEmpty()) Text(fetched.genres.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        fetched.rating?.let { Text("★ %.1f".format(it), style = MaterialTheme.typography.bodySmall) }

        HorizontalDivider()
        OutlinedTextField(value = state.destinationFolder, onValueChange = onFolderChange, label = { Text(stringResource(R.string.addmedia_destination_folder)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.destinationFileName, onValueChange = onFileNameChange, label = { Text(stringResource(R.string.addmedia_destination_file)) }, modifier = Modifier.fillMaxWidth())

        if (state.prepareError != null) Text(state.prepareError, color = MaterialTheme.colorScheme.error)

        Button(onClick = onConfirm, enabled = !state.isPreparing, modifier = Modifier.fillMaxWidth()) {
            if (state.isPreparing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.addmedia_upload_button))
            }
        }
    }
}

@Composable
private fun UploadingStep(state: AddMediaUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.addmedia_uploading))
        val fraction = if (state.uploadTotalBytes > 0) (state.uploadedBytes.toFloat() / state.uploadTotalBytes).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text("${state.uploadedBytes / 1_000_000} / ${state.uploadTotalBytes / 1_000_000} МБ")
        state.uploadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
        Button(onClick = onRescanNow) { Text(stringResource(R.string.addmedia_rescan_now)) }
    }
}
