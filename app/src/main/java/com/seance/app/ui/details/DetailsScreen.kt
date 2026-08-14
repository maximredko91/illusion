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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.seance.app.R
import com.seance.app.data.image.fanartModel
import com.seance.app.data.image.posterModel
import com.seance.app.data.local.entity.DownloadEntity
import com.seance.app.data.local.entity.DownloadStatus
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.data.repository.WatchProgressRepository
import com.seance.app.ui.common.LocalNavAnimatedVisibilityScope
import com.seance.app.ui.common.LocalSharedTransitionScope
import com.seance.app.ui.common.PosterCard
import com.seance.app.ui.common.posterTransitionKey
import com.seance.app.ui.common.shimmer
import com.seance.app.ui.common.toggle

@Composable
fun DetailsScreen(
    stableId: String,
    libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository,
    downloadRepository: DownloadRepository,
    onPlay: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DetailsViewModel = viewModel(
        key = stableId,
        factory = DetailsViewModel.factory(stableId, libraryRepository, watchProgressRepository, downloadRepository)
    )
    val state by viewModel.state.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val download by viewModel.download.collectAsState()
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        val item = state.item
        when {
            item != null -> DetailsContent(
                item = item,
                displayTitle = state.seriesTitle ?: item.title,
                similar = state.similar,
                collection = state.collection,
                episodes = state.episodes,
                isFavorite = isFavorite,
                onToggleFavorite = viewModel::toggleFavorite,
                download = download,
                onStartDownload = { viewModel.startDownload(context) },
                onRemoveDownload = { viewModel.removeDownload(context) },
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
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DetailsContent(
    item: MediaItemEntity,
    displayTitle: String,
    similar: List<MediaItemEntity>,
    collection: List<MediaItemEntity>,
    episodes: List<MediaItemEntity>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    download: DownloadEntity?,
    onStartDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onPlay: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box {
            val fanart = item.fanartModel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                            )
                        )
                )
            }
            IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
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
                    .width(100.dp)
                    .aspectRatio(2f / 3f)
                    .offset(y = (-32).dp)
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        posterModifier = posterModifier.sharedElement(
                            rememberSharedContentState(key = posterTransitionKey(item.stableId)),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
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
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
                Text(displayTitle, style = MaterialTheme.typography.headlineSmall)
                item.originalTitle?.takeIf { it != item.title }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    listOfNotNull(
                        item.year?.toString(),
                        item.country,
                        item.runtimeMinutes?.let { "$it мин" },
                        item.rating?.let { "★ %.1f".format(it) }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.genres.isNotEmpty()) {
                    Text(
                        item.genres.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text(stringResource(R.string.details_play), modifier = Modifier.padding(start = 8.dp))
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
            DownloadButton(download = download, onStart = onStartDownload, onRemove = onRemoveDownload)
        }

        Text(
            item.plot?.takeIf { it.isNotBlank() } ?: stringResource(R.string.details_no_description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (episodes.isNotEmpty()) {
            EpisodeList(episodes, onPlay)
        }

        if (item.director.isNotEmpty()) {
            PersonRow(stringResource(R.string.details_director), item.director, onOpenPerson)
        }
        if (item.actors.isNotEmpty()) {
            PersonRow(stringResource(R.string.details_actors), item.actors, onOpenPerson)
        }

        if (collection.isNotEmpty()) {
            MediaRow(stringResource(R.string.details_collection), collection, onOpenItem)
        }
        if (similar.isNotEmpty()) {
            MediaRow(stringResource(R.string.details_similar), similar, onOpenItem)
        }
    }
}

@Composable
private fun DownloadButton(download: DownloadEntity?, onStart: () -> Unit, onRemove: () -> Unit) {
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
        DownloadStatus.COMPLETED -> IconButton(onClick = onRemove) {
            Icon(Icons.Default.DownloadDone, contentDescription = stringResource(R.string.details_download_remove))
        }
        DownloadStatus.FAILED -> IconButton(onClick = onStart) {
            Icon(Icons.Default.ErrorOutline, contentDescription = stringResource(R.string.details_download_retry))
        }
    }
}

@Composable
private fun PersonRow(label: String, names: List<String>, onOpenPerson: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(names) { name ->
                AssistChip(onClick = { onOpenPerson(name) }, label = { Text(name) })
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
                    Text(label.ifBlank { episode.title }, modifier = Modifier.padding(start = 12.dp))
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
