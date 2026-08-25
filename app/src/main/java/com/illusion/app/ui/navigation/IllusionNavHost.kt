package com.illusion.app.ui.navigation

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.illusion.app.R
import com.illusion.app.IllusionApplication
import com.illusion.app.domain.model.Category
import com.illusion.app.domain.model.UiMode
import com.illusion.app.ui.common.LocalNavAnimatedVisibilityScope
import com.illusion.app.ui.common.LocalSharedTransitionScope
import com.illusion.app.ui.common.LocalShimmerProgress
import com.illusion.app.ui.common.LocalUiMode
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.segmentTick
import com.illusion.app.ui.addmedia.AddMediaScreen
import com.illusion.app.ui.details.DetailsScreen
import com.illusion.app.ui.downloads.DownloadsScreen
import com.illusion.app.ui.favorites.FavoritesScreen
import com.illusion.app.ui.history.HistoryScreen
import com.illusion.app.ui.home.HomeScreen
import com.illusion.app.ui.home.HomeViewModel
import com.illusion.app.ui.library.LibraryScreen
import com.illusion.app.ui.library.LibraryViewModel
import com.illusion.app.ui.onboarding.OnboardingScreen
import com.illusion.app.ui.person.PersonScreen
import com.illusion.app.ui.player.PlayerScreen
import com.illusion.app.ui.scan.ScanProgressScreen
import com.illusion.app.ui.search.SearchScreen
import com.illusion.app.ui.search.TagsScreen
import com.illusion.app.ui.settings.CacheScreen
import com.illusion.app.ui.settings.SettingsScreen
import com.illusion.app.ui.settings.SettingsViewModel
import com.illusion.app.ui.smbsource.AddSmbSourceScreen
import com.illusion.app.ui.smbsource.EditSmbSourceScreen
import com.illusion.app.work.WorkScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
fun IllusionNavHost(
    app: IllusionApplication,
    modifier: Modifier = Modifier
) = SharedTransitionLayout(modifier = modifier) {
    // null (not chosen yet) reads as PHONE here - Splash routes a fresh install to Onboarding
    // before any real content renders, so this default is never actually shown to a user who
    // hasn't answered the onboarding prompt yet; it only matters for the brief pre-onboarding
    // frame and for pre-existing installs from before this feature (see SettingsRepository.uiMode).
    val uiMode by app.settingsRepository.uiMode.collectAsState(initial = UiMode.PHONE)
    // Wraps the real LocalHapticFeedback so every existing call site (6 files, .toggle()/
    // .reject()/.segmentTick()/.tick()) respects the Settings switch without being touched
    // individually - overriding the same CompositionLocal androidx itself provides.
    val hapticsEnabled by app.settingsRepository.hapticsEnabled.collectAsState(initial = true)
    val realHapticFeedback = LocalHapticFeedback.current
    val gatedHapticFeedback = remember(hapticsEnabled, realHapticFeedback) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticsEnabled) realHapticFeedback.performHapticFeedback(hapticFeedbackType)
            }
        }
    }
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    // Deliberately NOT unwrapped with `by` here - reading .value in this composable's own body
    // would make this whole scope (everything CompositionLocalProvider wraps, all the way down
    // to every screen) re-execute on every animation frame instead of just the leaf shimmer()
    // modifier that actually needs the live value. That storm of full-tree recomposition ~90-120
    // times/sec was confirmed with logcat timestamps during a tab switch. Passing the State object
    // through untouched keeps IllusionNavHost's own scope stable; only Modifier.shimmer()'s `.value`
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
        LocalShimmerProgress provides shimmerProgressState,
        LocalHapticFeedback provides gatedHapticFeedback
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
            IllusionNavGraph(app, navController, modifier = Modifier.fillMaxSize())
        }
    }
}

private const val NAV_TRANSITION_MS = 350

// Details' own fade (see the enterTransition/exitTransition KDoc below for why it's a plain fade,
// not a slide) used to share NAV_TRANSITION_MS with the slide-based transitions elsewhere on this
// screen - per feedback, that felt too abrupt paired with the shared-element poster/fanart motion
// happening at the same time. Slower and gentler on its own, independent of the slide transitions.
private const val DETAILS_FADE_MS = 500

