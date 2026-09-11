package com.illusion.app.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MovieCreation
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
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
import com.illusion.app.domain.model.genreDisplayName
import com.illusion.app.ui.common.LocalUiMode
import com.illusion.app.ui.common.PosterCard
import com.illusion.app.ui.common.ThumbnailImage
import com.illusion.app.ui.common.shimmer
import com.illusion.app.ui.common.ZoomableImageViewer
import com.illusion.app.ui.common.bridgeFocusDown
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.formatWatchLeft
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
    val offlineWarning = stringResource(R.string.details_offline_warning)
    val downloadOfflineWarning = stringResource(R.string.details_offline_warning_download)
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
                resumePositionMs = watchProgress?.positionMs ?: 0L,
                totalDurationMs = watchProgress?.durationMs ?: 0L,
                download = download,
                downloads = downloads,
                // Скачивание тянет файл с того же NAS, что и воспроизведение, поэтому без
                // домашней сети оно так же бессмысленно - раньше проверка стояла только на
                // «Смотреть» и «Трейлер», а загрузка молча ставилась в очередь и падала уже
                // внутри воркера, где пользователь её не видит.
                onStartDownload = {
                    if (com.illusion.app.ui.common.isOnLocalNetwork(context)) {
                        viewModel.startDownload(context)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(downloadOfflineWarning) }
                    }
                },
                onRemoveDownload = { viewModel.removeDownload(context) },
                onDownloadSeason = { ids ->
                    if (com.illusion.app.ui.common.isOnLocalNetwork(context)) {
                        viewModel.startSeasonDownload(context, ids)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(downloadOfflineWarning) }
                    }
                },
                onDownloadEpisode = { id ->
                    if (com.illusion.app.ui.common.isOnLocalNetwork(context)) {
                        viewModel.startDownload(context, id)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(downloadOfflineWarning) }
                    }
                },
                onRemoveEpisodeDownload = { id -> viewModel.removeDownload(context, id) },
                onRemoveSeasonDownloads = { ids -> viewModel.removeSeasonDownloads(context, ids) },
                onDownloadError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                // Previously this just always navigated straight into the player, which then
                // errored on its own SMB connection attempt if there was no Wi-Fi - a real "no
                // Wi-Fi" state read as a broken player. A completed download plays from a local
                // file regardless of network, so it's the one case allowed through unconditionally.
                // Takes the id it's given, NOT item.stableId - this lambda used to discard its
                // argument, so tapping any episode in the list played the show's representative
                // episode (S1E1) instead of the one tapped, confirmed on-device.
                onPlay = { stableId ->
                    if (download?.status == DownloadStatus.COMPLETED || com.illusion.app.ui.common.isOnLocalNetwork(context)) {
                        onPlay(stableId)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(offlineWarning) }
                    }
                },
                onPlayTrailer = { stableId ->
                    // Trailers are never downloaded (see the app's own README/CLAUDE notes - TMDB
                    // has no downloadable file), so there's no local-file exception here.
                    if (com.illusion.app.ui.common.isOnLocalNetwork(context)) {
                        onPlayTrailer(stableId)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(offlineWarning) }
                    }
                },
                onPlaySeasonTrailer = onPlayTrailer,
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
        // Раньше кнопки висели поверх фанарта полупрозрачными кружками и закрывали собой картинку,
        // а при прокрутке - ещё и текст под собой. Теперь это обычная полоса в цвет темы над
        // фанартом: картинка начинается под ней и ничем не перекрывается (высота полосы
        // зарезервирована в DetailsContent, см. TOP_BAR_ROW_HEIGHT).
        val cornerIconTint = MaterialTheme.colorScheme.onSurface
        val cornerPillColor = Color.Transparent
        // Нижняя грань полосы была прямым срезом во всю ширину и читалась остро - отсюда
        // скруглённые нижние углы. Захода на фанарт больше нет: он был нужен, пока кадр
        // растворялся заливкой и в углах должен был выглядывать из-под полосы. Сейчас кадр чистый
        // и со своим скруглением, а заход просто прятал его верхние 18dp под полосой.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(bottomStart = TOP_BAR_OVERLAP, bottomEnd = TOP_BAR_OVERLAP)
                )
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(com.illusion.app.ui.common.rememberLatchedStatusBarsInsets())
                // На TV кнопки и их рамка фокуса упирались в угол экрана.
                .padding(horizontal = 4.dp + com.illusion.app.ui.common.LocalTvSafeMarginDp.current)
                .padding(top = com.illusion.app.ui.common.LocalTvSafeMarginDp.current),
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
                    .size(TOP_BAR_ROW_HEIGHT)
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
                    .size(TOP_BAR_ROW_HEIGHT)
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
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Высота верхней полосы без статус-бара. Она же задаёт размер обеих кнопок - полоса ровно в их
 * рост, лишнего воздуха сверху и снизу нет. Шла от 56dp (48dp IconButton по умолчанию плюс по 4dp
 * отступа у строки) к 48, теперь 40: полоса обжимает иконку в 24dp с 8dp полями.
 *
 * 40dp - нижняя разумная граница. Рекомендация Material - 48dp на цель нажатия, и высоту здесь
 * задаёт именно кнопка, так что дальше ужимать полосу можно только за счёт удобства попадания.
 *
 * Фиксированное число, а не замер onSizeChanged - замер зависел бы от тех же инсетов, гонку с
 * которыми здесь уже приходится обходить вручную (см. statusBarsTopDp ниже).
 */
