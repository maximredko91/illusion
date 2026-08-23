package com.illusion.app.ui.common

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.illusion.app.R
import com.illusion.app.data.image.posterModel
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.domain.model.Category

/** Poster + title card used in the home carousels and library grids. Falls back to a category icon when there's no poster. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PosterCard(
    item: MediaItemEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRatingBadge: Boolean = false,
    posterAspectRatio: Float = 2f / 3f,
    /** Dims the poster and labels it, e.g. for "which entry in this collection am I on" rows - see MediaRow in DetailsScreen. */
    isCurrent: Boolean = false,
    /** 0f-1f watched fraction, drawn as a thin bar along the poster's bottom edge - e.g. Home's "Продолжить просмотр" row. Null omits the bar entirely (no bar reads as "not applicable here", not "0% watched"). */
    progressFraction: Float? = null
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
            // Details now just fades in/out (see NAV_TRANSITION handling in IllusionNavHost), no
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
                if (isCurrent) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                    // Perforated top/bottom frame - a deliberately rarer touch than the rating
                    // badge's strip (this only ever shows on the one "you are here" card in a
                    // collection row, never a whole dense grid), so it can afford to be a full
                    // frame rather than a thin edge without reading as visual clutter.
                    PerforationStrip(
                        holeColor = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(6.dp)
                    )
                    PerforationStrip(
                        holeColor = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp)
                    )
                    Text(
                        stringResource(R.string.details_collection_current),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp)
                    )
                }
                if (progressFraction != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
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

/** Crimson strip in the launcher mark/splash - see ic_mark.xml. Reused here so the brand's own perforation motif shows up in-app, not just on the icon. */
private val IllusionCrimson = Color(0xFFC2413A)
private val RatingBadgeBackground = Color.Black.copy(alpha = 0.68f)

@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // height(IntrinsicSize.Min) - without it, the perforation strip's fillMaxHeight() below
        // had nothing but the poster Box's own loose height constraint to fill, stretching the
        // whole badge down over half the poster instead of matching its actual (small) content
        // height - confirmed on-device. This measures the Row by its content's min intrinsic
        // height first, so fillMaxHeight() children match that instead of the unconstrained parent.
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(RatingBadgeBackground)
    ) {
        PerforationStrip(
            holeColor = RatingBadgeBackground,
            modifier = Modifier.width(5.dp).fillMaxHeight()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 6.dp, top = 3.dp, bottom = 3.dp)
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
}

/**
 * A crimson strip with punched rectangular holes down its length - the same "film edge" motif as
 * the launcher mark (ic_mark.xml: a crimson strip with small dark rectangle cutouts), reused here
 * at whatever size the caller gives it. [holeColor] should match whatever's actually behind this
 * strip (the badge/frame's own background) - the holes aren't real transparency, just painted the
 * same color as their surroundings so they read as cutouts without needing an offscreen compositing
 * layer for a see-through blend mode, which isn't worth the cost at this size.
 */
@Composable
private fun PerforationStrip(holeColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(color = IllusionCrimson)
        val vertical = size.height >= size.width
        val length = if (vertical) size.height else size.width
        val thickness = if (vertical) size.width else size.height
        val holeLength = thickness * 0.55f
        val holeThickness = thickness * 0.5f
        val gap = holeLength * 0.85f
        val holeSize = if (vertical) Size(holeThickness, holeLength) else Size(holeLength, holeThickness)
        var pos = gap
        while (pos + holeLength < length) {
            val topLeft = if (vertical) {
                Offset((size.width - holeThickness) / 2f, pos)
            } else {
                Offset(pos, (size.height - holeThickness) / 2f)
            }
            drawRect(color = holeColor, topLeft = topLeft, size = holeSize)
            pos += holeLength + gap
        }
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
