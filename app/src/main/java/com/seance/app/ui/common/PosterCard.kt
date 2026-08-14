package com.seance.app.ui.common

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.seance.app.data.image.posterModel
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.domain.model.Category

/** Poster + title card used in the home carousels and library grids. Falls back to a category icon when there's no poster. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PosterCard(
    item: MediaItemEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    Card(
        modifier = modifier.clickable {
            haptics.tick()
            onClick()
        }
    ) {
        Column {
            var posterBoxModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    posterBoxModifier = posterBoxModifier.sharedElement(
                        rememberSharedContentState(key = posterTransitionKey(item.stableId)),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
            Box(modifier = posterBoxModifier) {
                val model = item.posterModel
                if (model != null) {
                    val painter = rememberAsyncImagePainter(model = model, contentScale = ContentScale.Crop)
                    val state by painter.state.collectAsState()
                    Image(
                        painter = painter,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (state is AsyncImagePainter.State.Loading) {
                        Box(modifier = Modifier.fillMaxSize().shimmer())
                    } else if (state is AsyncImagePainter.State.Error) {
                        PosterPlaceholder(item.category)
                    }
                } else {
                    PosterPlaceholder(item.category)
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    item.title,
                    maxLines = 2,
                    minLines = 2,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    posterSubtitle(item) ?: "",
                    maxLines = 1,
                    minLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun posterSubtitle(item: MediaItemEntity): String? {
    val parts = listOfNotNull(item.year?.toString(), item.genres.firstOrNull())
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun PosterPlaceholder(category: Category) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.4f)
        )
    }
}

private val Category.icon: ImageVector
    get() = when (this) {
        Category.MOVIES, Category.CARTOONS -> Icons.Default.Movie
        Category.TV_SHOWS, Category.CARTOON_SERIES -> Icons.Default.SmartDisplay
    }
