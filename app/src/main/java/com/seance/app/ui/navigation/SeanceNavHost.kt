package com.seance.app.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.seance.app.R
import com.seance.app.SeanceApplication
import com.seance.app.domain.model.Category
import com.seance.app.ui.common.LocalNavAnimatedVisibilityScope
import com.seance.app.ui.common.LocalSharedTransitionScope
import com.seance.app.ui.common.segmentTick
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
    val destination: Destination,
    val labelRes: Int,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Destination.Home, R.string.nav_home, Icons.Default.Home),
    BottomTab(Destination.Library(Category.MOVIES), R.string.nav_movies, Icons.Default.Movie),
    BottomTab(Destination.Library(Category.TV_SHOWS), R.string.nav_tv_shows, Icons.Default.SmartDisplay),
    BottomTab(Destination.Library(Category.CARTOONS), R.string.nav_cartoons, Icons.Default.Theaters),
    BottomTab(Destination.Search, R.string.nav_search, Icons.Default.Search)
)

private val bottomBarRoutes = setOf(
    Destination.Home::class,
    Destination.Library::class,
    Destination.Search::class
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SeanceNavHost(
    app: SeanceApplication,
    modifier: Modifier = Modifier
) = SharedTransitionLayout(modifier = modifier) {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        SeanceNavHostContent(app)
    }
}

@Composable
private fun SeanceNavHostContent(app: SeanceApplication) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = backStackEntry?.destination?.hierarchy?.any { dest ->
        bottomBarRoutes.any { dest.hasRoute(it) }
    } == true

    val haptics = LocalHapticFeedback.current
    Scaffold(
        // This outer Scaffold has no topBar, so by default it would take responsibility for
        // system-bar/cutout insets itself (Scaffold's rule: it reserves safeDrawing insets only
        // when topBar/bottomBar aren't present) and apply them to innerPadding/NavHost - but every
        // screen inside NavHost already handles its own insets (its own Scaffold + top bar, or,
        // for the fullscreen player, deliberately no padding at all so video can draw under the
        // cutout). Without this override, the player's content area was measured 150dp narrower
        // than the screen in landscape - not a system overlay blocking it, just this Scaffold
        // reserving cutout width that nothing here actually needed reserved.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentLibraryCategory = backStackEntry
                        ?.takeIf { it.destination.hasRoute<Destination.Library>() }
                        ?.let { runCatching { it.toRoute<Destination.Library>() }.getOrNull() }
                        ?.category
                    bottomTabs.forEach { tab ->
                        val tabDestination = tab.destination
                        val selected = if (tabDestination is Destination.Library) {
                            val matchesAnimationTab = tabDestination.category == Category.CARTOONS &&
                                (currentLibraryCategory == Category.CARTOONS || currentLibraryCategory == Category.CARTOON_SERIES)
                            currentLibraryCategory == tabDestination.category || matchesAnimationTab
                        } else {
                            backStackEntry?.destination?.hierarchy?.any {
                                it.hasRoute(tabDestination::class)
                            } == true
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) haptics.segmentTick()
                                navController.navigate(tab.destination) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Splash,
            modifier = Modifier.padding(innerPadding)
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
                    val target = if (hasSources) Destination.Home else Destination.Onboarding
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
                        navController.navigate(Destination.Home) {
                            popUpTo(Destination.Home) { inclusive = true }
                        }
                    }
                )
            }

            composable<Destination.Home> {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
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
                        onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) }
                    )
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
            composable<Destination.Library> { entry ->
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    val library = entry.toRoute<Destination.Library>()
                    val libraryViewModel: LibraryViewModel = viewModel(
                        key = library.category.name,
                        factory = LibraryViewModel.factory(app.libraryRepository, library.category)
                    )
                    val items by libraryViewModel.items.collectAsState()
                    val isLoading by libraryViewModel.isLoading.collectAsState()
                    val sortOrder by libraryViewModel.sortOrder.collectAsState()
                    val genreFilter by libraryViewModel.genreFilter.collectAsState()
                    val availableGenres by libraryViewModel.availableGenres.collectAsState()
                    val yearFilter by libraryViewModel.yearFilter.collectAsState()
                    val availableYears by libraryViewModel.availableYears.collectAsState()
                    LibraryScreen(
                        category = library.category,
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
                        onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) },
                        onOpenSettings = { navController.navigate(Destination.Settings) },
                        onOpenFavorites = { navController.navigate(Destination.Favorites) },
                        onOpenHistory = { navController.navigate(Destination.History) },
                        onOpenDownloads = { navController.navigate(Destination.Downloads) },
                        onCategoryChange = { newCategory ->
                            navController.navigate(Destination.Library(newCategory)) {
                                popUpTo(Destination.Library(library.category)) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
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
                    factory = SettingsViewModel.factory(app.smbSourceRepository, app.settingsRepository, app.thumbnailRepository, app.downloadRepository, app.backupManager)
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
                    onDeleteSource = { source -> settingsViewModel.deleteSource(source) }
                )
            }
            composable<Destination.Cache> {
                val context = LocalContext.current
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(app.smbSourceRepository, app.settingsRepository, app.thumbnailRepository, app.downloadRepository, app.backupManager)
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
}
