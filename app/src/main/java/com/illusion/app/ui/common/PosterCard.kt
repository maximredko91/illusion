package com.illusion.app.ui.common

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.platform.LocalDensity
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
import com.illusion.app.domain.model.UiMode
import com.illusion.app.domain.model.genreDisplayName

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
    progressFraction: Float? = null,
    /** Заменяет обычную подпись «год · жанр». В ряду «Продолжить просмотр» полезнее видеть прогресс и остаток. */
    subtitleOverride: String? = null,
    /** false - один постер без названия и подписи. Для коллекций: там подпись своя, под карточкой. */
    showCaption: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    // TV gets androidx.tv.material3.Card - native focus scale/border/glow (real D-pad-first
    // component, not the hand-rolled scale+border focusHighlight() hack every other platform
    // still uses) instead of a plain clickable Material3 Card. Phone/tablet keeps the exact
    // previous Card. IMPORTANT: only verify this on the real TV Box with a real remote - tapping
    // TV mode on a touchscreen phone will look completely broken (tv-material's click handling
    // requires D-pad focus first, see TvAwareControls.kt's own KDoc), that is expected, not a bug.
    if (LocalUiMode.current == UiMode.TV) {
        var focused by remember { mutableStateOf(false) }
        androidx.tv.material3.Card(
            onClick = { haptics.tick(); onClick() },
            // Default focused scale is 1.1f (verified via javap on CardDefaults.scale$default) -
            // a poster card at the edge of a dense grid zooms 10% on focus with no reserved room
            // for it, so it visibly overflows the screen edge (confirmed on the real TV Box).
            // 1.08f - same as focusHighlight() everywhere else, within the grid's own spacing.
            scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.08f),
            // Без рамки: карточка в фокусе выделяется сама - увеличением и осветлением постера
            // (focusLift ниже). Рамка читалась как обводка поверх интерфейса - просьба пользователя.
            border = androidx.tv.material3.CardDefaults.border(focusedBorder = androidx.tv.material3.Border.None),
            modifier = modifier.onFocusChanged { focused = it.hasFocus }
        ) {
            Box(Modifier.focusLift(focused)) {
                PosterCardContent(item, showRatingBadge, posterAspectRatio, isCurrent, progressFraction, subtitleOverride, showCaption)
            }
        }
        return
    }

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
        PosterCardContent(item, showRatingBadge, posterAspectRatio, isCurrent, progressFraction, subtitleOverride, showCaption)
    }
}

