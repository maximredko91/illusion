package com.illusion.app.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.tooling.preview.Preview
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
import com.illusion.app.domain.model.UiMode
import com.illusion.app.domain.model.editionLabel
import com.illusion.app.domain.model.statusLabel
import com.illusion.app.domain.model.videoQualityLabel
import com.illusion.app.data.player.AudioTrackProber
import com.illusion.app.data.repository.AudioTrackRepository
import com.illusion.app.data.repository.DownloadRepository
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.WatchProgressRepository
import com.illusion.app.ui.common.LocalUiMode
import com.illusion.app.ui.common.PosterCard
import com.illusion.app.ui.common.RatingBadge
import com.illusion.app.ui.common.ThumbnailImage
import com.illusion.app.ui.common.shimmer
import com.illusion.app.ui.common.ZoomableImageViewer
import com.illusion.app.ui.common.bridgeFocusDown
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.tick
import com.illusion.app.ui.common.toggle
import com.illusion.app.ui.theme.IllusionTheme

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
    // See TvFocus.bridgeFocusDown's own KDoc - back/home float outside the scrollable content
    // below them, which stranded D-pad focus on just those two buttons with no way to reach
    // anything else on a real Android TV.
    val contentFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

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
                folderCollection = state.folderCollection,
                episodes = state.episodes,
                isFavorite = isFavorite,
                onToggleFavorite = viewModel::toggleFavorite,
                isWatched = watchProgress?.watched == true,
                onToggleWatched = viewModel::toggleWatched,
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
                // Previously this just always navigated straight into the player, which then
                // errored on its own SMB connection attempt if there was no Wi-Fi - a real "no
                // Wi-Fi" state read as a broken player. A completed download plays from a local
                // file regardless of network, so it's the one case allowed through unconditionally.
                onPlay = {
                    if (download?.status == DownloadStatus.COMPLETED || com.illusion.app.ui.common.isOnLocalNetwork(context)) {
                        onPlay(item.stableId)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.details_offline_warning)) }
                    }
                },
                onPlayTrailer = {
                    // Trailers are never downloaded (see the app's own README/CLAUDE notes - TMDB
                    // has no downloadable file), so there's no local-file exception here.
                    if (com.illusion.app.ui.common.isOnLocalNetwork(context)) {
                        onPlayTrailer(item.stableId)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.details_offline_warning)) }
                    }
                },
                onOpenPerson = onOpenPerson,
                onOpenItem = onOpenItem,
                contentFocusRequester = contentFocusRequester
            )
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            else -> Text(
                stringResource(R.string.details_not_found),
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
        // Sits here (a sibling of the scrolling DetailsContent, not inside it) so it stays fixed
        // on screen instead of scrolling away with the fanart - per feedback. A translucent accent
        // pill backdrop (rather than the earlier opaque black one) stays legible over any photo
        // while still reading as glass rather than a solid UI chrome bar, and the icon itself keeps
        // its own theme-driven tint independent of the pill's color.
        val haptics = LocalHapticFeedback.current
        val cornerIconTint = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color.Black else Color.White
        val cornerPillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(com.illusion.app.ui.common.rememberLatchedStatusBarsInsets())
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val backSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = {
                    haptics.tick()
                    onBack()
                },
                interactionSource = backSource,
                modifier = Modifier
                    .background(cornerPillColor, CircleShape)
                    .focusHighlight(backSource, color = cornerIconTint)
                    .bridgeFocusDown(contentFocusRequester)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.details_back),
                    tint = cornerIconTint
                )
            }
            // Escape hatch for drilling several Similar/series hops deep (Movie -> part 2 -> part
            // 3 -> ...) - onBack still steps back one card at a time (so returning to the card the
            // user actually came from works normally), this jumps straight to the main screen
            // instead of requiring one "Назад" per hop.
            val homeSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = {
                    haptics.tick()
                    onGoHome()
                },
                interactionSource = homeSource,
                modifier = Modifier
                    .background(cornerPillColor, CircleShape)
                    .focusHighlight(homeSource, color = cornerIconTint)
                    .bridgeFocusDown(contentFocusRequester)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = stringResource(R.string.details_go_home),
                    tint = cornerIconTint
                )
            }
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
    folderCollection: List<MediaItemEntity>,
    episodes: List<MediaItemEntity>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isWatched: Boolean,
    onToggleWatched: () -> Unit,
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
    contentFocusRequester: androidx.compose.ui.focus.FocusRequester? = null
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
        val haptics = LocalHapticFeedback.current
        // Phone sizing reused as-is on TV made the fanart/poster look tiny from couch distance on
        // a real TV screen (confirmed on-device) - same couch-distance reasoning as
        // posterCardMinWidth() elsewhere, just applied to Details' own header instead of a grid
        // cell. Declared at this scope (not inside the fanart Box below) since the poster size
        // further down needs it too.
        val isTv = LocalUiMode.current == UiMode.TV
        Box {
            val fanart = item.fanartModel
            val fanartSource = remember { MutableInteractionSource() }
            // Matches the floating back/home buttons' own footprint (IconButton's default touch
            // target) - they float on top of this fanart now rather than living inside it (see
            // DetailsScreen's own overlay), but the dead zone here still needs to line up with
            // where a near-miss on one of them actually lands.
            val cornerButtonSize = 48.dp
            val fanartHeight = if (isTv) 460.dp else 220.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fanartHeight)
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
                    //
                    // The same alpha/stop values read very differently depending on the resolved
                    // background color: a dark background blends into a photo like a natural
                    // vignette, but the identical curve in a light theme washes the art out into a
                    // near-white haze over roughly the same area. Per feedback, the top fade was
                    // never the actual problem (kept at dark theme's own values below) - it's
                    // specifically the BOTTOM dissolve fading to a light/white background that
                    // reads as covering half the fanart. Pushed further down (0.87 vs 0.62) so only
                    // the last ~13% actually washes toward opaque, instead of the last third.
                    val backgroundColor = MaterialTheme.colorScheme.background
                    val isLightBackground = backgroundColor.luminance() > 0.5f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isLightBackground) {
                                    Brush.verticalGradient(
                                        0f to backgroundColor.copy(alpha = 0.28f),
                                        0.14f to Color.Transparent,
                                        0.87f to Color.Transparent,
                                        1f to backgroundColor
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        0f to backgroundColor.copy(alpha = 0.28f),
                                        0.14f to Color.Transparent,
                                        0.62f to Color.Transparent,
                                        1f to backgroundColor
                                    )
                                }
                            )
                    )
                    // TV-only "hero" title overlay - Netflix/Google TV-style, the title sits
                    // directly on the backdrop instead of only appearing in the metadata row
                    // below (phone's layout, unchanged). Purely additive on top of the existing
                    // gradient - doesn't touch the zoom-click hit box, the corner buttons, or any
                    // of the phone-only layout math below it. The title still also appears in its
                    // usual place further down (shared code path with phone) - deliberately not
                    // removed there, since collapsing that would mean threading isTv through
                    // several more tightly-coupled measurements (title-wrap line caps, the empty-
                    // space-under-a-short-title spacing) for no real benefit; a title shown twice
                    // is harmless, a broken layout isn't.
                    if (isTv) {
                        Text(
                            item.title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                        )
                    }
                    // Tap-to-zoom, phone only - TV has no pinch/pan gesture to make a zoomed
                    // viewer useful, and a D-pad has nothing sensible to focus it with either
                    // (matches the original-title tap-to-expand right below, which draws the same
                    // isTv line). Zoom only triggers from this inset center region, not the full
                    // fanart - a full-bleed clickable here meant a near-miss on the back/home/
                    // favorite buttons (all anchored to this box's own corners) fell straight
                    // through to opening the zoomed viewer instead, since a tap just outside a
                    // button's actual touch target still landed on this Box underneath it. Purely
                    // a hit-test layer, no visuals.
                    if (!isTv && fanart != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(cornerButtonSize)
                                .focusHighlight(fanartSource)
                                .clickable(interactionSource = fanartSource, indication = LocalIndication.current) {
                                    zoomedImage = fanart
                                    zoomedImageIsFanart = true
                                }
                        )
                    }
                }
            }
        }

        // getString (not stringResource) since the hint text is picked inside an onClick lambda,
        // not composed directly - stringResource can't be called from a non-composable callback.
        val hintContext = androidx.compose.ui.platform.LocalContext.current
        // ONE shared hint, not one per icon - two independent bubbles could both be visible at
        // once (tapping both icons in quick succession) and, sitting right next to each other in
        // a narrow column, their text visibly overlapped. Both icon buttons below update this
        // single state instead of their own.
        var hintGeneration by remember { mutableStateOf(0) }
        var hintVisible by remember { mutableStateOf(false) }
        var hintText by remember { mutableStateOf("") }
        LaunchedEffect(hintGeneration) {
            if (hintGeneration == 0) return@LaunchedEffect
            hintVisible = true
            kotlinx.coroutines.delay(1500)
            hintVisible = false
        }
        // The bubble itself is rendered as this Box's LAST child, below - Compose draws a Box's
        // children in declaration order, so being last guarantees it paints on top of *both* the
        // poster column (which owns the icon buttons that trigger it) and the metadata column
        // next to it. It used to live inside the poster column's own local Box instead - that
        // subtree draws before the metadata column's (declared right after it in the outer Row),
        // so wherever the bubble grew far enough right to visually reach the genre chips, those
        // chips (painted later) rendered on top of it instead of the other way around.
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row {
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
                val posterSource = remember { MutableInteractionSource() }
                val posterWidth = if (isTv) 200.dp else 132.dp
                Column(modifier = Modifier.width(posterWidth)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .let { if (contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                            .focusHighlight(posterSource)
                            .clickable(interactionSource = posterSource, indication = LocalIndication.current) { zoomedImage = poster; zoomedImageIsFanart = false }
                    ) {
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
                    // Moved down here from two floating corner buttons on the fanart (per user
                    // feedback) - no more translucent circle backdrop (that was only ever needed to
                    // stay legible over an arbitrary photo; sitting under the poster, both buttons
                    // are on the screen's own themed background instead) and both tints now come
                    // from the color scheme so they read correctly in either light or dark theme,
                    // instead of a fixed white that would have washed out on a light background.
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        val favoriteScale = remember { Animatable(1f) }
                        val favoriteScope = rememberCoroutineScope()
                        val favoriteTint by animateColorAsState(
                            targetValue = if (isFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "favoriteTint"
                        )
                        // Fixed size (matches IconButton's own default touch target) rather than
                        // wrapping content - an implicitly-sized Box here would grow/shrink this
                        // whole Box (a direct child of the SpaceBetween Row above) as its content
                        // changed, shifting the Row's layout.
                        Box(modifier = Modifier.size(48.dp)) {
                            com.illusion.app.ui.common.TvAwareIconButton(
                                onClick = {
                                    haptics.toggle(!isFavorite)
                                    onToggleFavorite()
                                    hintText = hintContext.getString(
                                        if (!isFavorite) R.string.details_favorite_added_hint else R.string.details_favorite_removed_hint
                                    )
                                    hintGeneration++
                                    favoriteScope.launch {
                                        favoriteScale.snapTo(0.7f)
                                        favoriteScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                    }
                                }
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
                        val watchedScale = remember { Animatable(1f) }
                        val watchedScope = rememberCoroutineScope()
                        val watchedTint by animateColorAsState(
                            targetValue = if (isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "watchedTint"
                        )
                        Box(modifier = Modifier.size(48.dp)) {
                            com.illusion.app.ui.common.TvAwareIconButton(
                                onClick = {
                                    haptics.toggle(!isWatched)
                                    onToggleWatched()
                                    hintText = hintContext.getString(
                                        if (!isWatched) R.string.details_watched_added_hint else R.string.details_watched_removed_hint
                                    )
                                    hintGeneration++
                                    watchedScope.launch {
                                        watchedScale.snapTo(0.7f)
                                        watchedScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                    }
                                }
                            ) {
                                Crossfade(targetState = isWatched, label = "watchedIcon") { watched ->
                                    Icon(
                                        imageVector = if (watched) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = stringResource(
                                            if (watched) R.string.details_watched_remove else R.string.details_watched_add
                                        ),
                                        tint = watchedTint,
                                        modifier = Modifier.scale(watchedScale.value)
                                    )
                                }
                            }
                        }
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
                        // Capped at 2 lines on phone so a long original title can't tower past the
                        // poster's own height and leave a lot of blank space under it - a tap
                        // expands it in place there (touch-only affordance, so it's worth the
                        // extra interactivity). The TV layout's poster/metadata column is taller
                        // (200dp poster vs 132dp) so it just shows the full text unconditionally
                        // instead - no tap target, no focus/selection highlight (that's what was
                        // actually reported as broken on TV: a stray D-pad focus box landing on
                        // plain informational text with nothing to do there).
                        if (isTv) {
                            Text(
                                "($originalTitle)",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            var originalTitleExpanded by remember(item.stableId) { mutableStateOf(false) }
                            val originalTitleSource = remember { MutableInteractionSource() }
                            Text(
                                "($originalTitle)",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (originalTitleExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(
                                    interactionSource = originalTitleSource,
                                    indication = LocalIndication.current
                                ) { originalTitleExpanded = !originalTitleExpanded }
                            )
                        }
                    }
                }
                Text(
                    listOfNotNull(
                        item.year?.toString(),
                        item.country,
                        item.runtimeMinutes?.let { "$it мин" },
                        item.videoQualityLabel,
                        item.editionLabel
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
        // Anchored to this Box's own top-start (which now carries the 16dp horizontal padding
        // that used to live on the Row directly above, so this shares the same coordinate frame
        // as the icon row rather than landing 16dp off from it) - the vertical offset is derived,
        // not eyeballed: poster width is a fixed 132dp at a fixed 2:3 aspect ratio, so its image
        // is exactly 198dp tall; the icon row sits right below it with 4dp of its own top padding,
        // putting the icon row's own top edge at 202dp. 28dp above that (174dp) lands the bubble
        // just above the icons, same visual gap the old per-icon placement used.
        ActionHintBubble(
            text = hintText,
            visible = hintVisible,
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).align(Alignment.TopStart).offset(y = 174.dp)
        )
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
        // Only ever non-null for a series item (show-level tvshow.nfo <status> - see
        // MediaItemEntity.status's own KDoc), so this doubles as the "is this a series" gate here
        // without needing a separate category check.
        val seriesStatus = item.statusLabel
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
            if (mpaa != null || premiered != null || collectionName != null || seriesStatus != null) {
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
                            Text(formatPremieredDate(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    seriesStatus?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.details_series_status_label),
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

        ActionButtonsRow(
            hasStartedWatching = hasStartedWatching,
            hasTrailer = item.trailerPath != null,
            download = download,
            itemTitle = displayTitle,
            onPlay = { onPlay(item.stableId) },
            onPlayTrailer = { onPlayTrailer(item.stableId) },
            onStartDownload = onStartDownload,
            onRemoveDownload = onRemoveDownload,
            onDownloadError = onDownloadError
        )

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
        // show a pointless one-poster row. Also skip the nfo-based "Другие части" row entirely
        // when it has the same item count as the folder-based "Коллекция" row below it - in
        // practice that means the two are the same set of movies (the folder collection already
        // wins over nfo on any real disagreement, see LibraryScanner), so showing both was just
        // the same row twice under different labels (reported on-device as visibly duplicated).
        if (collection.size > 1 && collection.size != folderCollection.size) {
            MediaRow(stringResource(R.string.details_collection), collection, onOpenItem, currentStableId = item.stableId)
        }
        if (folderCollection.size > 1) {
            MediaRow(stringResource(R.string.details_folder_collection), folderCollection, onOpenItem, currentStableId = item.stableId)
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

/** Small transient confirmation ("Добавлено в избранное", ...) anchored next to the favorite/watched buttons - the icon/color swap alone wasn't a clear enough confirmation on its own per feedback. */
@Composable
private fun ActionHintBubble(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            tonalElevation = 4.dp
        ) {
            Text(
                text,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** Play/Trailer/download row from the top of Details - extracted so it's previewable in isolation (see the @Preview functions right below DownloadButton) without needing a full MediaItemEntity/ViewModel. */
@Composable
private fun ActionButtonsRow(
    hasStartedWatching: Boolean,
    hasTrailer: Boolean,
    download: DownloadEntity?,
    itemTitle: String,
    onPlay: () -> Unit,
    onPlayTrailer: () -> Unit,
    onStartDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDownloadError: (String) -> Unit
) {
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
            com.illusion.app.ui.common.TvAwareButton(
                onClick = onPlay,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(
                    stringResource(if (hasStartedWatching) R.string.details_continue_watching else R.string.details_play),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (hasTrailer) {
                // Was icon-only (a bare Icons.Default.Theaters circle) - per user feedback, nothing
                // about that icon alone actually reads as "trailer" to someone who hasn't already
                // learned what it means here. A short label fixes that. Same weight(1f) as the
                // play button so both buttons in this row end up the same size.
                val trailerSource = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onPlayTrailer,
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
            itemTitle = itemTitle,
            onStart = onStartDownload,
            onRemove = onRemoveDownload,
            onError = onDownloadError
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
            com.illusion.app.ui.common.TvAwareButton(
                onClick = onStart,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth()
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

// Previews for the redesigned action-button row (Play/Trailer equal-width, DownloadButton below in
// the same filled-Button style as Play) - one per DownloadEntity state, since that's what actually
// changes DownloadButton's look. No real MediaItemEntity/ViewModel needed, just fake DownloadEntity
// values - see ActionButtonsRow's own KDoc for why this was extracted out of DetailsScreen for this.
@Preview(showBackground = true)
@Composable
private fun ActionButtonsRowNotDownloadedPreview() {
    IllusionTheme {
        Surface {
            ActionButtonsRow(
                hasStartedWatching = false,
                hasTrailer = true,
                download = null,
                itemTitle = "Пример фильма",
                onPlay = {}, onPlayTrailer = {}, onStartDownload = {}, onRemoveDownload = {}, onDownloadError = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionButtonsRowDownloadingPreview() {
    IllusionTheme {
        Surface {
            ActionButtonsRow(
                hasStartedWatching = true,
                hasTrailer = false,
                download = DownloadEntity(
                    stableId = "preview",
                    contentUri = "content://preview",
                    status = DownloadStatus.DOWNLOADING,
                    totalBytes = 1_000_000_000L,
                    downloadedBytes = 420_000_000L,
                    updatedAt = 0L
                ),
                itemTitle = "Пример фильма",
                onPlay = {}, onPlayTrailer = {}, onStartDownload = {}, onRemoveDownload = {}, onDownloadError = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionButtonsRowDownloadedPreview() {
    IllusionTheme {
        Surface {
            ActionButtonsRow(
                hasStartedWatching = true,
                hasTrailer = true,
                download = DownloadEntity(
                    stableId = "preview",
                    contentUri = "content://preview",
                    status = DownloadStatus.COMPLETED,
                    totalBytes = 1_000_000_000L,
                    downloadedBytes = 1_000_000_000L,
                    updatedAt = 0L
                ),
                itemTitle = "Пример фильма",
                onPlay = {}, onPlayTrailer = {}, onStartDownload = {}, onRemoveDownload = {}, onDownloadError = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionButtonsRowFailedPreview() {
    IllusionTheme {
        Surface {
            ActionButtonsRow(
                hasStartedWatching = false,
                hasTrailer = false,
                download = DownloadEntity(
                    stableId = "preview",
                    contentUri = "content://preview",
                    status = DownloadStatus.FAILED,
                    totalBytes = 1_000_000_000L,
                    downloadedBytes = 120_000_000L,
                    updatedAt = 0L,
                    errorMessage = "Соединение потеряно"
                ),
                itemTitle = "Пример фильма",
                onPlay = {}, onPlayTrailer = {}, onStartDownload = {}, onRemoveDownload = {}, onDownloadError = {}
            )
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
                com.illusion.app.ui.common.TvAwareAssistChip(
                    onClick = { if (clickable) onOpenPerson(name) },
                    label = { Text(name) },
                    enabled = clickable
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
                com.illusion.app.ui.common.TvAwareIconButton(
                    onClick = {
                        val ids = seasonEpisodes.map { it.stableId }
                        if (seasonHasDownloads) seasonDownloadsToRemove = ids else onDownloadSeason(ids)
                    },
                    modifier = Modifier.size(36.dp)
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
            val seasonAnimMs = com.illusion.app.ui.common.economicalDurationMs(300)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(seasonAnimMs)) + expandVertically(tween(seasonAnimMs)),
                exit = fadeOut(tween(seasonAnimMs)) + shrinkVertically(tween(seasonAnimMs))
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
                                        formatPremieredDate(it),
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
                                com.illusion.app.ui.common.TvAwareIconButton(
                                    onClick = { episodeDownloadToRemove = episode },
                                    modifier = Modifier.padding(start = 4.dp).size(36.dp)
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
                                com.illusion.app.ui.common.TvAwareIconButton(
                                    onClick = { onDownloadEpisode(episode.stableId) },
                                    modifier = Modifier.padding(start = 4.dp).size(36.dp)
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

/**
 * NFO `<premiered>`/`<aired>` values are Kodi/tinyMediaManager's own ISO `yyyy-MM-dd` format, but
 * were shown completely raw here ("1999-09-01") instead of a readable localized date. Malformed or
 * non-ISO values (a bare year, "N/A", an empty scraper placeholder, ...) are shown unchanged rather
 * than hidden - same fallback approach [ageRatingLabel]'s caller already uses for a messy `mpaa`.
 */
private fun formatPremieredDate(raw: String): String =
    runCatching { java.time.LocalDate.parse(raw) }
        .map { it.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("ru"))) }
        .getOrDefault(raw)

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
