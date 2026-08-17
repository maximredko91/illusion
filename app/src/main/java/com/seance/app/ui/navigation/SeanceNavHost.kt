package com.seance.app.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.seance.app.R
import com.seance.app.SeanceApplication
import com.seance.app.domain.model.Category
import com.seance.app.domain.model.UiMode
import com.seance.app.ui.common.LocalNavAnimatedVisibilityScope
import com.seance.app.ui.common.LocalSharedTransitionScope
import com.seance.app.ui.common.LocalShimmerProgress
import com.seance.app.ui.common.LocalUiMode
import com.seance.app.ui.common.focusHighlight
import com.seance.app.ui.common.segmentTick
import com.seance.app.ui.addmedia.AddMediaScreen
import com.seance.app.ui.details.DetailsScreen
import com.seance.app.ui.downloads.DownloadsScreen
import com.seance.app.ui.favorites.FavoritesScreen
import com.seance.app.ui.history.HistoryScreen
import com.seance.app.ui.home.HomeScreen
import com.seance.app.ui.home.HomeViewModel
import com.seance.app.ui.library.LibraryScreen
import com.seance.app.ui.library.LibraryViewModel
import com.seance.app.ui.onboarding.OnboardingScreen
import com.seance.app.ui.person.PersonScreen
import com.seance.app.ui.player.PlayerScreen
import com.seance.app.ui.scan.ScanProgressScreen
import com.seance.app.ui.search.SearchScreen
import com.seance.app.ui.settings.CacheScreen
import com.seance.app.ui.settings.SettingsScreen
import com.seance.app.ui.settings.SettingsViewModel
import com.seance.app.ui.smbsource.AddSmbSourceScreen
import com.seance.app.ui.smbsource.EditSmbSourceScreen
import com.seance.app.work.WorkScheduler
import kotlinx.coroutines.flow.first

private data class BottomTab(
    val category: Category?,
    val labelRes: Int,
    val icon: ImageVector
)

// null represents the Home tab - Home isn't a Category, so this list can't be keyed on Category
// alone, and a nullable key is simpler here than inventing a wrapper sealed type for four items.
private val bottomTabs = listOf(
    BottomTab(null, R.string.nav_home, Icons.Default.Home),
    BottomTab(Category.MOVIES, R.string.nav_movies, Icons.Default.Movie),
    BottomTab(Category.TV_SHOWS, R.string.nav_tv_shows, Icons.Default.SmartDisplay),
    BottomTab(Category.CARTOONS, R.string.nav_cartoons, Icons.Default.Theaters)
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SeanceNavHost(
    app: SeanceApplication,
    modifier: Modifier = Modifier
) = SharedTransitionLayout(modifier = modifier) {
    // null (not chosen yet) reads as PHONE here - Splash routes a fresh install to Onboarding
    // before any real content renders, so this default is never actually shown to a user who
    // hasn't answered the onboarding prompt yet; it only matters for the brief pre-onboarding
    // frame and for pre-existing installs from before this feature (see SettingsRepository.uiMode).
    val uiMode by app.settingsRepository.uiMode.collectAsState(initial = UiMode.PHONE)
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    // Deliberately NOT unwrapped with `by` here - reading .value in this composable's own body
    // would make this whole scope (everything CompositionLocalProvider wraps, all the way down
    // to every screen) re-execute on every animation frame instead of just the leaf shimmer()
    // modifier that actually needs the live value. That storm of full-tree recomposition ~90-120
    // times/sec was confirmed with logcat timestamps during a tab switch. Passing the State object
    // through untouched keeps SeanceNavHost's own scope stable; only Modifier.shimmer()'s `.value`
    // read recomposes per frame.
    val shimmerProgressState = shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    CompositionLocalProvider(
        LocalSharedTransitionScope provides this,
        LocalUiMode provides (uiMode ?: UiMode.PHONE),
        LocalShimmerProgress provides shimmerProgressState
    ) {
        val navController = rememberNavController()
        // Destinations with no Scaffold of their own (Details, Person, ...) render straight onto
        // whatever is behind them and rely on LocalContentColor for their default (unspecified)
        // Text colors - previously an outer Scaffold (removed when tab-switching moved off
        // NavController) supplied both via its default containerColor. A plain
        // Modifier.background() only paints pixels, it doesn't set LocalContentColor, so swapping
        // to that instead of Scaffold silently turned every color-less Text on those screens black
        // (Compose's fallback) even though the background itself looked right. Surface sets both,
        // same as Scaffold did.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            SeanceNavGraph(app, navController, modifier = Modifier.fillMaxSize())
        }
    }
}