@Composable
private fun IllusionNavGraph(app: IllusionApplication, navController: NavHostController, modifier: Modifier = Modifier) {
        val predictiveBackEnabled by app.settingsRepository.predictiveBackEnabled.collectAsState(initial = true)
        // Lives here (IllusionNavGraph's own scope, never disposed by internal route changes -
        // unlike the composable<Destination.Tabs> block below, which Details pushes right over)
        // so it can tell a genuine tab switch (Movies -> Series) apart from merely returning from
        // Details to the SAME tab - both look identical from inside that disposed/recomposed
        // block (Details push tears the whole Tabs composition down, so its own LaunchedEffect(category)
        // couldn't distinguish "fresh tab" from "back from Details" - confirmed bug: sort order
        // silently reset to default every time you opened a card and came back).
        var lastVisitedLibraryCategory by remember { mutableStateOf<Category?>(null) }
        NavHost(
            navController = navController,
            startDestination = Destination.Splash,
            modifier = modifier,
            // Material "shared axis X": the incoming screen slides in from the edge being
            // revealed while the outgoing one drifts a little the other way and fades - this is
            // also what gets scrubbed live by the predictive-back gesture (NavHost drives these
            // same transitions frame-by-frame off the swipe progress), instead of the library's
            // default flat 700ms crossfade.
            //
            // Destination.Details is the exception: its poster/fanart already animates via a
            // sharedElement bounds transform (see PosterCard/DetailsScreen), which is its own
            // independent motion from wherever the poster card sat in the grid to where it lands
            // in Details. Also sliding the *whole* Details screen sideways on top of that stacked
            // two motions together - the poster visibly "flew in from offscreen" on top of its own
            // bounds animation, which read as jank rather than one clean transform. A plain fade
            // leaves the shared element's own motion as the only motion, matching how Material's
            // container-transform pattern is meant to pair with a fade, not a second slide.
            enterTransition = {
                if (targetState.destination.hasRoute<Destination.Details>()) {
                    fadeIn(tween(DETAILS_FADE_MS))
                } else {
                    slideInHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { it / 3 } +
                        fadeIn(tween(NAV_TRANSITION_MS))
                }
            },
            exitTransition = {
                if (targetState.destination.hasRoute<Destination.Details>()) {
                    fadeOut(tween(DETAILS_FADE_MS))
                } else {
                    slideOutHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { -it / 6 } +
                        fadeOut(tween(NAV_TRANSITION_MS))
                }
            },
            popEnterTransition = {
                if (!predictiveBackEnabled) {
                    EnterTransition.None
                } else if (initialState.destination.hasRoute<Destination.Details>()) {
                    // Popping back FROM Details (initialState, the screen being left) - the
                    // returning screen should fade in too, matching the shared element animating
                    // back down onto its poster card rather than sliding underneath that motion.
                    fadeIn(tween(DETAILS_FADE_MS))
                } else {
                    // Every other pop (Settings/Favorites/Search/... back to Tabs, or any nested
                    // screen back to its parent) uses the same fade+scale-in Details already did -
                    // matches Android's own predictive-back convention (the destination is already
                    // sitting slightly behind/smaller and settles to full size as it's revealed)
                    // instead of a slide-in-from-the-edge, which used to only read correctly for a
                    // full system-driven swipe and looked like a plain re-entrance for a tap-back.
                    fadeIn(tween(NAV_TRANSITION_MS)) +
                        scaleIn(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing), initialScale = 0.95f)
                }
            },
            popExitTransition = {
                if (!predictiveBackEnabled) {
                    ExitTransition.None
                } else if (initialState.destination.hasRoute<Destination.Details>()) {
                    // A plain fadeOut alone cross-dissolves in place - held partway through a
                    // predictive-back swipe, Details' poster/fanart and the Tabs grid underneath
                    // sit at the same overlapping alpha, reading as the destination "bleeding
                    // through" rather than Details visibly leaving. Pairing the fade with a slight
                    // scale-down (Material's own predictive-back convention) makes Details read as
                    // shrinking away even when the gesture is paused mid-swipe, not just at 60fps.
                    fadeOut(tween(DETAILS_FADE_MS)) + scaleOut(tween(DETAILS_FADE_MS), targetScale = 0.92f)
                } else {
                    // Same shrink-away treatment as Details, generalized - see popEnterTransition's
                    // comment above for why this replaced the old slide-out.
                    fadeOut(tween(NAV_TRANSITION_MS)) +
                        scaleOut(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing), targetScale = 0.92f)
                }
            }
        ) {
            composable<Destination.Splash> {
                LaunchedEffect(Unit) {
                    // A manual "rescan now" still running (or not yet started) reattaches its own
                    // ScanProgress screen here instead of falling through to the tabs - the work
                    // itself survives a killed process fine (WorkManager, its own foreground
                    // service), but the *screen* showing its progress was only ever reachable via
                    // this nav graph's own transient back stack, which MIUI backgrounding this app
                    // long enough to kill the process (a real, reported occurrence) throws away -
                    // the user came back to what looked like the scan silently having been dropped.
                    val runningScanWorkId = WorkScheduler.runningOneTimeScanWorkId(app)
                    if (runningScanWorkId != null) {
                        navController.navigate(Destination.ScanProgress(runningScanWorkId.toString())) {
                            popUpTo(Destination.Splash) { inclusive = true }
                        }
                        return@LaunchedEffect
                    }
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
                        navController.navigate(Destination.ScanProgress(workId, allowDismiss = false)) {
                            popUpTo(Destination.Onboarding) { inclusive = true }
                        }
                    }
                )
            }

            composable<Destination.ScanProgress> { entry ->
                val route = entry.toRoute<Destination.ScanProgress>()
                ScanProgressScreen(
                    workId = route.workId,
                    allowDismiss = route.allowDismiss,
                    onComplete = {
                        navController.navigate(Destination.Tabs) {
                            popUpTo(Destination.Tabs) { inclusive = true }
                        }
                    },
                    onDismiss = {
                        navController.navigate(Destination.Tabs) {
                            popUpTo(Destination.Tabs) { inclusive = true }
                        }
                    }
                )
            }

            composable<Destination.Tabs> {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    TabsHost(
                        app,
                        navController,
                        lastVisitedLibraryCategory = lastVisitedLibraryCategory,
                        onLibraryCategoryVisited = { lastVisitedLibraryCategory = it }
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
            composable<Destination.Search> { entry ->
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    val search = entry.toRoute<Destination.Search>()
                    SearchScreen(
                        libraryRepository = app.libraryRepository,
                        settingsRepository = app.settingsRepository,
                        initialQuery = search.initialQuery,
                        onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) },
                        onOpenSettings = { navController.navigate(Destination.Settings) },
                        onOpenFavorites = { navController.navigate(Destination.Favorites) },
                        onOpenHistory = { navController.navigate(Destination.History) },
                        onOpenDownloads = { navController.navigate(Destination.Downloads) },
                        onOpenTags = { navController.navigate(Destination.Tags) }
                    )
                }
            }
            composable<Destination.Tags> {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    TagsScreen(
                        libraryRepository = app.libraryRepository,
                        translationRepository = app.tagTranslationRepository,
                        onSelectTag = { tag ->
                            navController.navigate(Destination.Search(tag)) {
                                popUpTo(Destination.Search()) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() }
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
                        onPlay = { stableId ->
                            navController.navigate(Destination.Player(stableId)) { launchSingleTop = true }
                        },
                        onPlayTrailer = { stableId ->
                            navController.navigate(Destination.Player(stableId, trailer = true)) { launchSingleTop = true }
                        },
                        onOpenPerson = { name -> navController.navigate(Destination.Person(name)) },
                        onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) },
                        onBack = { navController.popBackStack() },
                        // Drilling through several Similar/series hops grows the back stack one
                        // card at a time (by design - a normal "back" from a deep card should land
                        // on the card the user actually came from, not skip straight home). This is
                        // the escape hatch for "I've gone deep and just want out" without breaking
                        // that per-card back behavior: clears everything above Tabs in one shot.
                        onGoHome = {
                            navController.navigate(Destination.Tabs) {
                                popUpTo<Destination.Tabs> { inclusive = false }
                                launchSingleTop = true
                            }
                        }
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
                    smbSourceRepository = app.smbSourceRepository,
                    credentialStore = app.credentialStore,
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Destination.Settings> {
                val context = LocalContext.current
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(app.smbSourceRepository, app.settingsRepository, app.thumbnailRepository, app.downloadRepository, app.backupManager, app.devAccessStore, app.libraryRepository, app.watchProgressRepository, app.tagTranslationRepository)
                )
                val sources by settingsViewModel.sources.collectAsState()
                val cacheSizeBytes by settingsViewModel.cacheSizeBytes.collectAsState()
                val downloadsSizeBytes by settingsViewModel.downloadsSizeBytes.collectAsState()
                val recoveredDownloadsCount by settingsViewModel.recoveredDownloadsCount.collectAsState()
                val pendingImportSources by settingsViewModel.pendingImportSources.collectAsState()
                val backupMessage by settingsViewModel.backupMessage.collectAsState()
                val isScanRunning by remember(context) { WorkScheduler.isOneTimeScanRunning(context) }.collectAsState(initial = false)
                val scanCoroutineScope = rememberCoroutineScope()
                val deeplUpgradeState by settingsViewModel.deeplUpgradeState.collectAsState()
                // Explicitly scoped to the Activity (not this NavBackStackEntry) so it resolves to
                // the exact same instance MainActivity's top-level UpdatePrompt observes - the
                // manual "Проверить обновления" button here needs to trigger the same dialog that
                // lives above the whole NavHost, not a second independent ViewModel/dialog nobody
                // ever sees rendered.
                val updateViewModel: com.illusion.app.ui.update.UpdateViewModel = viewModel(
                    viewModelStoreOwner = context as androidx.activity.ComponentActivity,
                    factory = com.illusion.app.ui.update.UpdateViewModel.factory(app, app.updateChecker, app.settingsRepository)
                )
                val upToDateMessage by updateViewModel.upToDateMessage.collectAsState()
                SettingsScreen(
                    sources = sources,
                    deeplApiKey = settingsViewModel.deeplApiKey,
                    onDeeplApiKeyChange = settingsViewModel::setDeeplApiKey,
                    deeplUpgradeState = deeplUpgradeState,
                    onUpgradeTagsToDeepL = settingsViewModel::upgradeTagsToDeepL,
                    onCancelTagsUpgrade = settingsViewModel::cancelTagsUpgrade,
                    onDismissDeeplUpgradeState = settingsViewModel::dismissDeeplUpgradeState,
                    requireChargingForHeavyTasks = settingsViewModel.requireChargingForHeavyTasks,
                    rescanIntervalHours = settingsViewModel.rescanIntervalHours,
                    playerMode = settingsViewModel.playerMode,
                    onPlayerModeChange = settingsViewModel::setPlayerMode,
                    externalPlayerPackage = settingsViewModel.externalPlayerPackage,
                    onExternalPlayerPackageChange = settingsViewModel::setExternalPlayerPackage,
                    cacheSizeBytes = cacheSizeBytes,
                    onRefreshCacheSize = { settingsViewModel.refreshCacheSize(context) },
                    onOpenCache = { navController.navigate(Destination.Cache) },
                    uiMode = settingsViewModel.uiMode,
                    onUiModeChange = { mode -> settingsViewModel.setUiMode(mode) },
                    defaultSortOrder = settingsViewModel.defaultSortOrder,
                    onDefaultSortOrderChange = settingsViewModel::setDefaultSortOrder,
                    hapticsEnabled = settingsViewModel.hapticsEnabled,
                    onHapticsEnabledChange = settingsViewModel::setHapticsEnabled,
                    predictiveBackEnabled = settingsViewModel.predictiveBackEnabled,
                    onPredictiveBackEnabledChange = settingsViewModel::setPredictiveBackEnabled,
                    accentColor = settingsViewModel.accentColor,
                    onAccentColorChange = settingsViewModel::setAccentColor,
                    themeMode = settingsViewModel.themeMode,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    onToggleChargingRequirement = { enabled ->
                        settingsViewModel.setRequireChargingForHeavyTasks(context, enabled)
                    },
                    onRescanIntervalChange = { hours -> settingsViewModel.setRescanIntervalHours(context, hours) },
                    onRescanNow = {
                        val workId = WorkScheduler.enqueueOneTimeScan(context)
                        navController.navigate(Destination.ScanProgress(workId.toString()))
                    },
                    onRescanForceNow = {
                        val workId = WorkScheduler.enqueueOneTimeScan(context, force = true)
                        navController.navigate(Destination.ScanProgress(workId.toString()))
                    },
                    isScanRunning = isScanRunning,
                    onOpenRunningScan = {
                        scanCoroutineScope.launch {
                            val workId = WorkScheduler.runningOneTimeScanWorkId(context)
                            if (workId != null) {
                                navController.navigate(Destination.ScanProgress(workId.toString()))
                            }
                        }
                    },
                    downloadsFolderUri = settingsViewModel.downloadsFolderUri,
                    onPickDownloadsFolder = { uri -> settingsViewModel.setDownloadsFolderUri(context, uri) },
                    downloadsSizeBytes = downloadsSizeBytes,
                    onRefreshDownloadsSize = { settingsViewModel.refreshDownloadsSize() },
                    onClearDownloads = { settingsViewModel.clearAllDownloads() },
                    onRecoverDownloads = { uri -> settingsViewModel.recoverDownloads(uri) },
                    recoveredDownloadsCount = recoveredDownloadsCount,
                    onDismissRecoveredDownloadsMessage = { settingsViewModel.dismissRecoveredDownloadsMessage() },
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
                    onSourceEnabledChange = { source, enabled -> settingsViewModel.setSourceEnabled(source, enabled) },
                    onResetToDefaults = { settingsViewModel.resetToDefaults() },
                    onFactoryReset = { settingsViewModel.factoryReset(context) },
                    hasDevPassword = settingsViewModel::hasDevPassword,
                    onGenerateDevPassword = settingsViewModel::generateDevPassword,
                    onVerifyDevPassword = settingsViewModel::verifyDevPassword,
                    isDevAccessRemembered = settingsViewModel::isDevAccessRemembered,
                    onRememberDevAccess = settingsViewModel::rememberDevAccess,
                    onForgetDevAccess = settingsViewModel::forgetDevAccess,
                    onDevAccessGranted = { navController.navigate(Destination.AddMedia) },
                    onCheckForUpdates = { updateViewModel.checkForUpdate(force = true) },
                    upToDateMessage = upToDateMessage,
                    onDismissUpToDateMessage = { updateViewModel.dismissUpToDateMessage() },
                    updateCheckIntervalHours = updateViewModel.updateCheckIntervalHours,
                    onUpdateCheckIntervalChange = { hours -> updateViewModel.setUpdateCheckIntervalHours(hours) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Destination.Cache> {
                val context = LocalContext.current
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(app.smbSourceRepository, app.settingsRepository, app.thumbnailRepository, app.downloadRepository, app.backupManager, app.devAccessStore, app.libraryRepository, app.watchProgressRepository, app.tagTranslationRepository)
                )
                val cacheSizeBytes by settingsViewModel.cacheSizeBytes.collectAsState()
                val posterCacheSizeBytes by settingsViewModel.posterCacheSizeBytes.collectAsState()
                val fanartCacheSizeBytes by settingsViewModel.fanartCacheSizeBytes.collectAsState()
                CacheScreen(
                    cacheSizeBytes = cacheSizeBytes,
                    onRefreshCacheSize = { settingsViewModel.refreshCacheSize(context) },
                    onClearCache = { settingsViewModel.clearCache(context) },
                    posterCacheSizeBytes = posterCacheSizeBytes,
                    onClearPosterCache = { settingsViewModel.clearPosterCache(context) },
                    fanartCacheSizeBytes = fanartCacheSizeBytes,
                    onClearFanartCache = { settingsViewModel.clearFanartCache(context) },
                    imageCacheLimitMb = settingsViewModel.imageCacheLimitMb,
                    onSetImageCacheLimitMb = settingsViewModel::setImageCacheLimitMb,
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
                    devAccessStore = app.devAccessStore,
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

        // Setting popEnterTransition/popExitTransition above to EnterTransition.None/
        // ExitTransition.None when predictiveBackEnabled is off does NOT stop the OS from doing a
        // live predictive-back swipe preview - it only removes our animation's alpha/scale, so
        // under a *held*, seekable gesture the destination screen still gets composed at once at
        // whatever partial progress and just snaps to fully visible with no fade to mask it,
        // reading as an even worse "bleed-through" than with the animation on. The only real way to
        // suppress the system preview is to make sure the ACTIVE back callback isn't
        // predictive-aware at all: androidx.activity.compose.BackHandler registers a plain
        // (non-animated) OnBackPressedCallback, and per the OnBackInvokedCallback contract, if
        // that's the topmost enabled callback the system skips the live-scrub preview entirely and
        // just waits for gesture completion. Composed after NavHost (and thus registered after, so
        // higher LIFO priority than NavHost's own internal predictive-back callback) - but gated on
        // canPop so it doesn't shadow TabsHost's own always-on exit-confirmation BackHandler when
        // sitting on the root Tabs destination with nothing left to pop.
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val canPop = navController.previousBackStackEntry != null
        BackHandler(enabled = !predictiveBackEnabled && canPop) {
            navController.popBackStack()
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
private fun TabsHost(
    app: IllusionApplication,
    navController: NavHostController,
    lastVisitedLibraryCategory: Category?,
    onLibraryCategoryVisited: (Category) -> Unit
) {
    val uiMode = LocalUiMode.current
    val haptics = LocalHapticFeedback.current

    // Destination.Tabs is the app's root screen (Splash pops itself off the back stack on
    // arrival) - a back gesture/press here would otherwise exit the Activity outright with no
    // confirmation, which is easy to trigger by accident with Android's edge swipe-back gesture.
    // A snackbar + a second back press within the window below is the same pattern most apps use
    // instead of a click-through confirm dialog, which would be far more intrusive for something
    // this frequent.
    val exitSnackbarHostState = remember { SnackbarHostState() }
    val backScope = rememberCoroutineScope()
    var awaitingExitConfirmation by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? android.app.Activity
    BackHandler(enabled = true) {
        if (awaitingExitConfirmation) {
            activity?.finish()
        } else {
            awaitingExitConfirmation = true
            backScope.launch { exitSnackbarHostState.showSnackbar(app.getString(R.string.exit_confirm_message)) }
            backScope.launch {
                delay(2000)
                awaitingExitConfirmation = false
            }
        }
    }

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

    val tabContent: @Composable (Category?) -> Unit = { category ->
        if (category == null) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(app.libraryRepository, app.watchProgressRepository)
            )
            val continueWatching by homeViewModel.continueWatching.collectAsState()
            val randomPicks by homeViewModel.randomPicks.collectAsState()
            HomeScreen(
                continueWatching = continueWatching,
                randomPicks = randomPicks,
                onRefreshRandomPicks = homeViewModel::refreshRandomPicks,
                onOpenSettings = { navController.navigate(Destination.Settings) },
                onOpenFavorites = { navController.navigate(Destination.Favorites) },
                onOpenHistory = { navController.navigate(Destination.History) },
                onOpenDownloads = { navController.navigate(Destination.Downloads) },
                onOpenSearch = { navController.navigate(Destination.Search()) },
                onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) }
            )
        } else {
            val libraryViewModel: LibraryViewModel = viewModel(
                key = category.name,
                factory = LibraryViewModel.factory(app.libraryRepository, app.settingsRepository, category)
            )
            // This branch is only composed while `category` is the active tab (Crossfade disposes
            // it on switch, recomposing fresh on return) - LaunchedEffect(category) firing on every
            // fresh entry into composition was meant to be "reset sort/filters each time this tab
            // is navigated to" (per feedback), but that fresh-entry event ALSO fires when merely
            // returning from Details (which disposes this whole branch too, same as a real tab
            // switch does) - confirmed bug: sort order silently reset every time a card was opened
            // and backed out of. Guarding on lastVisitedLibraryCategory (hoisted above Details'
            // own disposal boundary, in IllusionNavGraph) restricts the reset to when `category`
            // actually differs from the last tab shown - a genuine switch, not a Details round trip.
            LaunchedEffect(category) {
                if (lastVisitedLibraryCategory != category) {
                    libraryViewModel.resetFilters()
                    onLibraryCategoryVisited(category)
                }
            }
            val items by libraryViewModel.items.collectAsState()
            val isLoading by libraryViewModel.isLoading.collectAsState()
            val sortOrder by libraryViewModel.sortOrder.collectAsState()
            val sortAscending by libraryViewModel.sortAscending.collectAsState()
            val genreFilter by libraryViewModel.genreFilter.collectAsState()
            val availableGenres by libraryViewModel.availableGenres.collectAsState()
            val yearFilter by libraryViewModel.yearFilter.collectAsState()
            val availableYears by libraryViewModel.availableYears.collectAsState()
            val countryFilter by libraryViewModel.countryFilter.collectAsState()
            val availableCountries by libraryViewModel.availableCountries.collectAsState()
            LibraryScreen(
                category = category,
                items = items,
                isLoading = isLoading,
                sortOrder = sortOrder,
                onSortOrderChange = libraryViewModel::setSortOrder,
                sortAscending = sortAscending,
                onSortAscendingChange = libraryViewModel::setSortAscending,
                genreFilter = genreFilter,
                onGenreFilterChange = libraryViewModel::setGenreFilter,
                availableGenres = availableGenres,
                yearFilter = yearFilter,
                onYearFilterChange = libraryViewModel::setYearFilter,
                availableYears = availableYears,
                countryFilter = countryFilter,
                onCountryFilterChange = libraryViewModel::setCountryFilter,
                availableCountries = availableCountries,
                gridState = gridStateFor(category),
                onOpenItem = { stableId -> navController.navigate(Destination.Details(stableId)) },
                onOpenSettings = { navController.navigate(Destination.Settings) },
                onOpenFavorites = { navController.navigate(Destination.Favorites) },
                onOpenHistory = { navController.navigate(Destination.History) },
                onOpenDownloads = { navController.navigate(Destination.Downloads) },
                onOpenSearch = { navController.navigate(Destination.Search()) },
                onCategoryChange = { newCategory -> selectedCategory = newCategory }
            )
        }
    }

    val content: @Composable (PaddingValues) -> Unit = { innerPadding ->
        Crossfade(
            targetState = selectedCategory,
            animationSpec = tween(200),
            modifier = Modifier.padding(innerPadding),
            label = "tabs"
        ) { category -> tabContent(category) }
    }

    val railItems: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
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

    val isPhoneLandscape = uiMode != UiMode.TV &&
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Rotating a phone between the two landscape orientations physically swaps which edge the
    // front camera's display cutout sits on - docking the rail to a fixed side (or guessing from
    // Surface.rotation, tried first and wrong on this device) put it right on top of the cutout
    // for one of the two rotations. Read the real cutout inset instead (same source of truth
    // DetailsScreen already uses to mirror its own safe-area padding) and dock the rail to
    // whichever side does NOT have it.
    val cutoutInsets = WindowInsets.displayCutout
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val railOnLeft = cutoutInsets.getLeft(density, layoutDirection) <= cutoutInsets.getRight(density, layoutDirection)

    // The TV Box target has no touchscreen, so the bottom NavigationBar used on phones in
    // portrait is a D-pad dead end: it lives in a separate Scaffold slot below the scrollable
    // screen content, and DPAD_DOWN from the last focusable item on screen never reaches it
    // (verified on-device - Compose's scrollable modifier absorbs the key event once the
    // direction matches the scroll axis, rather than handing it to directional focus search once
    // there's nothing left to scroll). A left-edge NavigationRail is reachable via DPAD_LEFT from
    // any point in the content column instead, which isn't the axis vertical carousels/lists
    // scroll on. Phone landscape uses the same side-rail layout (not because of D-pad, but because
    // a bottom bar wastes too much of the little vertical room landscape already has) - side
    // instead of bottom also matches the phone's own wider-than-tall shape in that orientation.
    if (uiMode == UiMode.TV || isPhoneLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (railOnLeft) {
                NavigationRail(content = railItems)
            }
            Box(modifier = Modifier.weight(1f)) {
                content(PaddingValues())
            }
            if (!railOnLeft) {
                NavigationRail(content = railItems)
            }
        }
    } else {
        Scaffold(
            // See the matching comment that used to live on the outer Scaffold in
            // IllusionNavHostContent - every screen in NavHost handles its own insets, this Scaffold
            // only needs to reserve space for its own bottom bar.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(exitSnackbarHostState) },
            bottomBar = {
                // Was rememberLatchedNavigationBarsInsets() (same monotonic-max-of-real-and-
                // ambient-inset workaround as the status bar fix) - on this device it latched onto
                // an inset taller than the real gesture-nav bar and never came back down (the latch
                // only ever grows), leaving a permanent dark gap above the visible bar that the
                // user could see instantly disappear/reappear as the ambient and real readings
                // briefly disagreed during a heavy relayout (e.g. a Library sort switch). The
                // status-bar bleed-through bug this pattern was built for doesn't apply here -
                // plain default insets render correctly.
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
