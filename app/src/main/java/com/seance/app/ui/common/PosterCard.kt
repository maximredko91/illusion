package com.seance.app.ui.common

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import java.util.Locale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import androidx.compose.ui.res.stringResource
import com.seance.app.R
import com.seance.app.data.image.posterModel
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.domain.model.Category

/** Poster + title card used in the home carousels and library grids. Falls back to a category icon when there's no poster. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PosterCard(
    item: MediaItemEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRatingBadge: Boolean = false,
    posterAspectRatio: Float = 2f / 3f
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        // A dozen-plus grid cards each casting their own shadow is real GPU compositing cost on
        // the very first frame a screen full of them appears - flat cards render just as well in
        // a grid where the poster image itself already provides all the visual separation needed.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .focusHighlight(interactionSource)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptics.tick()
                onClick()
            }
    ) {
        Column {
            // Shared-element bounds-morph into/out of Details deliberately removed (per user
            // feedback: it read as the poster "flying in" oddly rather than a clean transition) -
            // Details now just fades in/out (see NAV_TRANSITION handling in SeanceNavHost), no
            // bounds animation on the poster itself.
            //
            // The poster always keeps its full aspect ratio (never cropped to force an exact row
            // count on screen - tried and rejected per user feedback: a fixed pixel height forced
            // Crop to cut off parts of the image, e.g. faces).
            val posterBoxModifier = Modifier.fillMaxWidth().aspectRatio(posterAspectRatio)
            Box(modifier = posterBoxModifier) {
                val model = item.posterModel
                if (model != null) {
                    // AsyncImage (not rememberAsyncImagePainter+Image) so Coil sizes the decode to
                    // this Box's actual grid-cell size instead of the poster's full original
                    // resolution - rememberAsyncImagePainter has no layout size to read, so every
                    // poster in every carousel/grid was decoding at full source size regardless of
                    // how small the card actually renders it.
                    var loadState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
                    AsyncImage(
                        model = model,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onLoading = { loadState = it },
                        onSuccess = { loadState = it },
                        onError = { loadState = it }
                    )
                    if (loadState is AsyncImagePainter.State.Loading) {
                        Box(modifier = Modifier.fillMaxSize().shimmer())
                    } else if (loadState is AsyncImagePainter.State.Error) {
                        PosterPlaceholder(item.category, reason = imageLoadFailureReason())
                    }
                } else {
                    PosterPlaceholder(item.category)
                }
                if (showRatingBadge && item.rating != null) {
                    RatingBadge(item.rating, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    item.title,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    posterSubtitle(item) ?: "",
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = Color(0xFFFFC107),
            modifier = Modifier.size(14.dp)
        )
        Text(
            String.format(Locale.US, "%.1f", rating),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

private fun posterSubtitle(item: MediaItemEntity): String? {
    val parts = listOfNotNull(item.year?.toString(), item.genres.firstOrNull())
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun PosterPlaceholder(category: Category, reason: String? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(0.4f).aspectRatio(1f)
            )
            if (reason != null) {
                Text(
                    reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
    }
}

/** Why a poster/fanart that DOES have a path failed to actually load - distinct from "no path at all" (PosterPlaceholder's plain no-reason case), which isn't a failure, just nothing to fetch. */
@Composable
private fun imageLoadFailureReason(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return if (isOnLocalNetwork(context)) {
        stringResource(R.string.poster_load_failed)
    } else {
        stringResource(R.string.poster_load_failed_offline)
    }
}

private val Category.icon: ImageVector
    get() = when (this) {
        Category.MOVIES, Category.CARTOONS -> Icons.Default.Movie
        Category.TV_SHOWS, Category.CARTOON_SERIES -> Icons.Default.SmartDisplay
    }
