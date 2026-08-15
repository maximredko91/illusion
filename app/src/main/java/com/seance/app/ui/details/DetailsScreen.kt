package com.seance.app.ui.details

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.seance.app.R
import com.seance.app.data.image.fanartModel
import com.seance.app.data.image.posterModel
import com.seance.app.data.local.entity.DownloadEntity
import com.seance.app.data.local.entity.DownloadStatus
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.player.AudioTrackProber
import com.seance.app.data.repository.AudioTrackRepository
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.WatchProgressRepository
import com.seance.app.ui.common.LocalNavAnimatedVisibilityScope
import com.seance.app.ui.common.LocalSharedTransitionScope
import com.seance.app.ui.common.PosterCard
import com.seance.app.ui.common.RatingBadge
import com.seance.app.ui.common.posterTransitionKey
import com.seance.app.ui.common.shimmer
import com.seance.app.ui.common.ZoomableImageViewer
import com.seance.app.ui.common.toggle

@Composable
fun DetailsScreen(
    stableId: String,
    libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository,
    downloadRepository: DownloadRepository,
    audioTrackRepository: AudioTrackRepository,
    audioTrackProber: AudioTrackProber,
    onPlay: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DetailsViewModel = viewModel(
        key = stableId,
        factory = DetailsViewModel.factory(
            stableId,
            libraryRepository,
            watchProgressRepository,
            downloadRepository,
            audioTrackRepository,
            audioTrackProber
        )
    )
    val state by viewModel.state.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val download by viewModel.download.collectAsState()
    val watchProgress by viewModel.watchProgress.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        val item = state.item
        when {
            item != null -> DetailsContent(
                item = item,
                displayTitle = state.seriesTitle ?: item.title,
                audioTracks = state.audioTracks,
                clickablePersons = state.clickablePersons,
                similar = state.similar,
                collection = state.collection,
                episodes = state.episodes,
                isFavorite = isFavorite,
                onToggleFavorite = viewModel::toggleFavorite,
                hasStartedWatching = watchProgress?.let { it.positionMs > 0 && !it.watched } == true,
                download = download,
                onStartDownload = { viewModel.startDownload(context) },
                onRemoveDownload = { viewModel.removeDownload(context) },
                onDownloadError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                onPlay = onPlay,
                onOpenPerson = onOpenPerson,
                onOpenItem = onOpenItem,
                onBack = onBack
            )
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            else -> Text(
                stringResource(R.string.details_not_found),
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DetailsContent(
    item: MediaItemEntity,
    displayTitle: String,
    audioTracks: List<String>?,
    clickablePersons: Set<String>,
    similar: List<MediaItemEntity>,
    collection: List<MediaItemEntity>,
    episodes: List<MediaItemEntity>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    hasStartedWatching: Boolean,
    download: DownloadEntity?,
    onStartDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDownloadError: (String) -> Unit,
    onPlay: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit
) {
    var zoomedImage by remember { mutableStateOf<Any?>(null) }

    // Landscape on this device has a real display-cutout inset on one side only (front camera) -
    // padding just that side (the naive fix) looks lopsided, since the cutout is physically on
    // one edge but the reserved-safe-area column applies for the whole screen height. Mirror it:
    // reserve the same width on both edges so the layout stays visually symmetric regardless of
    // which side the hardware cutout is actually on. Zero in portrait (no cutout there), so no
    // change from before on that orientation.
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cutoutInsets = WindowInsets.displayCutout
    val cutoutHorizontalDp = with(density) {
        maxOf(cutoutInsets.getLeft(density, layoutDirection), cutoutInsets.getRight(density, layoutDirection)).toDp()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Push everything - including the fanart backdrop, not just the back button - below
            // the status bar, instead of letting the fanart bleed under it. Simpler and more
            // robust than darkening the top of the image for icon contrast: a bright backdrop
            // (e.g. a poster with a white background) can't wash out status-bar icons if nothing
            // ever renders behind them.
            .statusBarsPadding()
            .padding(horizontal = cutoutHorizontalDp)
    ) {
        Box {
            val fanart = item.fanartModel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .let { if (fanart != null) it.clickable { zoomedImage = fanart } else it }
            ) {
                if (fanart != null) {
                    val painter = rememberAsyncImagePainter(model = fanart, contentScale = ContentScale.Crop)
                    val state by painter.state.collectAsState()
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (state is AsyncImagePainter.State.Loading) {
                        Box(modifier = Modifier.fillMaxSize().shimmer())
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                // Fanart now starts right below the status bar (flat background
                                // above it, per the statusBarsPadding fix) - a plain hard cut into
                                // the image there reads as an abrupt seam. Fade in from the same
                                // background color over a short top strip, in addition to the
                                // existing fade-out into the background at the bottom.
                                0f to MaterialTheme.colorScheme.background,
                                0.15f to Color.Transparent,
                                0.6f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.background
                            )
                        )
                )
                if (fanart != null) {
                    // Same idea as the top/bottom fade, on the left/right edges - a fixed-width
                    // decorative soft edge, independent of `cutoutHorizontalDp` (that one's for
                    // correctness/symmetry of the surrounding margin, this one's purely cosmetic
                    // and applies in portrait too, where there's no cutout at all).
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(24.dp)
                            .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.background, Color.Transparent)))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(24.dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
                    )
                }
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(4.dp)
                    // A plain white icon washes out on a bright fanart (same problem the poster
                    // corner badges already solve) - same translucent-black pill treatment as
                    // RatingBadge/MpaaBadge, so it stays legible regardless of the image underneath.
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.details_back),
                    tint = Color.White
                )
            }
        }

        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            val poster = item.posterModel
            if (poster != null) {
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
                var posterModifier = Modifier
                    .width(132.dp)
                    .aspectRatio(2f / 3f)
                    .offset(y = (-42).dp)
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        posterModifier = posterModifier.sharedElement(
                            rememberSharedContentState(key = posterTransitionKey(item.stableId)),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
                posterModifier = posterModifier.clickable { zoomedImage = poster }
                Box(modifier = posterModifier) {
                    val painter = rememberAsyncImagePainter(model = poster, contentScale = ContentScale.Crop)
                    val state by painter.state.collectAsState()
                    Image(
                        painter = painter,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (state is AsyncImagePainter.State.Loading) {
                        Box(modifier = Modifier.fillMaxSize().shimmer())
                    }
                    item.rating?.let { rating ->
                        RatingBadge(rating, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                    }
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp).fillMaxWidth()) {
                Text(displayTitle, style = MaterialTheme.typography.headlineSmall)
                item.originalTitle?.takeIf { it != item.title }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    listOfNotNull(
                        item.year?.toString(),
                        item.country,
                        item.runtimeMinutes?.let { "$it мин" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.genres.isNotEmpty() || item.studio != null) {
                    Text(
                        listOfNotNull(item.genres.takeIf { it.isNotEmpty() }?.joinToString(", "), item.studio)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.tagline?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Button(onClick = { onPlay(item.stableId) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(
                    stringResource(if (hasStartedWatching) R.string.details_continue_watching else R.string.details_play),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            val haptics = LocalHapticFeedback.current
            IconButton(onClick = {
                haptics.toggle(!isFavorite)
                onToggleFavorite()
            }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.details_favorite_remove else R.string.details_favorite_add
                    )
                )
            }
            DownloadButton(
                download = download,
                itemTitle = displayTitle,
                onStart = onStartDownload,
                onRemove = onRemoveDownload,
                onError = onDownloadError
            )
        }

        Text(
            item.plot?.takeIf { it.isNotBlank() } ?: stringResource(R.string.details_no_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        audioTracks?.takeIf { it.isNotEmpty() }?.let { tracks ->
            Text(
                stringResource(R.string.details_audio_tracks, tracks.joinToString("; ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (episodes.isNotEmpty()) {
            EpisodeList(episodes, onPlay)
        }

        if (item.director.isNotEmpty()) {
            PersonRow(stringResource(R.string.details_director), item.director, clickablePersons, onOpenPerson)
        }
        if (item.actors.isNotEmpty()) {
            PersonRow(stringResource(R.string.details_actors), item.actors, clickablePersons, onOpenPerson)
        }

        if (collection.isNotEmpty()) {
            MediaRow(stringResource(R.string.details_collection), collection, onOpenItem)
        }
        if (similar.isNotEmpty()) {
            MediaRow(stringResource(R.string.details_similar), similar, onOpenItem)
        }
    }

    zoomedImage?.let { model ->
        ZoomableImageViewer(model = model, contentDescription = displayTitle, onDismiss = { zoomedImage = null })
    }
}

@Composable
private fun DownloadButton(
    download: DownloadEntity?,
    itemTitle: String,
    onStart: () -> Unit,
    onRemove: () -> Unit,
    onError: (String) -> Unit
) {
    var showRemoveConfirm by remember { mutableStateOf(false) }
    when (download?.status) {
        null -> IconButton(onClick = onStart) {
            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.details_download))
        }
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
            val progress = if (download.totalBytes > 0) {
                (download.downloadedBytes.toFloat() / download.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(start = 4.dp)) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.padding(8.dp))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.details_download_cancel))
                }
            }
        }
        DownloadStatus.COMPLETED -> {
            IconButton(onClick = { showRemoveConfirm = true }) {
                Icon(Icons.Default.DownloadDone, contentDescription = stringResource(R.string.details_download_remove))
            }
            if (showRemoveConfirm) {
                AlertDialog(
                    onDismissRequest = { showRemoveConfirm = false },
                    title = { Text(stringResource(R.string.details_download_remove_confirm_title)) },
                    text = { Text(stringResource(R.string.details_download_remove_confirm_message, itemTitle)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showRemoveConfirm = false
                            onRemove()
                        }) {
                            Text(stringResource(R.string.details_download_remove_confirm_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveConfirm = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
        DownloadStatus.FAILED -> {
            val errorMessage = stringResource(R.string.details_download_error_generic, download.errorMessage ?: stringResource(R.string.downloads_failed))
            IconButton(onClick = {
                onError(errorMessage)
                onStart()
            }) {
                Icon(Icons.Default.ErrorOutline, contentDescription = stringResource(R.string.details_download_retry))
            }
        }
    }
}

@Composable
private fun PersonRow(label: String, names: List<String>, clickablePersons: Set<String>, onOpenPerson: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(names) { name ->
                // Only worth a filmography screen when the person has more than one title here -
                // otherwise it's just this one item again, so the chip stays inert (greyed out).
                val clickable = name in clickablePersons
                AssistChip(
                    onClick = { if (clickable) onOpenPerson(name) },
                    label = { Text(name) },
                    enabled = clickable
                )
            }
        }
    }
}

@Composable
private fun EpisodeList(episodes: List<MediaItemEntity>, onPlay: (String) -> Unit) {
    val bySeason = episodes
        .sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
        .groupBy { it.seasonNumber }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        bySeason.forEach { (season, seasonEpisodes) ->
            Text(
                if (season != null) stringResource(R.string.details_season, season) else stringResource(R.string.details_episodes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            seasonEpisodes.forEach { episode ->
                val label = listOfNotNull(
                    episode.seasonNumber?.let { s -> episode.episodeNumber?.let { e -> "S${s}E$e" } },
                    episode.title
                ).joinToString(" · ")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(episode.stableId) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        label.ifBlank { episode.title },
                        modifier = Modifier.padding(start = 12.dp).weight(1f)
                    )
                    episode.premiered?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaRow(title: String, items: List<MediaItemEntity>, onOpenItem: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(items, key = { it.stableId }) { item ->
                PosterCard(item = item, onClick = { onOpenItem(item.stableId) }, modifier = Modifier.width(110.dp))
            }
        }
    }
}