private val TOP_BAR_ROW_HEIGHT = 40.dp

/**
 * Насколько фанарт заходит под верхнюю полосу, он же радиус скругления её нижних углов. Без
 * захода скруглённые углы вырезали бы не картинку, а пустой фон страницы, и смысла в них бы не
 * было; с заходом в углах видно сам кадр, и грань читается мягкой.
 */
private val TOP_BAR_OVERLAP = 18.dp

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
    resumePositionMs: Long,
    totalDurationMs: Long,
    download: DownloadEntity?,
    downloads: Map<String, DownloadEntity>,
    onStartDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onPlaySeasonTrailer: (String) -> Unit,
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
            .padding(top = statusBarsTopDp + TOP_BAR_ROW_HEIGHT)
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
            // TV Box - 540dp в высоту: при 400dp кнопки уезжали за нижний край экрана.
            val fanartHeight = if (isTv) 160.dp else 188.dp
            // Кадр ничем не заливается, только скруглён снизу - в обеих темах одинаково.
            //
            // Раньше верх и низ растворялись в цвет фона градиентом. В тёмной теме это читалось
            // виньеткой, а в светлой белый поверх тёмного кадра давал серую дымку - грязные
            // полосы сверху и снизу. Длину градиента подбирали трижды, каждый раз оставалось грязно.
            // Держать разные решения по темам тоже не вариант: получались два разных стиля одного
            // экрана. Чёткий край со скруглением работает везде: картинка остаётся чистой.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fanartHeight)
                    .clip(RoundedCornerShape(bottomStart = TOP_BAR_OVERLAP, bottomEnd = TOP_BAR_OVERLAP))
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
                        // Затемнение под названием - на светлом кадре белый текст не читался.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                    )
                                )
                        )
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

        Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
            val poster = item.posterModel
            if (poster != null) {
                // Shared-element bounds-morph from the grid poster removed (per user feedback -
                // see the matching note in PosterCard.kt) - Details now just fades in/out instead.
                // Used to pull the poster up by a fixed 42dp so it overlapped the fanart above it
                // (a "hero card" look) - dropped per user feedback: the fanart's own height here
                // is a fixed 220dp regardless of screen size, so that overlap had no situation
                // where it was actually needed for space, it just permanently covered part of the
                // fanart image. A small fixed gap now separates the rounded fanart edge from the
                // poster, so the two images don't visually merge.
                //
                // Stretching the poster to match the metadata column's height (via
                // Modifier.height(IntrinsicSize.Min) on the Row + fillMaxHeight here) was tried to
                // close the empty space a long original title left underneath the poster - dropped,
                // it fed back on itself: a taller poster claims more width to keep its aspect ratio,
                // which leaves the title column narrower, which wraps the title onto even more
                // lines, which grows the column taller still. The title itself is now capped at 4
                // lines below instead, which keeps the column from running away in the first place.
                val posterSource = remember { MutableInteractionSource() }
                // 184dp на TV давало постер ~550px в высоту - кнопки уезжали за экран.
                val posterWidth = if (isTv) 110.dp else 120.dp
                Column(
                    modifier = Modifier.width(posterWidth),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                }
                // Свойства именно этого файла - качество картинки и издание («Режиссёрская
                // версия»). Раньше издание стояло среди жанров, а качество было доступно только
                // в блоке фактов далеко внизу. Здесь они и не путаются с жанрами, и заполняют
                // пустоту, которая оставалась под постером рядом с более высокой колонкой справа.
                //
                // Были просто текстом в тонкой рамке и не читались как что-то значащее - теперь у
                // каждого своя тональная заливка и значок: качество акцентом (это главное свойство
                // файла), издание - вторичным цветом.
                item.videoQualityLabel?.let { quality ->
                    TechTagChip(
                        text = quality,
                        icon = Icons.Default.HighQuality,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                item.editionLabel?.let { edition ->
                    TechTagChip(
                        text = edition,
                        icon = Icons.Default.MovieCreation,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer
                    )
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
                        style = (if (!isTv && displayTitle.length > 24) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.headlineSmall
                        }).copy(fontWeight = FontWeight.Bold)
                    )
                    if (originalTitle != null) {
                        // Capped at 2 lines on phone so a long original title can't tower past the
                        // poster's own height and leave a lot of blank space under it - a tap
                        // expands it in place there (touch-only affordance, so it's worth the
                        // extra interactivity). The TV layout's poster/metadata column is taller
                        // (184dp poster vs 120dp) so it just shows the full text unconditionally
                        // instead - no tap target, no focus/selection highlight (that's what was
                        // actually reported as broken on TV: a stray D-pad focus box landing on
                        // plain informational text with nothing to do there).
                        if (isTv) {
                            Text(
                                "($originalTitle)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            var originalTitleExpanded by remember(item.stableId) { mutableStateOf(false) }
                            val originalTitleSource = remember { MutableInteractionSource() }
                            Text(
                                "($originalTitle)",
                                style = MaterialTheme.typography.titleMedium,
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.rating?.let { rating ->
                            Text(
                                "★ ${String.format(java.util.Locale.forLanguageTag("ru"), "%.1f", rating)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val isSeries = item.seriesStableId != null
                        val yearAndRuntime = listOfNotNull(
                            // A show's title comes from its folder name, which conventionally
                            // already carries the year ("Клиника (2001)") - repeating it right underneath
                            // read as a mistake.
                            item.year?.toString()?.takeIf { !TITLE_YEAR_PATTERN.containsMatchIn(displayTitle) },
                            // For a series the item behind this screen is one episode, so its own
                            // runtime ("22 мин") described that episode while sitting exactly where a
                            // film's total runtime goes. Season/episode counts belong in that slot
                            // for a show.
                            if (isSeries) seasonsAndEpisodesLabel(episodes) else formatRuntime(item.runtimeMinutes)
                        ).joinToString(" · ")
                        if (yearAndRuntime.isNotEmpty()) {
                            Text(
                                yearAndRuntime,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    item.country?.takeIf { it.isNotBlank() }?.let { country ->
                        Text(
                            country,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Техметки (качество, «Режиссёрская версия») стояли в одном ряду с жанрами и
                // читались как ещё один жанр. Теперь они под постером - см. TechTagColumn ниже,
                // где заодно занимают пустое место, которое оставляла более низкая колонка постера.
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
                                genreDisplayName(genre),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)
        ) {
            val favoriteSource = remember { MutableInteractionSource() }
            FilterChip(
                selected = isFavorite,
                onClick = {
                    haptics.toggle(!isFavorite)
                    onToggleFavorite()
                },
                label = { Text(stringResource(R.string.details_favorite_short), maxLines = 1) },
                leadingIcon = {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                interactionSource = favoriteSource,
                modifier = Modifier.weight(1f).focusHighlight(favoriteSource)
            )
            val watchedSource = remember { MutableInteractionSource() }
            FilterChip(
                selected = isWatched,
                onClick = {
                    haptics.toggle(!isWatched)
                    onToggleWatched()
                },
                label = { Text(stringResource(R.string.details_watched_short), maxLines = 1) },
                leadingIcon = {
                    Icon(
                        if (isWatched) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                interactionSource = watchedSource,
                modifier = Modifier.weight(1f).focusHighlight(watchedSource)
            )
        }

        // Tagline and studio moved out of the narrow column next to the poster (where studio used
        // to sit) into one shared full-width block below everything - per user feedback, having
        // one crammed into that cramped column and the other as a bare line further down didn't
        // read as a deliberate part of the design. Grouped together in their own lightly-tinted
        // card here instead, both get the room to breathe a plain inline `Text` next to a poster
        // never had.
        // For a series the header has no trailer of its own (the item behind it is an episode), so
        // the first season's trailer stands in as the show's - that's the one a viewer means by
        // "the trailer" on a series card. Later seasons stay reachable from their own season rows.
        val headerTrailerItem = item.takeIf { it.trailerPath != null }
            ?: episodes
                .filter { it.hasSeasonWideTrailer() }
                .minByOrNull { it.seasonNumber ?: Int.MAX_VALUE }
        ActionButtonsRow(
            hasStartedWatching = hasStartedWatching,
            resumePositionMs = resumePositionMs,
            totalDurationMs = totalDurationMs,
            hasTrailer = headerTrailerItem != null,
            // "Смотреть"/"Продолжить" alone said nothing about WHICH episode a series card would
            // start - the episode label makes the button's actual effect visible before tapping.
            playEpisodeLabel = if (item.seriesStableId != null) {
                item.seasonNumber?.let { season ->
                    item.episodeNumber?.let { episode ->
                        stringResource(R.string.details_season_episode, season, episode)
                    }
                }
            } else {
                null
            },
            // A series download button in the header downloads this one representative episode,
            // which reads as "download the show". Each season row has its own download action,
            // which is the unambiguous one.
            showDownload = item.seriesStableId == null,
            download = download,
            itemTitle = displayTitle,
            onPlay = { onPlay(item.stableId) },
            onPlayTrailer = { headerTrailerItem?.let { onPlayTrailer(it.stableId) } },
            onStartDownload = onStartDownload,
            onRemoveDownload = onRemoveDownload,
            onDownloadError = onDownloadError
        )

        val tagline = item.tagline?.takeIf { it.isNotBlank() }
        val studio = item.studio?.takeIf { it.isNotBlank() }
        val mpaa = item.mpaa?.takeIf { it.isNotBlank() }
        val premiered = item.premiered?.takeIf { it.isNotBlank() }
        // Only ever non-null for a series item (show-level tvshow.nfo <status> - see
        // MediaItemEntity.status's own KDoc), so this doubles as the "is this a series" gate here
        // without needing a separate category check.
        val seriesStatus = item.statusLabel
        val collectionName = item.collectionName?.takeIf { it.isNotBlank() }
        tagline?.let {
            // Слоган шёл вплотную над «Описанием» одной строкой курсивом - было непонятно,
            // что это за фраза и почему она там. Теперь это явная цитата: подписана, отбита
            // вертикальной линией слева и отделена от описания воздухом.
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        stringResource(R.string.details_tagline_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        val plot = item.plot?.takeIf { it.isNotBlank() } ?: stringResource(R.string.details_no_description)
        var plotExpanded by remember(item.stableId) { mutableStateOf(false) }
        var plotHasOverflow by remember(item.stableId) { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
                .fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.details_description_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                plot,
                style = MaterialTheme.typography.bodyMedium.copy(
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph
                ),
                textAlign = TextAlign.Justify,
                maxLines = if (plotExpanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!plotExpanded) plotHasOverflow = result.hasVisualOverflow
                }
            )
            if (plotHasOverflow || plotExpanded) {
                TextButton(
                    onClick = { plotExpanded = !plotExpanded },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(if (plotExpanded) R.string.details_description_collapse else R.string.details_description_expand))
                }
            }
        }

        // Одна колонка «подпись — значение» вместо двух колонок по разным краям. Раньше левая
        // выравнивалась влево, правая вправо, между ними была широкая канава - пара
        // подпись→значение через неё не читалась, блок воспринимался как два несвязанных
        // списка. Слоган остаётся отдельным блоком во всю ширину: он длинный и курсивный,
        // в таблицу не ложится.
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            studio?.let {
                MetaRow(stringResource(R.string.details_studio_label)) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            premiered?.let {
                MetaRow(stringResource(R.string.details_premiered_label)) {
                    Text(formatPremieredDate(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
            mpaa?.let {
                MetaRow(stringResource(R.string.details_mpaa_label)) {
                    // Сырая строка из .nfo часто нечитаема ("US:PG-13 / US:Rated PG-13") - показываем
                    // бейдж 0+/6+/12+/16+/18+, когда узнали формат, иначе текст как есть.
                    val ageLabel = ageRatingLabel(it)
                    if (ageLabel != null) {
                        AgeRatingBadge(ageLabel)
                    } else {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            seriesStatus?.let {
                MetaRow(stringResource(R.string.details_series_status_label)) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            collectionName?.let {
                MetaRow(stringResource(R.string.details_collection_name_label)) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            audioTracks?.takeIf { it.isNotEmpty() }?.let { tracks ->
                MetaRow(stringResource(R.string.details_audio_tracks_label).trim()) {
                    Text(
                        tracks.joinToString("; "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Skipped for a series: subtitlePaths belongs to the one representative episode behind
            // this screen, so "Субтитры: Нет" sat among show-wide facts (studio, premiere, status)
            // while actually describing a single file. Per-episode subtitle state isn't something
            // this block can honestly summarize for a whole show.
            if (item.seriesStableId == null) {
            MetaRow(stringResource(R.string.details_subtitles_label).trim()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hasSubtitles = item.subtitlePaths.isNotEmpty()
                    Text(
                        stringResource(if (hasSubtitles) R.string.details_yes else R.string.details_no),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.hasForcedSubtitles) {
                        Text(
                            stringResource(R.string.details_forced_subtitles_suffix),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
            }
        }

        if (episodes.isNotEmpty()) {
            EpisodeList(episodes, downloads, item.plot, onPlay, onPlaySeasonTrailer, onDownloadSeason, onDownloadEpisode, onRemoveEpisodeDownload, onRemoveSeasonDownloads)
        }

        if (item.director.isNotEmpty()) {
            PersonRow(
                stringResource(R.string.details_director),
                item.director,
                clickablePersons,
                onOpenPerson,
                inlineLabel = item.director.size <= 2
            )
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
        // Последняя секция упиралась в жест-бар: карточки обрезались нижним краем экрана,
        // а дальше список уже не прокручивался.
        Spacer(
            Modifier
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
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

/** Play/Trailer/download row from the top of Details - extracted so it's previewable in isolation (see the @Preview functions right below DownloadButton) without needing a full MediaItemEntity/ViewModel. */
@Composable
private fun ActionButtonsRow(
    hasStartedWatching: Boolean,
    resumePositionMs: Long = 0L,
    totalDurationMs: Long = 0L,
    hasTrailer: Boolean,
    /** "S1E1" for a series, null for a film - appended to the play button so it names the episode it starts. */
    playEpisodeLabel: String? = null,
    showDownload: Boolean = true,
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
        com.illusion.app.ui.common.TvAwareButton(
            onClick = onPlay,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            // На TV во всю ширину экрана кнопка превращалась в полосу через весь экран.
            modifier = if (LocalUiMode.current == UiMode.TV) Modifier.widthIn(min = 240.dp, max = 360.dp) else Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            val playLabel = stringResource(
                if (hasStartedWatching) R.string.details_continue_watching else R.string.details_play
            )
            Text(
                playEpisodeLabel?.let { "$playLabel · $it" } ?: playLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (hasStartedWatching && totalDurationMs > 0 && resumePositionMs > 0) {
            val fraction = (resumePositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
            Column {
                LinearProgressIndicator(
                    progress = { fraction },
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
                Text(
                    stringResource(
                        R.string.details_resume_hint,
                        formatWatchClock(resumePositionMs),
                        (fraction * 100).toInt(),
                        formatWatchLeft(totalDurationMs - resumePositionMs)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
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
            if (showDownload) {
                DownloadButton(
                    download = download,
                    itemTitle = itemTitle,
                    onStart = onStartDownload,
                    onRemove = onRemoveDownload,
                    onError = onDownloadError,
                    modifier = if (hasTrailer) Modifier.weight(1f) else Modifier.widthIn(max = 240.dp)
                )
            }
        }
    }
}

/** Позиция остановки как часы: час показывается только когда он есть, чтобы у серии не было ведущего «0:». */
private fun formatWatchClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}


@Composable
private fun DownloadButton(
    download: DownloadEntity?,
    itemTitle: String,
    onStart: () -> Unit,
    onRemove: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRemoveConfirm by remember { mutableStateOf(false) }
    when (download?.status) {
        null -> {
            // Не сплошная заливка, как у «Смотреть»: одинаковый цвет плюс вся ширина строки
            // делали второстепенную загрузку заметнее главного действия. Тональная заливка
            // заодно уравнивает это состояние с остальными состояниями загрузки ниже - те уже тональные.
            OutlinedButton(
                onClick = onStart,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = modifier
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
                modifier = modifier
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
                modifier = modifier.focusHighlight(removeSource)
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
                modifier = modifier.focusHighlight(retrySource)
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

/**
 * Строка «подпись — значение» мета-блока. Обе части выровнены по левому краю своей колонки,
 * подпись фиксированной ширины - тогда весь блок читается как таблица, а не как два
 * независимых столбца, разогнанных по разным краям карточки.
 */
/** Свойство самого файла (качество, издание) под постером - с заливкой и значком, чтобы не теряться рядом с жанрами. */
@Composable
private fun TechTagChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color,
    content: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun MetaRow(label: String, value: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(104.dp).padding(top = 2.dp)
        )
        Box(modifier = Modifier.weight(1f)) { value() }
    }
}

@Composable
private fun PersonRow(
    label: String,
    names: List<String>,
    clickablePersons: Set<String>,
    onOpenPerson: (String) -> Unit,
    // У режиссёра почти всегда одно имя, и отдельный заголовок над единственным чипом
    // съедал целую секцию по высоте ради одного слова. В этом режиме подпись стоит слева
    // от самих чипов, а не над ними.
    inlineLabel: Boolean = false
) {
    if (inlineLabel) {
        Row(
            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).focusGroup()
            ) {
                names.forEach { name ->
                    val clickable = name in clickablePersons
                    PersonChip(name, clickable, onOpenPerson)
                }
            }
        }
        return
    }
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
                PersonChip(name, clickable, onOpenPerson)
            }
        }
    }
}

@Composable
private fun PersonChip(name: String, clickable: Boolean, onOpenPerson: (String) -> Unit) {
    if (clickable) {
        val interactionSource = remember { MutableInteractionSource() }
        AssistChip(
            onClick = { onOpenPerson(name) },
            label = { Text(name) },
            interactionSource = interactionSource,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            modifier = Modifier.focusHighlight(interactionSource)
        )
    } else {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** A trailing "(2001)" in a title - show folders conventionally carry the year already. */
private val TITLE_YEAR_PATTERN = Regex("""\(\s*\d{4}\s*\)""")

private fun formatRuntime(minutes: Int?): String? {
    val value = minutes?.takeIf { it > 0 } ?: return null
    return when {
        value < 60 -> "$value мин"
        value % 60 == 0 -> "${value / 60} ч"
        else -> "${value / 60} ч ${value % 60} мин"
    }
}

private fun russianPlural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

/** "6 сезонов · 168 эпизодов" - what a series card's meta line should say instead of one episode's runtime. */
private fun seasonsAndEpisodesLabel(episodes: List<MediaItemEntity>): String? {
    if (episodes.isEmpty()) return null
    val seasons = episodes.mapNotNull { it.seasonNumber }.distinct().size
    val episodeCount = episodes.size
    val seasonsPart = if (seasons > 0) {
        "$seasons ${russianPlural(seasons, "сезон", "сезона", "сезонов")}"
    } else {
        null
    }
    val episodesPart = "$episodeCount ${russianPlural(episodeCount, "эпизод", "эпизода", "эпизодов")}"
    return listOfNotNull(seasonsPart, episodesPart).joinToString(" · ")
}

/** "Название-S1-trailer.mp4" - the shape LibraryScanner assigns to every episode of one season. */
private val SEASON_TRAILER_FILE_PATTERN = Regex("""[-_. ]s\d{1,2}[-_. ]+trailer""", RegexOption.IGNORE_CASE)

/**
 * True when this item's trailer is one a whole season shares, rather than the item's own
 * "<episode>-trailer.mkv". Two shapes qualify: the per-season file above, and a bare "trailer.mkv"
 * sitting in the season's folder (which the scanner hands to every episode there).
 */
private fun MediaItemEntity.hasSeasonWideTrailer(): Boolean {
    val fileBase = trailerPath
        ?.substringAfterLast('\\')
        ?.substringBeforeLast('.')
        ?.lowercase()
        ?: return false
    if (SEASON_TRAILER_FILE_PATTERN.containsMatchIn(fileBase)) return true
    return fileBase == "trailer" ||
        (fileBase.startsWith("trailer") &&
            fileBase.substring("trailer".length).all { it.isDigit() || it == '-' || it == '_' })
}

@Composable
private fun EpisodeList(
    episodes: List<MediaItemEntity>,
    downloads: Map<String, DownloadEntity>,
    /** The show's own synopsis - episodes that merely inherit it don't repeat it in their row. */
    seriesPlot: String?,
    onPlay: (String) -> Unit,
    onPlaySeasonTrailer: (String) -> Unit,
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
            val seasonTrailerEpisode = seasonEpisodes.firstOrNull { it.hasSeasonWideTrailer() }
            // With several seasons all pointing at the same file, that file sits at the show root
            // and belongs to the show as a whole (the Details header already offers it) - showing
            // it again on every season row would just be the same trailer repeated.
            val seasonTrailerIsShared = seasonTrailerEpisode != null && bySeason.size > 1 &&
                bySeason.values.count { others ->
                    others.any { it.trailerPath == seasonTrailerEpisode.trailerPath }
                } > 1
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
                if (seasonTrailerEpisode != null && !seasonTrailerIsShared) {
                    // A bare Theaters glyph next to a season told nobody what it does (same
                    // feedback the header's trailer button got earlier) - it's a labelled chip now.
                    val seasonTrailerSource = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = { onPlaySeasonTrailer(seasonTrailerEpisode.stableId) },
                        interactionSource = seasonTrailerSource,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .heightIn(min = 32.dp)
                            .focusHighlight(seasonTrailerSource)
                    ) {
                        Icon(
                            Icons.Default.Theaters,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            stringResource(R.string.details_trailer),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(148.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            ThumbnailImage(episode.episodeThumbModel, contentDescription = null)
                        }
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            // The premiere date used to sit in the same row as the title, taking
                            // enough width that a title like "Мой первый день" wrapped one word per
                            // line. It's supporting information - it belongs on the meta line below
                            // together with the runtime, not competing with the title for width.
                            Text(
                                label.ifBlank { episode.title },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val episodeMeta = listOfNotNull(
                                episode.premiered?.takeIf { it.isNotBlank() }?.let { formatPremieredDate(it) },
                                formatRuntime(episode.runtimeMinutes)
                            ).joinToString(" · ")
                            if (episodeMeta.isNotEmpty()) {
                                Text(
                                    episodeMeta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            // Episodes without their own <plot> inherit the show's, so every row
                            // repeated the synopsis already shown at the top of this screen -
                            // skipped entirely in that case. What's left is capped at two lines:
                            // a full synopsis per row made a 33-episode season unreadable.
                            episode.plot
                                ?.takeIf { it.isNotBlank() && it != seriesPlot }
                                ?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
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
    // Был залит ярко-красным - единственное насыщенное пятно на всём экране, перетягивало
    // внимание с кнопки воспроизведения ради справочной мелочи.
    Text(
        label,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