@Composable
private fun PosterCardContent(
    item: MediaItemEntity,
    showRatingBadge: Boolean,
    posterAspectRatio: Float,
    isCurrent: Boolean,
    progressFraction: Float?,
    subtitleOverride: String?,
    showCaption: Boolean
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
                // Бейдж рейтинга с постера убран: какой угол ни выбери, у части постеров там
                // окажется название фильма («Шоу Трумана», «Список Шиндлера», «1917») - бейдж его
                // перекроет. Рейтинг теперь в подписи под постером (CaptionMetaRow): виден всегда,
                // а не только при сортировке по рейтингу, и ничего не закрывает.
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
                    // Подложка под самой надписью, а не только общее затемнение карточки выше: мелкий
                    // белый текст попадал на светлый участок постера и терялся даже под затемнением.
                    Text(
                        stringResource(R.string.details_collection_current),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
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
            if (showCaption) {
                // Название раньше всегда занимало две строки (minLines = 2) ради одинаковой высоты
                // карточек в ряду - у коротких названий это оставляло пустую строку ровно между
                // названием и метастрокой. Высота держится тем же числом строк, но метастрока
                // прижата к низу, а воздух уходит под название.
                val subtitleLines = 2
                val captionHeight = with(LocalDensity.current) {
                    val titleLine = MaterialTheme.typography.bodyMedium.lineHeight.toDp()
                    val metaLine = MaterialTheme.typography.bodySmall.lineHeight.toDp()
                    titleLine * 2 + metaLine * subtitleLines + 4.dp
                }
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(8.dp).height(captionHeight)
                ) {
                    Text(
                        item.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Прогресс («39% · осталось 1 ч 4 мин») в ширину карточки не влезает
                    // в одну строку - ему даётся две, обычной метастроке хватает одной.
                    if (subtitleOverride != null) {
                        Text(
                            subtitleOverride,
                            maxLines = subtitleLines,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        CaptionMetaRow(item)
                    }
                }
            }
    }
}

/** Crimson strip in the launcher mark/splash - see ic_mark.xml. Reused here so the brand's own perforation motif shows up in-app, not just on the icon. */
internal val IllusionCrimson = Color(0xFFC2413A)
/**
 * Было жёсткое чёрное с белым текстом - на светлой теме бейдж читался чужеродным пятном.
 * Теперь берёт цвета темы, но непрозрачные: он лежит поверх произвольного постера, и
 * полупрозрачность здесь стоила бы читаемости.
 */
@Composable
private fun ratingBadgeBackground() = MaterialTheme.colorScheme.surfaceContainerHighest

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
            .background(ratingBadgeBackground())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 6.dp, top = 3.dp, bottom = 3.dp)
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                String.format(Locale.US, "%.1f", rating),
                color = MaterialTheme.colorScheme.onSurface,
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
internal fun PerforationStrip(holeColor: Color, modifier: Modifier = Modifier) {
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

/**
 * Рейтинг + год + длительность одной строкой под названием. Жанр отсюда убран: он рядом
 * есть фильтром, часто не влезал («2012 · Научная фантаст...») и ничего не говорил о том,
 * стоит ли включать фильм сейчас - в отличие от длительности.
 */
@Composable
private fun CaptionMetaRow(item: MediaItemEntity) {
    // Две короткие строки вместо одной длинной: «рейтинг · год · длительность · жанр»
    // в ширину карточки не влезает и обрезалась бы на самом жанре (так было со старой
    // подписью: «2012 · Научная фантаст...»).
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.rating?.let { rating ->
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                // Запятая, как в карточке фильма («9,0»), а не точка.
                Text(
                    String.format(Locale.forLanguageTag("ru"), "%.1f", rating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            val year = item.year?.toString()?.takeIf { !TITLE_YEAR_PATTERN.containsMatchIn(item.title) }
            if (year != null) {
                Text(
                    if (item.rating != null) " · $year" else year,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Жанр и длительность были одной строкой, и при длинном жанре («Приключения»)
        // обрезалась именно длительность: «Приключения · 2 ч 24 …». Теперь урезается жанр,
        // а длительность всегда видна целиком.
        val genre = item.genres.firstOrNull()?.let(::genreDisplayName)
        val runtime = posterRuntimeLabel(item.runtimeMinutes)
        if (genre != null || runtime != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (genre != null) {
                    Text(
                        genre,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (runtime != null) {
                    Text(
                        if (genre != null) " · $runtime" else runtime,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun posterRuntimeLabel(minutes: Int?): String? {
    val value = minutes?.takeIf { it > 0 } ?: return null
    return when {
        value < 60 -> "$value мин"
        value % 60 == 0 -> "${value / 60} ч"
        else -> "${value / 60} ч ${value % 60} мин"
    }
}

/** A trailing "(2001)" in a title - show folder names conventionally carry the year already. */
private val TITLE_YEAR_PATTERN = Regex("""\(\s*\d{4}\s*\)""")

private fun posterSubtitle(item: MediaItemEntity): String? {
    // Series titles come from the show's folder ("Клиника (2001)"), so printing the year again
    // right under it was pure repetition.
    val year = item.year?.toString()?.takeIf { !TITLE_YEAR_PATTERN.containsMatchIn(item.title) }
    val parts = listOfNotNull(year, item.genres.firstOrNull()?.let(::genreDisplayName))
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
