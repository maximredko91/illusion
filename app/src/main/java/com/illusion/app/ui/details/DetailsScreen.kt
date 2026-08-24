package com.illusion.app.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.illusion.app.R
import com.illusion.app.data.image.episodeThumbModel
import com.illusion.app.data.image.fanartModel
import com.illusion.app.data.image.posterModel
import com.illusion.app.data.local.entity.DownloadEntity
import com.illusion.app.data.local.entity.DownloadStatus
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.local.entity.hasForcedSubtitles
import com.illusion.app.data.player.AudioTrackProber
import com.illusion.app.data.repository.AudioTrackRepository
import com.illusion.app.data.repository.DownloadRepository
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.WatchProgressRepository
import com.illusion.app.ui.common.PosterCard
import com.illusion.app.ui.common.RatingBadge
import com.illusion.app.ui.common.ThumbnailImage
import com.illusion.app.ui.common.shimmer
import com.illusion.app.ui.common.ZoomableImageViewer
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.toggle

@Composable
fun DetailsScreen(
    stableId: String,
    libraryRepository: LibraryRepository,
    watchProgressRepository: WatchProgressRepository,
    downloadRepository: DownloadRepository,
    audioTrackRepository: AudioTrackRepository,
    audioTrackProber: AudioTrackProber,
    onPlay: (String) -> Unit,
    onPlayTrailer: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
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
    val downloads by viewModel.downloads.collectAsState()
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
                downloads = downloads,
                onStartDownload = { viewModel.startDownload(context) },
                onRemoveDownload = { viewModel.removeDownload(context) },
                onDownloadSeason = { ids -> viewModel.startSeasonDownload(context, ids) },
                onDownloadEpisode = { id -> viewModel.startDownload(context, id) },
                onRemoveEpisodeDownload = { id -> viewModel.removeDownload(context, id) },
                onRemoveSeasonDownloads = { ids -> viewModel.removeSeasonDownloads(context, ids) },
                onDownloadError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                onPlay = onPlay,
                onPlayTrailer = onPlayTrailer,
                onOpenPerson = onOpenPerson,
                onOpenItem = onOpenItem,
                onBack = onBack,
                onGoHome = onGoHome
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
    downloads: Map<String, DownloadEntity>,
    onStartDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDownloadSeason: (List<String>) -> Unit,
    onDownloadEpisode: (String) -> Unit,
    onRemoveEpisodeDownload: (String) -> Unit,
    onRemoveSeasonDownloads: (List<String>) -> Unit,
    onDownloadError: (String) -> Unit,
    onPlay: (String) -> Unit,
    onPlayTrailer: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit
) {
    var zoomedImage by remember { mutableStateOf<Any?>(null) }
    var zoomedImageIsFanart by remember { mutableStateOf(false) }
    val fanartImageLoader = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.illusion.app.IllusionApplication).fanartImageLoader

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
    // Modifier.statusBarsPadding() measured a correct nonzero inset here (confirmed via logging)
    // but still rendered with zero effective padding - the fanart backdrop bled straight under the
    // status bar regardless. WindowInsets.statusBars (Compose's ambient snapshot) was also observed
    // to transiently report 0 on its own, on this device, with no Dialog involved - a genuine
    // Compose/OS insets-redispatch race, not something this screen's own code controls. Cross-check
    // against the real, current View-system insets (ViewCompat.getRootWindowInsets, queried fresh -
    // not cached - every recomposition) and latch onto the largest value either source has ever
    // reported: the real status bar height doesn't shrink mid-session in practice, so a regression
    // to a smaller/zero value is always the race, never a legitimate change.
    val view = LocalView.current
    var statusBarsTopDp by remember { mutableStateOf(0.dp) }
    val ambientStatusBarsTopDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val viewStatusBarsTopDp = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.statusBars())
        ?.top
        ?.let { with(density) { it.toDp() } }
        ?: 0.dp
    // Forces a second read ~200ms after first composition, in case the very first frame lands
    // before the real inset value is dispatched at all and nothing else happens to trigger a
    // recomposition afterward to pick up the corrected value. The bare read below (result
    // otherwise unused) is what makes this composable actually recompose when the tick changes.
    var recheckTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        recheckTick++
    }
    recheckTick

    val liveStatusBarsTopDp = maxOf(ambientStatusBarsTopDp, viewStatusBarsTopDp)
    if (liveStatusBarsTopDp > statusBarsTopDp) statusBarsTopDp = liveStatusBarsTopDp

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Reserves the status bar's height from the scrollable VIEWPORT itself, not just as an
            // initial content offset - this padding must come before .verticalScroll() in the
            // chain. Padding placed after .verticalScroll() only offsets the content's starting
            // position; that gap scrolls away with the rest of the content, and everything further
            // down (description, cast, ...) ends up passing behind the status bar during a scroll.
            // With the viewport itself inset instead, nothing can ever render there regardless of
            // scroll position.
            .padding(top = statusBarsTopDp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = cutoutHorizontalDp)
    ) {
        Box {
            val fanart = item.fanartModel
            val fanartSource = remember { MutableInteractionSource() }
            // Back/home/favorite all anchor to this box's corners - matches their own footprint
            // (IconButton's default touch target) so the dead zone below lines up with where a
            // near-miss on one of them actually lands.
            val cornerButtonSize = 48.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (fanart != null) {
                    // AsyncImage (not rememberAsyncImagePainter+Image) so Coil sizes the decode to
                    // this Box's actual layout constraints instead of the fanart's full original
                    // resolution - rememberAsyncImagePainter has no layout size to read, so without
                    // this every fanart decoded at full source size regardless of the 220dp strip
                    // it's drawn into, which is what made opening a card feel slow to load.
                    var fanartLoading by remember { mutableStateOf(true) }
                    var fanartFailed by remember { mutableStateOf(false) }
                    AsyncImage(
                        model = fanart,
                        imageLoader = fanartImageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onLoading = { fanartLoading = true; fanartFailed = false },
                        onSuccess = { fanartLoading = false; fanartFailed = false },
                        onError = { fanartLoading = false; fanartFailed = true }
                    )
                    if (fanartFailed) {
                        val fanartContext = androidx.compose.ui.platform.LocalContext.current
                        Text(
                            stringResource(
                                if (com.illusion.app.ui.common.isOnLocalNetwork(fanartContext)) {
                                    R.string.poster_load_failed
                                } else {
                                    R.string.poster_load_failed_offline
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    if (fanartLoading) {
                        Box(modifier = Modifier.fillMaxSize().shimmer())
                    }
                    // Top: barely-there, just enough to soften the seam against the flat
                    // status-bar background above (not a real vignette - the fanart itself stays
                    // clear and bright through the middle, per feedback against darkening it).
                    // Bottom: a real dissolve to full opaque background over the last third of the
                    // image, so the hard cut straight into the poster/title row below becomes a
                    // graceful falloff instead - this is the "abrupt transition" fix, the top fade
                    // is unrelated and was already there.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to MaterialTheme.colorScheme.background.copy(alpha = 0.28f),
                                    0.14f to Color.Transparent,
                                    0.62f to Color.Transparent,
                                    1f to MaterialTheme.colorScheme.background
                                )
                            )
                    )
                }
                if (fanart != null) {
                    // Zoom only triggers from this inset center region, not the full fanart - a
                    // full-bleed clickable here meant a near-miss on the back/home/favorite buttons
                    // (all anchored to this box's own corners) fell straight through to opening the
                    // zoomed viewer instead, since a tap just outside a button's actual touch target
                    // still landed on this Box underneath it. Purely a hit-test layer, no visuals.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(cornerButtonSize)
                            .focusHighlight(fanartSource)
                            .clickable(interactionSource = fanartSource, indication = LocalIndication.current) { zoomedImage = fanart; zoomedImageIsFanart = true }
                    )
                }
            }
            val backSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onBack,
                interactionSource = backSource,
                modifier = Modifier
                    .padding(4.dp)
                    // A plain white icon washes out on a bright fanart (same problem the poster
                    // corner badges already solve) - same translucent-black pill treatment as
                    // RatingBadge/MpaaBadge, so it stays legible regardless of the image underneath.
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .focusHighlight(backSource, color = Color.White)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.details_back),
                    tint = Color.White
                )
            }

            // Escape hatch for drilling several Similar/series hops deep (Movie -> part 2 -> part
            // 3 -> ...) - onBack still steps back one card at a time (so returning to the card the
            // user actually came from works normally), this jumps straight to the main screen
            // instead of requiring one "Назад" per hop.
            val homeSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onGoHome,
                interactionSource = homeSource,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .focusHighlight(homeSource, color = Color.White)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = stringResource(R.string.details_go_home),
                    tint = Color.White
                )
            }

            // Moved here from the play-button row below (per user feedback) - top-right on the
            // fanart mirrors the back button's placement on the left, and reads as a standard
            // "favorite this" corner action the way most media apps place it, rather than
            // competing for space with Play/Trailer/Download in one row.
            val haptics = LocalHapticFeedback.current
            val favoriteSource = remember { MutableInteractionSource() }
            val favoriteScale = remember { Animatable(1f) }
            val favoriteScope = rememberCoroutineScope()
            val favoriteTint by animateColorAsState(
                targetValue = if (isFavorite) Color(0xFFE53935) else Color.White,
                label = "favoriteTint"
            )
            IconButton(
                onClick = {
                    haptics.toggle(!isFavorite)
                    onToggleFavorite()
                    // A little bounce every tap - in either direction (add or remove) - gives the
                    // action some tactile weight beyond just the icon/color swap underneath it.
                    favoriteScope.launch {
                        favoriteScale.snapTo(0.7f)
                        favoriteScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                    }
                },
                interactionSource = favoriteSource,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .focusHighlight(favoriteSource, color = Color.White)
            ) {
                Crossfade(targetState = isFavorite, label = "favoriteIcon") { favorite ->
                    Icon(
                        imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(
                            if (favorite) R.string.details_favorite_remove else R.string.details_favorite_add
                        ),
                        tint = favoriteTint,
                        modifier = Modifier.scale(favoriteScale.value)
                    )
                }
            }
        }

        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            val poster = item.posterModel
            if (poster != null) {
                // Shared-element bounds-morph from the grid poster removed (per user feedback -
                // see the matching note in PosterCard.kt) - Details now just fades in/out instead.
                // Used to pull the poster up by a fixed 42dp so it overlapped the fanart above it
                // (a "hero card" look) - dropped per user feedback: the fanart's own height here
                // is a fixed 220dp regardless of screen size, so that overlap had no situation
                // where it was actually needed for space, it just permanently covered part of the
                // fanart image. The poster now sits flush against the fanart's bottom edge.
                //
                // Stretching the poster to match the metadata column's height (via
                // Modifier.height(IntrinsicSize.Min) on the Row + fillMaxHeight here) was tried to
                // close the empty space a long original title left underneath the poster - dropped,
                // it fed back on itself: a taller poster claims more width to keep its aspect ratio,
                // which leaves the title column narrower, which wraps the title onto even more
                // lines, which grows the column taller still. The title itself is now capped at 4
                // lines below instead, which keeps the column from running away in the first place.
                var posterModifier = Modifier
                    .width(132.dp)
                    .aspectRatio(2f / 3f)
                val posterSource = remember { MutableInteractionSource() }
                posterModifier = posterModifier
                    .focusHighlight(posterSource)
                    .clickable(interactionSource = posterSource, indication = LocalIndication.current) { zoomedImage = poster; zoomedImageIsFanart = false }
                Box(modifier = posterModifier) {
                    var posterLoading by remember { mutableStateOf(true) }
                    AsyncImage(
                        model = poster,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onLoading = { posterLoading = true },
                        onSuccess = { posterLoading = false },
                        onError = { posterLoading = false }
                    )
                    if (posterLoading) {
                        Box(modifier = Modifier.fillMaxSize().shimmer())
                    }
                    item.rating?.let { rating ->
                        RatingBadge(rating, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                    }
                }
            }
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val originalTitle = item.originalTitle?.takeIf { it.isNotBlank() && it != item.title }
                // Title and original title share their own tight-spaced Column, separate from the
                // outer 8dp rhythm used between the bigger blocks below (year/genres/...) - two
                // separate Text composables sitting right next to each other under that wider
                // spacing read as two unrelated lines, not one title with its original name under
                // it.
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    // Two separate Text composables, not one combined AnnotatedString with a shared
                    // maxLines - a single shared line budget meant a long original title (e.g. "The
                    // Lord of the Rings: The Return of the King") could eat into the budget enough
                    // that the ellipsis landed on the *main* title's own line, making it look like
                    // the movie's real name got cut off, when only the parenthetical original title
                    // needed trimming.
                    Text(
                        displayTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    if (originalTitle != null) {
                        // Capped at 2 lines by default so a long original title can't tower past the
                        // poster's own height and leave a lot of blank space under it - but capping
                        // it outright would make the full title unreachable for someone who actually
                        // wants to read it (e.g. to search for it), so a tap expands it in place.
                        var originalTitleExpanded by remember(item.stableId) { mutableStateOf(false) }
                        val originalTitleSource = remember { MutableInteractionSource() }
                        Text(
                            "($originalTitle)",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (originalTitleExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .focusHighlight(originalTitleSource)
                                .clickable(interactionSource = originalTitleSource, indication = LocalIndication.current) {
                                    originalTitleExpanded = !originalTitleExpanded
                                }
                        )
                    }
                }
                Text(
                    listOfNotNull(
                        item.year?.toString(),
                        item.country,
                        item.runtimeMinutes?.let { "$it мин" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (item.genres.isNotEmpty()) {
                    // FlowRow, not a horizontally-scrolling Row (tried first, dropped per user
                    // feedback - same reasoning as the accent-color swatches in Settings: genre
                    // chips should all be visible at once, wrapping to a second line, not scrolled
                    // through).
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        item.genres.forEach { genre ->
                            Text(
                                genre,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Tagline and studio moved out of the narrow column next to the poster (where studio used
        // to sit) into one shared full-width block below everything - per user feedback, having
        // one crammed into that cramped column and the other as a bare line further down didn't
        // read as a deliberate part of the design. Grouped together in their own lightly-tinted
        // card here instead, both get the room to breathe a plain inline `Text` next to a poster
        // never had.
        val tagline = item.tagline?.takeIf { it.isNotBlank() }
        val studio = item.studio?.takeIf { it.isNotBlank() }
        val mpaa = item.mpaa?.takeIf { it.isNotBlank() }
        val premiered = item.premiered?.takeIf { it.isNotBlank() }
        val collectionName = item.collectionName?.takeIf { it.isNotBlank() }
        // Always rendered now (not gated on tagline/studio existing) - the audio/subtitle row
        // below moved in here per feedback and has content to show regardless of whether this
        // item even has a tagline or studio.
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            // Center, not the default Top - the right column (MPAA/premiere/collection) often has
            // fewer rows than the left (tagline/studio/audio/subtitles), and top-aligning both left
            // it stranded near the top with a visibly lopsided gap of dead space below it whenever
            // the two columns' row counts didn't match.
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tagline?.let {
                    Column {
                        Text(
                            stringResource(R.string.details_tagline_label),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            it,
                            style = MaterialTheme.typography.titleSmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                studio?.let {
                    Column {
                        Text(
                            stringResource(R.string.details_studio_label),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                // Moved in from its own row further down the card per feedback - grouped with
                // tagline/studio as "metadata about this file" rather than sitting right under the
                // description text with no visual relation to it. Two separate rows (not one shared
                // Row like the original standalone version) - a long audio track description (codec/
                // channels/bitrate) left almost no width for "Субтитры:" + icon, which then wrapped
                // one character per line instead of overflowing sanely.
                audioTracks?.takeIf { it.isNotEmpty() }?.let { tracks ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                                append(stringResource(R.string.details_audio_tracks_label))
                            }
                            append(tracks.joinToString("; "))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.details_subtitles_label),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val hasSubtitles = item.subtitlePaths.isNotEmpty()
                    Icon(
                        if (hasSubtitles) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (hasSubtitles) Color(0xFF4CAF50) else Color(0xFFE53935),
                        modifier = Modifier.padding(start = 4.dp).size(16.dp)
                    )
                    if (item.hasForcedSubtitles) {
                        Text(
                            stringResource(R.string.details_forced_subtitles_suffix),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
            // Right column: whatever room is left next to tagline/studio - MPAA rating, premiere
            // date and collection name were previously nowhere on this screen at all. Only shown
            // when at least one is present, and each field independently, since most items won't
            // have all three. Explicitly weighted (not just "whatever's left") - an unweighted
            // Column here sizes to its own unconstrained intrinsic width, and a long collection
            // name (e.g. "Очень страшное кино (Коллекция)") wanted enough of that to squeeze the
            // left column down to almost nothing, forcing the tagline to wrap one syllable per
            // line instead of at word boundaries.
            if (mpaa != null || premiered != null || collectionName != null) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    mpaa?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.details_mpaa_label),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // NFO mpaa content is often a messy raw certification string (e.g.
                            // "US:PG-13 / US:Rated PG-13") rather than something fit to show
                            // directly - shown as a plain 0+/6+/12+/16+/18+ age badge when it maps
                            // to a recognized rating, falling back to the raw text otherwise so
                            // nothing is silently hidden for an unrecognized format.
                            val ageLabel = ageRatingLabel(it)
                            if (ageLabel != null) {
                                AgeRatingBadge(ageLabel, modifier = Modifier.padding(top = 2.dp))
                            } else {
                                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                    premiered?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.details_premiered_label),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    collectionName?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.details_collection_name_label),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.End,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val playSource = remember { MutableInteractionSource() }
                Button(
                    onClick = { onPlay(item.stableId) },
                    interactionSource = playSource,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f).focusHighlight(playSource)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(
                        stringResource(if (hasStartedWatching) R.string.details_continue_watching else R.string.details_play),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (item.trailerPath != null) {
                    // Was icon-only (a bare Icons.Default.Theaters circle) - per user feedback, nothing
                    // about that icon alone actually reads as "trailer" to someone who hasn't already
                    // learned what it means here. A short label fixes that. Same weight(1f) as the
                    // play button so both buttons in this row end up the same size.
                    val trailerSource = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = { onPlayTrailer(item.stableId) },
                        interactionSource = trailerSource,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f).focusHighlight(trailerSource)
                    ) {
                        Icon(Icons.Default.Theaters, contentDescription = null)
                        Text(
                            stringResource(R.string.details_trailer),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
            DownloadButton(
                download = download,
                itemTitle = displayTitle,
                onStart = onStartDownload,
                onRemove = onRemoveDownload,
                onError = onDownloadError
            )
        }

        // Same backdrop treatment as the tagline/studio/audio/subtitles card above it (per
        // feedback) - a plain Text here previously had no visual container of its own at all.
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                stringResource(R.string.details_description_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                item.plot?.takeIf { it.isNotBlank() } ?: stringResource(R.string.details_no_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify
            )
        }

        if (episodes.isNotEmpty()) {
            EpisodeList(episodes, downloads, onPlay, onDownloadSeason, onDownloadEpisode, onRemoveEpisodeDownload, onRemoveSeasonDownloads)
        }

        if (item.director.isNotEmpty()) {
            PersonRow(stringResource(R.string.details_director), item.director, clickablePersons, onOpenPerson)
        }
        if (item.actors.isNotEmpty()) {
            PersonRow(stringResource(R.string.details_actors), item.actors, clickablePersons, onOpenPerson)
        }

        // > 1, not just non-empty - the current item is now included in this list (see
        // DetailsViewModel), so a collection of just itself (no real other parts) would otherwise
        // show a pointless one-poster row.
        if (collection.size > 1) {
            MediaRow(stringResource(R.string.details_collection), collection, onOpenItem, currentStableId = item.stableId)
        }
        if (similar.isNotEmpty()) {
            MediaRow(stringResource(R.string.details_similar), similar, onOpenItem)
        }
    }

    zoomedImage?.let { model ->
        ZoomableImageViewer(
            model = model,
            contentDescription = displayTitle,
            onDismiss = { zoomedImage = null },
            imageLoader = if (zoomedImageIsFanart) fanartImageLoader else null
        )
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
        null -> {
            // Same filled-Button design as "Смотреть" above it, not a bare icon - per user feedback,
            // an icon-only download affordance didn't read clearly enough next to a labeled button.
            val startSource = remember { MutableInteractionSource() }
            Button(
                onClick = onStart,
                interactionSource = startSource,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth().focusHighlight(startSource)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text(
                    stringResource(R.string.details_download_label),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
            val progress = if (download.totalBytes > 0) {
                (download.downloadedBytes.toFloat() / download.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            FilledTonalButton(
                onClick = onRemove,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.details_download_downloading_label, (progress * 100).toInt()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.details_download_cancel),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        DownloadStatus.COMPLETED -> {
            val removeSource = remember { MutableInteractionSource() }
            FilledTonalButton(
                onClick = { showRemoveConfirm = true },
                interactionSource = removeSource,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth().focusHighlight(removeSource)
            ) {
                Icon(Icons.Default.DownloadDone, contentDescription = null)
                Text(
                    stringResource(R.string.details_download_done_label),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (showRemoveConfirm) {
                AlertDialog(
                    onDismissRequest = { showRemoveConfirm = false },
                    title = { Text(stringResource(R.string.details_download_remove_confirm_title)) },
                    text = { Text(stringResource(R.string.details_download_remove_confirm_message, itemTitle)) },
                    confirmButton = {
                        val confirmSource = remember { MutableInteractionSource() }
                        TextButton(
                            onClick = {
                                showRemoveConfirm = false
                                onRemove()
                            },
                            interactionSource = confirmSource,
                            modifier = Modifier.focusHighlight(confirmSource)
                        ) {
                            Text(stringResource(R.string.details_download_remove_confirm_action))
                        }
                    },
                    dismissButton = {
                        val cancelDialogSource = remember { MutableInteractionSource() }
                        TextButton(
                            onClick = { showRemoveConfirm = false },
                            interactionSource = cancelDialogSource,
                            modifier = Modifier.focusHighlight(cancelDialogSource)
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
        DownloadStatus.FAILED -> {
            val errorMessage = stringResource(R.string.details_download_error_generic, download.errorMessage ?: stringResource(R.string.downloads_failed))
            val retrySource = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    onError(errorMessage)
                    onStart()
                },
                interactionSource = retrySource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth().focusHighlight(retrySource)
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null)
                Text(
                    stringResource(R.string.details_download_retry_label),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.focusGroup()
        ) {
            items(names, key = { it }) { name ->
                // Only worth a filmography screen when the person has more than one title here -
                // otherwise it's just this one item again, so the chip stays inert (greyed out).
                val clickable = name in clickablePersons
                val personSource = remember { MutableInteractionSource() }
                AssistChip(
                    onClick = { if (clickable) onOpenPerson(name) },
                    label = { Text(name) },
                    enabled = clickable,
                    interactionSource = personSource,
                    modifier = Modifier.focusHighlight(personSource)
                )
            }
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<MediaItemEntity>,
    downloads: Map<String, DownloadEntity>,
    onPlay: (String) -> Unit,
    onDownloadSeason: (List<String>) -> Unit,
    onDownloadEpisode: (String) -> Unit,
    onRemoveEpisodeDownload: (String) -> Unit,
    onRemoveSeasonDownloads: (List<String>) -> Unit
) {
    val bySeason = episodes
        .sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
        .groupBy { it.seasonNumber }

    // Collapsed by default ("spoiler"-style) - a season list can run to dozens of episode titles,
    // which for an unwatched show is itself a spoiler (episode titles/synopses give away plot
    // beats) as well as just a lot of scrolling to get past on the details page.
    var expandedSeasons by remember { mutableStateOf(emptySet<Int?>()) }
    var episodeDownloadToRemove by remember { mutableStateOf<MediaItemEntity?>(null) }
    var seasonDownloadsToRemove by remember { mutableStateOf<List<String>?>(null) }

    episodeDownloadToRemove?.let { episode ->
        AlertDialog(
            onDismissRequest = { episodeDownloadToRemove = null },
            title = { Text(stringResource(R.string.details_download_remove_confirm_title)) },
            text = { Text(stringResource(R.string.details_download_remove_confirm_message, episode.title)) },
            confirmButton = {
                val confirmSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        onRemoveEpisodeDownload(episode.stableId)
                        episodeDownloadToRemove = null
                    },
                    interactionSource = confirmSource,
                    modifier = Modifier.focusHighlight(confirmSource)
                ) { Text(stringResource(R.string.details_download_remove_confirm_action)) }
            },
            dismissButton = {
                val cancelSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { episodeDownloadToRemove = null },
                    interactionSource = cancelSource,
                    modifier = Modifier.focusHighlight(cancelSource)
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
    seasonDownloadsToRemove?.let { ids ->
        AlertDialog(
            onDismissRequest = { seasonDownloadsToRemove = null },
            title = { Text(stringResource(R.string.details_download_remove_confirm_title)) },
            text = { Text(stringResource(R.string.details_download_season_remove_confirm_message, ids.size)) },
            confirmButton = {
                val confirmSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        onRemoveSeasonDownloads(ids)
                        seasonDownloadsToRemove = null
                    },
                    interactionSource = confirmSource,
                    modifier = Modifier.focusHighlight(confirmSource)
                ) { Text(stringResource(R.string.details_download_remove_confirm_action)) }
            },
            dismissButton = {
                val cancelSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { seasonDownloadsToRemove = null },
                    interactionSource = cancelSource,
                    modifier = Modifier.focusHighlight(cancelSource)
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        bySeason.forEach { (season, seasonEpisodes) ->
            val expanded = season in expandedSeasons
            val seasonSource = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusHighlight(seasonSource)
                    .clickable(interactionSource = seasonSource, indication = LocalIndication.current) {
                        expandedSeasons = if (expanded) expandedSeasons - season else expandedSeasons + season
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (season != null) stringResource(R.string.details_season, season) else stringResource(R.string.details_episodes),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.details_episode_count, seasonEpisodes.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                val seasonHasDownloads = seasonEpisodes.any { downloads[it.stableId]?.status == DownloadStatus.COMPLETED }
                val seasonDownloadSource = remember { MutableInteractionSource() }
                IconButton(
                    onClick = {
                        val ids = seasonEpisodes.map { it.stableId }
                        if (seasonHasDownloads) seasonDownloadsToRemove = ids else onDownloadSeason(ids)
                    },
                    interactionSource = seasonDownloadSource,
                    modifier = Modifier.size(36.dp).focusHighlight(seasonDownloadSource)
                ) {
                    Icon(
                        if (seasonHasDownloads) Icons.Default.Delete else Icons.Default.Download,
                        contentDescription = stringResource(
                            if (seasonHasDownloads) R.string.details_download_season_remove else R.string.details_download_season
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                seasonEpisodes.forEach { episode ->
                    val label = listOfNotNull(
                        episode.seasonNumber?.let { s -> episode.episodeNumber?.let { e -> "S${s}E$e" } },
                        episode.title
                    ).joinToString(" · ")
                    val episodeSource = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusHighlight(episodeSource)
                            .clickable(interactionSource = episodeSource, indication = LocalIndication.current) { onPlay(episode.stableId) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(16f / 9f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            ThumbnailImage(episode.episodeThumbModel, contentDescription = null)
                        }
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Row {
                                Text(label.ifBlank { episode.title }, modifier = Modifier.weight(1f))
                                episode.premiered?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            episode.plot?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        when (downloads[episode.stableId]?.status) {
                            DownloadStatus.COMPLETED -> {
                                val episodeRemoveSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = { episodeDownloadToRemove = episode },
                                    interactionSource = episodeRemoveSource,
                                    modifier = Modifier.padding(start = 4.dp).size(36.dp).focusHighlight(episodeRemoveSource)
                                ) {
                                    Icon(
                                        Icons.Default.DownloadDone,
                                        contentDescription = stringResource(R.string.details_download_remove),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                                modifier = Modifier.padding(start = 4.dp).size(20.dp),
                                strokeWidth = 2.dp
                            )
                            else -> {
                                val episodeDownloadSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = { onDownloadEpisode(episode.stableId) },
                                    interactionSource = episodeDownloadSource,
                                    modifier = Modifier.padding(start = 4.dp).size(36.dp).focusHighlight(episodeDownloadSource)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.details_download))
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun MediaRow(title: String, items: List<MediaItemEntity>, onOpenItem: (String) -> Unit, currentStableId: String? = null) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.focusGroup()
        ) {
            items(items, key = { it.stableId }) { item ->
                PosterCard(
                    item = item,
                    onClick = { onOpenItem(item.stableId) },
                    modifier = Modifier.width(110.dp),
                    isCurrent = item.stableId == currentStableId
                )
            }
        }
    }
}

/**
 * Maps a raw NFO `<mpaa>` certification string to a plain 0+/6+/12+/18+ age badge. Handles the
 * common US/MPAA and TV-parental-guideline codes plus an already-numeric "16+"-style value
 * verbatim; returns null for anything unrecognized so the caller can fall back to showing the raw
 * text instead of silently hiding it.
 */
private fun ageRatingLabel(mpaa: String): String? {
    val upper = mpaa.uppercase()
    Regex("""\b(0|6|12|16|18)\+""").find(upper)?.let { return it.value }
    return when {
        upper.contains("NC-17") -> "18+"
        upper.contains("TV-MA") || Regex("""\bR\b""").containsMatchIn(upper) -> "18+"
        upper.contains("PG-13") || upper.contains("TV-14") -> "16+"
        upper.contains("TV-Y7") || upper.contains("TV-PG") || Regex("""\bPG\b""").containsMatchIn(upper) -> "12+"
        upper.contains("TV-Y") || upper.contains("TV-G") || Regex("""\bG\b""").containsMatchIn(upper) -> "0+"
        else -> null
    }
}

@Composable
private fun AgeRatingBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color(0xFFE53935), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