private const val NAV_TRANSITION_MS = 350

@Composable
private fun SeanceNavGraph(app: SeanceApplication, navController: NavHostController, modifier: Modifier = Modifier) {
        NavHost(
            navController = navController,
            startDestination = Destination.Splash,
            modifier = modifier,
            // Material "shared axis X": the incoming screen slides in from the edge being
            // revealed while the outgoing one drifts a little the other way and fades - this is
            // also what gets scrubbed live by the predictive-back gesture (NavHost drives these
            // same transitions frame-by-frame off the swipe progress), instead of the library's
            // default flat 700ms crossfade.
            enterTransition = {
                slideInHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { it / 3 } +
                    fadeIn(tween(NAV_TRANSITION_MS))
            },
            exitTransition = {
                slideOutHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { -it / 6 } +
                    fadeOut(tween(NAV_TRANSITION_MS))
            },
            popEnterTransition = {
                slideInHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { -it / 6 } +
                    fadeIn(tween(NAV_TRANSITION_MS))
            },
            popExitTransition = {
                slideOutHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { it / 3 } +
                    fadeOut(tween(NAV_TRANSITION_MS))
            }
        ) {
            composable<Destination.Splash> {
                LaunchedEffect(Unit) {
                    val hasSources = app.smbSourceRepository.observeSources().first().isNotEmpty()
                    if (hasSources) {
                        val hours = app.settingsRepository.rescanIntervalHours.first()
                        if (hours > 0) {
                            val requireCharging = app.settingsRepository.requireChargingForHeavyTasks.first()
                            WorkScheduler.schedulePeriodicScan(app, hours, requireCharging)
                        }
                    }
                    val target = if (hasSources) Destination.Tabs else Destination.Onboarding
                    navController.navigate(target) {
                        popUpTo(Destination.Splash) { inclusive = true }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            composable<Destination.Onboarding> {
                OnboardingScreen(
                    smbSourceRepository = app.smbSourceRepository,
                    settingsRepository = app.settingsRepository,
                    onFinished = { workId ->
                        navController.navigate(Destination.ScanProgress(workId)) {
                            popUpTo(Destination.Onboarding) { inclusive = true }
                        }
                    }
                )
            }

            composable<Destination.ScanProgress> { entry ->
                val route = entry.toRoute<Destination.ScanProgress>()
                ScanProgressScreen(
                    workId = route.workId,
                    onComplete = {
                        navController.navigate(Destination.Tabs) {
                            popUpTo(Destination.Tabs) { inclusive = true }
                        }
                    }
                )
            }

            composable<Destination.Tabs> {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    TabsHost(app, navController)
                }
            }
            composable<Destination.Downloads> {
                DownloadsScreen(
                    downloadRepository = app.downloadRepository,
                    libraryRepository = app.libraryRepository,
                    settingsRepository = app.settingsRepository,
                    onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Destination.Favorites> {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    FavoritesScreen(
                        libraryRepository = app.libraryRepository,
                        watchProgressRepository = app.watchProgressRepository,
                        onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable<Destination.History> {
                HistoryScreen(
                    libraryRepository = app.libraryRepository,
                    watchProgressRepository = app.watchProgressRepository,
                    onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Destination.Search> {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    SearchScreen(
                        libraryRepository = app.libraryRepository,
                        onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) },
                        onOpenSettings = { navController.navigate(Destination.Settings) },
                        onOpenFavorites = { navController.navigate(Destination.Favorites) },
                        onOpenHistory = { navController.navigate(Destination.History) },
                        onOpenDownloads = { navController.navigate(Destination.Downloads) }
                    )
                }
            }
            composable<Destination.Details> { entry ->
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    val details = entry.toRoute<Destination.Details>()
                    DetailsScreen(
                        stableId = details.stableId,
                        libraryRepository = app.libraryRepository,
                        watchProgressRepository = app.watchProgressRepository,
                        downloadRepository = app.downloadRepository,
                        audioTrackRepository = app.audioTrackRepository,
                        audioTrackProber = app.audioTrackProber,
                        onPlay = { stableId -> navController.navigate(Destination.Player(stableId)) },
                        onPlayTrailer = { stableId -> navController.navigate(Destination.Player(stableId, trailer = true)) },
                        onOpenPerson = { name -> navController.navigate(Destination.Person(name)) },
                        onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable<Destination.Person> { entry ->
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    val person = entry.toRoute<Destination.Person>()
                    PersonScreen(
                        name = person.name,
                        libraryRepository = app.libraryRepository,
                        onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) }
                    )
                }
            }
            composable<Destination.Player> { entry ->
                val player = entry.toRoute<Destination.Player>()
                PlayerScreen(
                    stableId = player.stableId,
                    isTrailer = player.trailer,
                    libraryRepository = app.libraryRepository,
                    watchProgressRepository = app.watchProgressRepository,
                    thumbnailRepository = app.thumbnailRepository,
                    settingsRepository = app.settingsRepository,
                    smbDataSourceFactory = app.smbDataSourceFactory,
                    downloadRepository = app.downloadRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Destination.Settings> {
                val context = LocalContext.current
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(app.smbSourceRepository, app.settingsRepository, app.thumbnailRepository, app.downloadRepository, app.backupManager, app.devAccessStore)
                )
                val sources by settingsViewModel.sources.collectAsState()
                val cacheSizeBytes by settingsViewModel.cacheSizeBytes.collectAsState()
                val downloadsSizeBytes by settingsViewModel.downloadsSizeBytes.collectAsState()
                val pendingImportSources by settingsViewModel.pendingImportSources.collectAsState()
                val backupMessage by settingsViewModel.backupMessage.collectAsState()
                SettingsScreen(
                    sources = sources,
                    requireChargingForHeavyTasks = settingsViewModel.requireChargingForHeavyTasks,
                    rescanIntervalHours = settingsViewModel.rescanIntervalHours,
                    seekDurationSeconds = settingsViewModel.seekDurationSeconds,
                    onSeekDurationChange = settingsViewModel::setSeekDurationSeconds,
                    cacheSizeBytes = cacheSizeBytes,
                    onRefreshCacheSize = { settingsViewModel.refreshCacheSize(context) },
                    onOpenCache = { navController.navigate(Destination.Cache) },
                    uiMode = settingsViewModel.uiMode,
                    onUiModeChange = { mode -> settingsViewModel.setUiMode(mode) },
                    onToggleChargingRequirement = { enabled ->
                        settingsViewModel.setRequireChargingForHeavyTasks(context, enabled)
                    },
                    onRescanIntervalChange = { hours -> settingsViewModel.setRescanIntervalHours(context, hours) },
                    onRescanNow = {
                        val workId = WorkScheduler.enqueueOneTimeScan(context)
                        navController.navigate(Destination.ScanProgress(workId.toString()))
                    },
                    downloadsFolderUri = settingsViewModel.downloadsFolderUri,
                    onPickDownloadsFolder = { uri -> settingsViewModel.setDownloadsFolderUri(context, uri) },
                    downloadsSizeBytes = downloadsSizeBytes,
                    onRefreshDownloadsSize = { settingsViewModel.refreshDownloadsSize() },
                    onClearDownloads = { settingsViewModel.clearAllDownloads() },
                    onExportBackup = { uri -> settingsViewModel.exportBackup(context, uri) },
                    onImportBackup = { uri -> settingsViewModel.importBackup(context, uri) },
                    pendingImportSources = pendingImportSources,
                    onConfirmImportSource = { password -> settingsViewModel.confirmImportSource(context, password) },
                    onSkipImportSource = { settingsViewModel.skipImportSource(context) },
                    backupMessage = backupMessage,
                    onDismissBackupMessage = { settingsViewModel.dismissBackupMessage() },
                    onAddSource = { navController.navigate(Destination.AddSmbSource) },
                    onEditSource = { source -> navController.navigate(Destination.EditSmbSource(source.id)) },
                    onDeleteSource = { source -> settingsViewModel.deleteSource(source) },
                    hasDevPassword = settingsViewModel::hasDevPassword,
                    onGenerateDevPassword = settingsViewModel::generateDevPassword,
                    onVerifyDevPassword = settingsViewModel::verifyDevPassword,
                    onDevAccessGranted = { navController.navigate(Destination.AddMedia) }
                )
            }
            composable<Destination.Cache> {
                val context = LocalContext.current
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(app.smbSourceRepository, app.settingsRepository, app.thumbnailRepository, app.downloadRepository, app.backupManager, app.devAccessStore)
                )
                val cacheSizeBytes by settingsViewModel.cacheSizeBytes.collectAsState()
                CacheScreen(
                    cacheSizeBytes = cacheSizeBytes,
                    onRefreshCacheSize = { settingsViewModel.refreshCacheSize(context) },
                    onClearCache = { settingsViewModel.clearCache(context) },
                    posterCachingEnabled = settingsViewModel.posterCachingEnabled,
                    onSetPosterCachingEnabled = { enabled -> settingsViewModel.setPosterCachingEnabled(context, enabled) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Destination.AddMedia> {
                val context = LocalContext.current
                AddMediaScreen(
                    sourceRepository = app.smbSourceRepository,
                    smbClient = app.smbClient,
                    tmdbClient = app.tmdbClient,
                    nfoWriter = app.nfoWriter,
                    onRescanNow = {
                        val workId = WorkScheduler.enqueueOneTimeScan(context)
                        navController.navigate(Destination.ScanProgress(workId.toString()))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Destination.AddSmbSource> {
                AddSmbSourceScreen(
                    smbSourceRepository = app.smbSourceRepository,
                    onSaved = { navController.popBackStack() }
                )
            }
            composable<Destination.EditSmbSource> { entry ->
                val route = entry.toRoute<Destination.EditSmbSource>()
                EditSmbSourceScreen(
                    sourceId = route.sourceId,
                    smbSourceRepository = app.smbSourceRepository,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
}

/**
 * Home + the three Library categories, all living in one NavBackStackEntry (Destination.Tabs).
 * Switching between them is a local Crossfade over [selectedCategory], not a NavController
 * navigation - see the KDoc on Destination.Tabs for the bug this sidesteps. Each category's
 * [androidx.compose.foundation.lazy.grid.LazyGridState] is hoisted here too, above the Crossfade,
 * so scroll position survives switching away and back without needing saveState/restoreState.
 */
@Composable
private fun TabsHost(app: SeanceApplication, navController: NavHostController) {
    val uiMode = LocalUiMode.current
    val haptics = LocalHapticFeedback.current

    var selectedCategory by rememberSaveable { mutableStateOf<Category?>(null) }

    val movieGridState = rememberLazyGridState()
    val tvGridState = rememberLazyGridState()
    val cartoonGridState = rememberLazyGridState()
    val cartoonSeriesGridState = rememberLazyGridState()
    fun gridStateFor(category: Category) = when (category) {
        Category.MOVIES -> movieGridState
        Category.TV_SHOWS -> tvGridState
        Category.CARTOONS -> cartoonGridState
        Category.CARTOON_SERIES -> cartoonSeriesGridState
    }

    fun isTabSelected(tabCategory: Category?): Boolean = if (tabCategory == Category.CARTOONS) {
        selectedCategory == Category.CARTOONS || selectedCategory == Category.CARTOON_SERIES
    } else {
        selectedCategory == tabCategory
    }

    fun selectTab(tabCategory: Category?) {
        if (isTabSelected(tabCategory)) return
        haptics.segmentTick()
        selectedCategory = tabCategory
    }

    val content: @Composable (PaddingValues) -> Unit = { innerPadding ->
        Crossfade(
            targetState = selectedCategory,
            animationSpec = tween(200),
            modifier = Modifier.padding(innerPadding),
            label = "tabs"
        ) { category ->
            if (category == null) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(app.libraryRepository, app.watchProgressRepository)
                )
                val continueWatching by homeViewModel.continueWatching.collectAsState()
                val recentlyAdded by homeViewModel.recentlyAdded.collectAsState()
                HomeScreen(
                    continueWatching = continueWatching,
                    recentlyAdded = recentlyAdded,
                    onOpenSettings = { navController.navigate(Destination.Settings) },
                    onOpenFavorites = { navController.navigate(Destination.Favorites) },
                    onOpenHistory = { navController.navigate(Destination.History) },
                    onOpenDownloads = { navController.navigate(Destination.Downloads) },
                    onOpenSearch = { navController.navigate(Destination.Search) },
                    onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) }
                )
            } else {
                val libraryViewModel: LibraryViewModel = viewModel(
                    key = category.name,
                    factory = LibraryViewModel.factory(app.libraryRepository, category)
                )
                val items by libraryViewModel.items.collectAsState()
                val isLoading by libraryViewModel.isLoading.collectAsState()
                val sortOrder by libraryViewModel.sortOrder.collectAsState()
                val genreFilter by libraryViewModel.genreFilter.collectAsState()
                val availableGenres by libraryViewModel.availableGenres.collectAsState()
                val yearFilter by libraryViewModel.yearFilter.collectAsState()
                val availableYears by libraryViewModel.availableYears.collectAsState()
                LibraryScreen(
                    category = category,
                    items = items,
                    isLoading = isLoading,
                    sortOrder = sortOrder,
                    onSortOrderChange = libraryViewModel::setSortOrder,
                    genreFilter = genreFilter,
                    onGenreFilterChange = libraryViewModel::setGenreFilter,
                    availableGenres = availableGenres,
                    yearFilter = yearFilter,
                    onYearFilterChange = libraryViewModel::setYearFilter,
                    availableYears = availableYears,
                    gridState = gridStateFor(category),
                    onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) },
                    onOpenSettings = { navController.navigate(Destination.Settings) },
                    onOpenFavorites = { navController.navigate(Destination.Favorites) },
                    onOpenHistory = { navController.navigate(Destination.History) },
                    onOpenDownloads = { navController.navigate(Destination.Downloads) },
                    onOpenSearch = { navController.navigate(Destination.Search) },
                    onCategoryChange = { newCategory -> selectedCategory = newCategory }
                )
            }
        }
    }

    // The TV Box target has no touchscreen, so the bottom NavigationBar used on phones is a D-pad
    // dead end: it lives in a separate Scaffold slot below the scrollable screen content, and
    // DPAD_DOWN from the last focusable item on screen never reaches it (verified on-device -
    // Compose's scrollable modifier absorbs the key event once the direction matches the scroll
    // axis, rather than handing it to directional focus search once there's nothing left to
    // scroll). A left-edge NavigationRail is reachable via DPAD_LEFT from any point in the
    // content column instead, which isn't the axis vertical carousels/lists scroll on.
    if (uiMode == UiMode.TV) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                bottomTabs.forEach { tab ->
                    val selected = isTabSelected(tab.category)
                    val interactionSource = remember { MutableInteractionSource() }
                    NavigationRailItem(
                        selected = selected,
                        onClick = { selectTab(tab.category) },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) },
                        interactionSource = interactionSource,
                        modifier = Modifier.focusHighlight(interactionSource)
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                content(PaddingValues())
            }
        }
    } else {
        Scaffold(
            // See the matching comment that used to live on the outer Scaffold in
            // SeanceNavHostContent - every screen in NavHost handles its own insets, this Scaffold
            // only needs to reserve space for its own bottom bar.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = isTabSelected(tab.category)
                        val interactionSource = remember { MutableInteractionSource() }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectTab(tab.category) },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) },
                            interactionSource = interactionSource,
                            modifier = Modifier.focusHighlight(interactionSource)
                        )
                    }
                }
            }
        ) { innerPadding ->
            content(innerPadding)
        }
    }
}
