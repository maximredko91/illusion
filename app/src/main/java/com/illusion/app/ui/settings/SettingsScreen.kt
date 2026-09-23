package com.illusion.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Backup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.domain.model.SortOrder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.domain.model.UiMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ListItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.expandVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.data.backup.BackupSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.data.download.DownloadStorage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.data.local.entity.SmbSourceEntity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.TvAwareButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.TvAwareOutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.focusHighlight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.reject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    /** Открытая категория, или null - список категорий. Приходит из маршрута, а не из состояния экрана. */
    category: String?,
    onOpenCategory: (String) -> Unit,
    sources: List<SmbSourceEntity>,
    sourcesMissingPassword: Set<Long> = emptySet(),
    playerMode: Flow<com.illusion.app.domain.model.PlayerMode>,
    onPlayerModeChange: (com.illusion.app.domain.model.PlayerMode) -> Unit,
    externalPlayerPackage: Flow<String?>,
    onExternalPlayerPackageChange: (String?) -> Unit,
    playerBufferSize: Flow<com.illusion.app.domain.model.PlayerBufferSize>,
    onPlayerBufferSizeChange: (com.illusion.app.domain.model.PlayerBufferSize) -> Unit,
    performanceMode: Flow<com.illusion.app.domain.model.PerformanceMode>,
    onPerformanceModeChange: (com.illusion.app.domain.model.PerformanceMode) -> Unit,
    cacheSizeBytes: Long?,
    onRefreshCacheSize: () -> Unit,
    onOpenCache: () -> Unit,
    uiMode: Flow<UiMode?>,
    onUiModeChange: (UiMode) -> Unit,
    tvOverscanMarginPercent: Flow<Int>,
    onTvOverscanMarginPercentChange: (Int) -> Unit,
    defaultSortOrder: Flow<SortOrder>,
    onDefaultSortOrderChange: (SortOrder) -> Unit,
    hapticsEnabled: Flow<Boolean>,
    onHapticsEnabledChange: (Boolean) -> Unit,
    predictiveBackEnabled: Flow<Boolean>,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    glassEffectEnabled: Flow<Boolean>,
    posterAccentEnabled: Flow<Boolean>,
    onPosterAccentEnabledChange: (Boolean) -> Unit,
    parallaxEnabled: Flow<Boolean>,
    onParallaxEnabledChange: (Boolean) -> Unit,
    onGlassEffectEnabledChange: (Boolean) -> Unit,
    accentColor: Flow<com.illusion.app.domain.model.AccentColor>,
    onAccentColorChange: (com.illusion.app.domain.model.AccentColor) -> Unit,
    themeMode: Flow<com.illusion.app.domain.model.ThemeMode>,
    onThemeModeChange: (com.illusion.app.domain.model.ThemeMode) -> Unit,
    onRescanNow: () -> Unit,
    onRescanForceNow: () -> Unit,
    isScanRunning: Boolean,
    onOpenRunningScan: () -> Unit,
    downloadsFolderUri: Flow<String?>,
    onPickDownloadsFolder: (android.net.Uri?) -> Unit,
    downloadsSizeBytes: Long?,
    onRefreshDownloadsSize: () -> Unit,
    onClearDownloads: () -> Unit,
    onRecoverDownloads: (android.net.Uri) -> Unit,
    recoveredDownloadsCount: Int?,
    onDismissRecoveredDownloadsMessage: () -> Unit,
    onExportBackup: (android.net.Uri) -> Unit,
    onImportBackup: (android.net.Uri) -> Unit,
    pendingImportSources: List<BackupSource>,
    onConfirmImportSource: (String) -> Unit,
    onSkipImportSource: () -> Unit,
    backupMessage: String?,
    onDismissBackupMessage: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (SmbSourceEntity) -> Unit,
    onDeleteSource: (SmbSourceEntity) -> Unit,
    onSourceEnabledChange: (SmbSourceEntity, Boolean) -> Unit,
    onResetToDefaults: () -> Unit,
    onFactoryReset: () -> Unit,
    hasDevPassword: () -> Boolean,
    onGenerateDevPassword: () -> String,
    onVerifyDevPassword: (String) -> Boolean,
    isDevAccessRemembered: () -> Boolean,
    onRememberDevAccess: () -> Unit,
    onForgetDevAccess: () -> Unit,
    onDevAccessGranted: () -> Unit,
    onCheckForUpdates: () -> Unit,
    upToDateMessage: String?,
    onDismissUpToDateMessage: () -> Unit,
    updateCheckIntervalHours: Flow<Int>,
    updateSource: Flow<com.illusion.app.domain.model.UpdateSource>,
    onUpdateSourceChange: (com.illusion.app.domain.model.UpdateSource) -> Unit,
    localUpdateSourceId: Flow<Long?>,
    onLocalUpdateSourceIdChange: (Long) -> Unit,
    onUpdateCheckIntervalChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUiMode by uiMode.collectAsState(initial = null)
    val currentTvOverscanMarginPercent by tvOverscanMarginPercent.collectAsState(initial = 0)
    val currentDefaultSortOrder by defaultSortOrder.collectAsState(initial = SortOrder.RATING)
    val currentPlayerMode by playerMode.collectAsState(initial = com.illusion.app.domain.model.PlayerMode.INTERNAL)
    val currentExternalPlayerPackage by externalPlayerPackage.collectAsState(initial = null)
    val currentPlayerBufferSize by playerBufferSize.collectAsState(initial = com.illusion.app.domain.model.PlayerBufferSize.INCREASED)
    val currentPerformanceMode by performanceMode.collectAsState(initial = com.illusion.app.domain.model.PerformanceMode.AUTO)
    val hapticsOn by hapticsEnabled.collectAsState(initial = true)
    val predictiveBackOn by predictiveBackEnabled.collectAsState(initial = true)
    val glassEffectOn by glassEffectEnabled.collectAsState(initial = false)
    val posterAccentOn by posterAccentEnabled.collectAsState(initial = true)
    val parallaxOn by parallaxEnabled.collectAsState(initial = true)
    val currentAccentColor by accentColor.collectAsState(initial = com.illusion.app.domain.model.AccentColor.ILLUSION)
    val currentThemeMode by themeMode.collectAsState(initial = com.illusion.app.domain.model.ThemeMode.SYSTEM)
    // Same effective-dark logic as IllusionTheme itself - the accent swatches need to preview
    // whichever of an accent's two variants (light/dark*Primary) is actually going to apply right
    // now, not always the light one regardless of theme (see AccentColorSwatch's own updated KDoc).
    val effectiveDarkTheme = when (currentThemeMode) {
        com.illusion.app.domain.model.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        com.illusion.app.domain.model.ThemeMode.LIGHT -> false
        com.illusion.app.domain.model.ThemeMode.DARK, com.illusion.app.domain.model.ThemeMode.BLACK -> true
    }
    val downloadsFolder by downloadsFolderUri.collectAsState(initial = null)
    // Deleting a source used to fire straight from the trash icon with no confirmation - the most
    // destructive action on this whole screen (orphans everything that source scanned into the
    // library) had less friction than clearing a poster cache. Mirrors the confirm-dialog pattern
    // already used for cache clearing / history removal elsewhere in the app.
    var pendingDeleteSource by remember { mutableStateOf<SmbSourceEntity?>(null) }
    val context = LocalContext.current
    val noFileAppMessage = stringResource(R.string.settings_downloads_no_file_app)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onPickDownloadsFolder(uri)
    }
    // Separate from folderPicker above - this one doesn't change where new downloads are saved, it
    // just points at a folder (typically Download/Illusion) to scan once for files a data clear or
    // reinstall orphaned. See DownloadRepository.recoverOrphanedDownloads's KDoc for why this has to
    // be a manual folder pick rather than an automatic scan.
    val recoverFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onRecoverDownloads(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportBackup(uri)
    }

    LaunchedEffect(Unit) { onRefreshDownloadsSize() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) { onRefreshCacheSize() }

    val importingSource = pendingImportSources.firstOrNull()
    if (importingSource != null) {
        var password by remember(importingSource) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onSkipImportSource,
            title = { Text(stringResource(R.string.settings_backup_import_password_title, importingSource.displayName)) },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.settings_backup_import_password_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                val confirmSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { onConfirmImportSource(password) },
                    interactionSource = confirmSource,
                    modifier = Modifier.focusHighlight(confirmSource)
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                val cancelSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = onSkipImportSource,
                    interactionSource = cancelSource,
                    modifier = Modifier.focusHighlight(cancelSource)
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (backupMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissBackupMessage,
            title = { Text(stringResource(R.string.settings_backup)) },
            text = { Text(backupMessage) },
            confirmButton = {
                val closeSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = onDismissBackupMessage,
                    interactionSource = closeSource,
                    modifier = Modifier.focusHighlight(closeSource)
                ) { Text(stringResource(R.string.player_close)) }
            }
        )
    }

    // Entry point into the developer-only "add media" scraper - a visible settings entry,
    // password-gated (see DevAccessStore's KDoc for why this is a deterrent, not real security),
    // no longer hidden behind repeated taps on the version string.
    var showDevPasswordEntry by remember { mutableStateOf(false) }
    var showDevPasswordGenerated by remember { mutableStateOf<String?>(null) }
    var devPasswordError by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showFactoryResetConfirm by remember { mutableStateOf(false) }
    var showClearDownloadsConfirm by remember { mutableStateOf(false) }
    var showForceScanConfirm by remember { mutableStateOf(false) }
    var showTvModeWarning by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = com.illusion.app.ui.common.tvSafeContentWindowInsets(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                // Bar was transparent, so rows scrolling past it were cut off mid-letter with
                // nothing behind them. An opaque surface gives the content something to disappear
                // under instead of appearing to be clipped in mid-air.
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(if (category != null) categoryTitle(category!!) else stringResource(R.string.settings_title)) },
                navigationIcon = {
                    val backSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { onBack() },
                        interactionSource = backSource,
                        modifier = Modifier.focusHighlight(backSource)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
                    }
                },
                actions = {
                    // Only meaningful inside the SMB-sources category now - shown elsewhere it had
                    // no relation to whatever category the user was actually looking at.
                    if (category == "smb_sources") {
                        com.illusion.app.ui.common.TvAwareIconButton(onClick = onAddSource) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_source))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        // Список и содержимое категории - теперь разные пункты навигации, так что одновременно
        // рисуется только что-то одно. Переход между ними и предпросмотр жеста назад ведёт NavHost -
        // здесь больше нет ни Crossfade, ни ручной анимации по прогрессу жеста.
        //
        // Позицию прокрутки списка больше не надо поднимать вручную: экран списка остаётся в
        // бэкстеке, а rememberScrollState сохраняется вместе с его состоянием.
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                when (category) {
                    null -> SettingsCategoryList(
                        cacheSizeBytes = cacheSizeBytes,
                        showDeveloperEntry = currentUiMode != UiMode.TV,
                        onOpenCategory = onOpenCategory,
                        onOpenCache = onOpenCache
                    )

                    "about" -> SettingsAboutCategory(
                        effectiveDarkTheme = effectiveDarkTheme,
                        sources = sources,
                        upToDateMessage = upToDateMessage,
                        onDismissUpToDateMessage = onDismissUpToDateMessage,
                        onCheckForUpdates = onCheckForUpdates,
                        updateCheckIntervalHours = updateCheckIntervalHours,
                        onUpdateCheckIntervalChange = onUpdateCheckIntervalChange,
                        updateSource = updateSource,
                        onUpdateSourceChange = onUpdateSourceChange,
                        localUpdateSourceId = localUpdateSourceId,
                        onLocalUpdateSourceIdChange = onLocalUpdateSourceIdChange,
                        onOpenCategory = onOpenCategory
                    )
                    "libraries" -> {
                        AboutLibraries()
                    }

                    "smb_sources" -> SettingsSourcesCategory(
                        sources = sources,
                        sourcesMissingPassword = sourcesMissingPassword,
                        onAddSource = onAddSource,
                        onEditSource = onEditSource,
                        onSourceEnabledChange = onSourceEnabledChange,
                        onRequestDelete = { pendingDeleteSource = it }
                    )

                    "ui_mode" -> SettingsAppearanceCategory(
                        isTvMode = currentUiMode == UiMode.TV,
                        effectiveDarkTheme = effectiveDarkTheme,
                        currentThemeMode = currentThemeMode,
                        onThemeModeChange = onThemeModeChange,
                        hapticsOn = hapticsOn,
                        onHapticsEnabledChange = onHapticsEnabledChange,
                        predictiveBackOn = predictiveBackOn,
                        onPredictiveBackEnabledChange = onPredictiveBackEnabledChange,
                        glassEffectOn = glassEffectOn,
                        onGlassEffectEnabledChange = onGlassEffectEnabledChange,
                        posterAccentOn = posterAccentOn,
                        onPosterAccentEnabledChange = onPosterAccentEnabledChange,
                        parallaxOn = parallaxOn,
                        onParallaxEnabledChange = onParallaxEnabledChange,
                        currentAccentColor = currentAccentColor,
                        onAccentColorChange = onAccentColorChange
                    )

                    "screen_mode" -> {
                        // Was two bare ListItems (title + radio, no icon, no description) glued
                        // into one card by a divider - per feedback that read as plain/unclear
                        // next to the rest of Settings. Each option now its own card (same
                        // one-option-per-card separation "Сброс" uses) with a leading icon in the
                        // same tonal-container style as CategoryRow's own leading icon, plus a
                        // one-line description of what the mode actually means.
                        SettingsGroup(modifier = Modifier.padding(bottom = 12.dp)) {
                            val phoneRowSource = remember { MutableInteractionSource() }
                            ScreenModeOptionRow(
                                title = stringResource(R.string.settings_ui_mode_phone),
                                description = stringResource(R.string.settings_ui_mode_phone_description),
                                icon = Icons.Default.Smartphone,
                                selected = currentUiMode == UiMode.PHONE,
                                interactionSource = phoneRowSource,
                                onClick = { onUiModeChange(UiMode.PHONE) }
                            )
                        }
                        SettingsGroup {
                            // Switching TO TV mode needs a confirmation first (not switching away
                            // from it, and not re-selecting it while already on it) - tv-material
                            // components only respond to a D-pad Enter while already focused, never
                            // to a plain touch tap (see TvAwareControls.kt's own KDoc) - a
                            // touch-only device stuck in TV mode can't tap its way back out of this
                            // exact screen either, since Settings' own buttons switch to tv-material
                            // the instant this takes effect. Per feedback.
                            val requestTvMode = { if (currentUiMode == UiMode.TV) Unit else showTvModeWarning = true }
                            val tvRowSource = remember { MutableInteractionSource() }
                            ScreenModeOptionRow(
                                title = stringResource(R.string.settings_ui_mode_tv),
                                description = stringResource(R.string.settings_ui_mode_tv_description),
                                icon = Icons.Default.Tv,
                                selected = currentUiMode == UiMode.TV,
                                interactionSource = tvRowSource,
                                onClick = requestTvMode
                            )
                        }
                        // Only meaningful in TV mode (IllusionNavHost only ever applies this
                        // margin there) - how much a real TV box crops varies by device (0%, 8%,
                        // and "way too much at 8%" were all seen on different real panels this
                        // session), so this is user-adjustable rather than a single hardcoded
                        // guess baked into the app.
                        if (currentUiMode == UiMode.TV) {
                            SettingsGroup {
                                SettingsActionCard(
                                    title = stringResource(R.string.settings_tv_overscan_margin),
                                    description = stringResource(R.string.settings_tv_overscan_margin_hint)
                                ) {
                                    TvOverscanMarginMenu(currentTvOverscanMarginPercent, onTvOverscanMarginPercentChange, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    "performance" -> {
                        SettingsGroup {
                            SettingsActionCard(
                                title = stringResource(R.string.settings_performance_mode),
                                description = stringResource(R.string.settings_performance_mode_description)
                            ) {
                                com.illusion.app.domain.model.PerformanceMode.entries.forEach { mode ->
                                    PerformanceModeOptionRow(
                                        mode = mode,
                                        selected = currentPerformanceMode == mode,
                                        onClick = { onPerformanceModeChange(mode) }
                                    )
                                }
                                val deviceClass = remember { com.illusion.app.data.settings.DevicePerformance.classify(context) }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(16.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Column {
                                        Text(
                                            stringResource(
                                                if (deviceClass.isLowEnd) R.string.settings_performance_device_class_low
                                                else R.string.settings_performance_device_class_normal
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            stringResource(R.string.settings_performance_device_ram, deviceClass.totalRamMb),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "library" -> {
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            SettingsActionCard(title = stringResource(R.string.settings_default_sort_order)) {
                                DefaultSortOrderMenu(currentDefaultSortOrder, onDefaultSortOrderChange, modifier = Modifier.fillMaxWidth())
                            }
                            // Scanning used to be its own top-level category - merged in here per
                            // user feedback, since it's really just another aspect of managing the
                            // library, not a separate concern of its own.
                            SettingsDivider()
                            if (isScanRunning) {
                                // A scan already running underneath, silently - tapping "Сканировать"
                                // again here would just REPLACE it with an identical fresh run (no
                                // visible effect), and per feedback there was previously no
                                // indication anywhere in the app that a dismissed scan was still
                                // going. This surfaces it directly instead of a plain idle button.
                                SettingsActionCard(
                                    title = stringResource(R.string.settings_scan_running),
                                    description = stringResource(R.string.settings_scan_running_description)
                                ) {
                                    TvAwareButton(onClick = onOpenRunningScan, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.settings_scan_running_open))
                                    }
                                }
                            } else {
                                SettingsActionCard(
                                    title = stringResource(R.string.settings_rescan_now),
                                    description = stringResource(R.string.settings_rescan_now_description)
                                ) {
                                    TvAwareButton(
                                        onClick = onRescanNow,
                                        enabled = sources.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.settings_rescan_now_action))
                                    }
                                }
                                SettingsDivider()
                                SettingsActionCard(
                                    title = stringResource(R.string.settings_rescan_force_now),
                                    description = stringResource(R.string.settings_rescan_force_now_description)
                                ) {
                                    TvAwareOutlinedButton(
                                        onClick = { showForceScanConfirm = true },
                                        enabled = sources.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.settings_rescan_force_now_action))
                                    }
                                }
                            }
                        }
                    }

                    "player" -> {
                        SettingsGroup {
                            // Was a one-off "open in external player" button inside the player's
                            // own settings sheet (had to be tapped every single playback) - moved
                            // here as a persistent default per user feedback: choose the player
                            // once, not every time.
                            // Card style (title/description full-width, action full-width below) -
                            // was a ListItem(trailingContent = Menu), which broke badly in TV mode:
                            // TvAwareOutlinedButton's TV branch centers its label via an internal
                            // Modifier.fillMaxWidth() Row, and Material3's ListItem sizes its
                            // trailingContent slot from that same composable's INTRINSIC width -
                            // fillMaxWidth() reports an unbounded/maximal intrinsic width regardless
                            // of the actual caller-side modifier, so ListItem reserved almost all
                            // the row's width for the trailing button and left next to none for
                            // headlineContent, which then wrapped its description one character per
                            // line (confirmed from on-device TV photos - "буферизация, но" etc.
                            // rendering as a vertical letter column). The card layout sidesteps this
                            // entirely since the button's own full-width slot has no competing
                            // sibling to starve.
                            SettingsActionCard(
                                title = stringResource(R.string.settings_player_mode),
                                description = stringResource(R.string.settings_player_mode_description)
                            ) {
                                PlayerModeMenu(currentPlayerMode, onPlayerModeChange, modifier = Modifier.fillMaxWidth())
                            }
                            // Only relevant once external playback can actually happen - hidden for
                            // PlayerMode.INTERNAL rather than shown-but-disabled, since it has no
                            // effect at all in that mode.
                            AnimatedVisibility(
                                visible = currentPlayerMode != com.illusion.app.domain.model.PlayerMode.INTERNAL,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                SettingsActionCard(
                                    title = stringResource(R.string.settings_external_player_app),
                                    description = stringResource(R.string.settings_external_player_app_description)
                                ) {
                                    ExternalPlayerAppMenu(currentExternalPlayerPackage, onExternalPlayerPackageChange, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            AnimatedVisibility(
                                visible = currentPlayerMode != com.illusion.app.domain.model.PlayerMode.EXTERNAL,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                SettingsActionCard(
                                    title = stringResource(R.string.settings_player_buffer_size),
                                    description = stringResource(R.string.settings_player_buffer_size_description)
                                ) {
                                    PlayerBufferSizeMenu(currentPlayerBufferSize, onPlayerBufferSizeChange, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    "downloads" -> {
                        SettingsGroup {
                            SettingsActionCard(
                                title = stringResource(R.string.settings_downloads_folder),
                                description = DownloadStorage.folderDisplayName(context, downloadsFolder)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    TvAwareButton(
                                        onClick = {
                                            val intent = DownloadStorage.openFolderIntent(context, downloadsFolder)
                                            if (intent != null) {
                                                runCatching { context.startActivity(intent) }
                                                    .onFailure {
                                                        android.util.Log.w("SettingsScreen", "openFolderIntent failed", it)
                                                        android.widget.Toast.makeText(context, noFileAppMessage, android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                            } else {
                                                android.widget.Toast.makeText(context, noFileAppMessage, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                        Text(stringResource(R.string.settings_downloads_open_folder))
                                    }
                                    TvAwareOutlinedButton(
                                        onClick = { folderPicker.launch(DownloadStorage.pickerInitialUri()) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Was text-only next to "Открыть папку"'s icon+text - same
                                        // weight(1f) width, but the missing icon still read as a
                                        // different, lighter style sitting right next to it. Per
                                        // feedback.
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                        Text(stringResource(R.string.settings_downloads_choose_folder))
                                    }
                                }
                            }
                            SettingsDivider()
                            SettingsActionCard(
                                title = if (downloadsSizeBytes != null) {
                                    stringResource(R.string.settings_downloads_size, formatBytes(downloadsSizeBytes))
                                } else {
                                    stringResource(R.string.settings_cache_size_unknown)
                                }
                            ) {
                                val clearDownloadsSource = remember { MutableInteractionSource() }
                                OutlinedButton(
                                    onClick = { showClearDownloadsConfirm = true },
                                    interactionSource = clearDownloadsSource,
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth().focusHighlight(clearDownloadsSource)
                                ) {
                                    Text(stringResource(R.string.settings_downloads_clear))
                                }
                            }
                            SettingsDivider()
                            // Manual recovery for files a data clear/reinstall orphaned - the user
                            // points at their real Downloads/Illusion folder (or wherever they see
                            // it via a file manager) once via the system picker; nothing runs
                            // automatically at startup (see this button's onClick / the repository
                            // function's own KDoc for why an automatic scan doesn't actually work).
                            SettingsActionCard(
                                title = stringResource(R.string.settings_downloads_recover_action),
                                description = stringResource(R.string.settings_downloads_recover_description)
                            ) {
                                TvAwareButton(
                                    onClick = { recoverFolderPicker.launch(DownloadStorage.pickerInitialUri()) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_downloads_recover_action))
                                }
                                if (recoveredDownloadsCount != null) {
                                    LaunchedEffect(recoveredDownloadsCount) {
                                        kotlinx.coroutines.delay(4000)
                                        onDismissRecoveredDownloadsMessage()
                                    }
                                    Text(
                                        if (recoveredDownloadsCount > 0) {
                                            stringResource(R.string.settings_downloads_recover_result, recoveredDownloadsCount)
                                        } else {
                                            stringResource(R.string.settings_downloads_recover_none)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    "backup" -> {
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            SettingsActionCard(
                                title = stringResource(R.string.settings_backup_contents_title),
                                description = stringResource(R.string.settings_backup_contents)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    TvAwareButton(
                                        onClick = { exportLauncher.launch("illusion-backup.json") },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                        Text(stringResource(R.string.settings_backup_export))
                                    }
                                    TvAwareOutlinedButton(
                                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                        Text(stringResource(R.string.settings_backup_import))
                                    }
                                }
                            }
                        }
                    }

                    "add_media" -> {
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            SettingsActionCard(
                                title = stringResource(R.string.settings_add_media),
                                description = stringResource(R.string.settings_add_media_description)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    if (isDevAccessRemembered()) {
                                        TvAwareButton(onClick = onForgetDevAccess, modifier = Modifier.weight(1f)) {
                                            Text(stringResource(R.string.settings_dev_access_forget))
                                        }
                                    }
                                    TvAwareButton(
                                        onClick = {
                                            when {
                                                isDevAccessRemembered() -> onDevAccessGranted()
                                                hasDevPassword() -> showDevPasswordEntry = true
                                                else -> showDevPasswordGenerated = onGenerateDevPassword()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.settings_add_media_open))
                                    }
                                }
                            }
                        }
                    }

                    "feedback" -> {
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            SettingsActionCard(
                                title = stringResource(R.string.settings_feedback_contact_title),
                                description = stringResource(R.string.settings_feedback_description)
                            ) {
                                Text(
                                    stringResource(R.string.settings_feedback_privacy),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                TvAwareOutlinedButton(
                                    onClick = { context.startActivity(com.illusion.app.data.crash.CrashReporter.feedbackIntent("bug")) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_feedback_bug_action))
                                }
                                Spacer(Modifier.height(8.dp))
                                TvAwareOutlinedButton(
                                    onClick = { context.startActivity(com.illusion.app.data.crash.CrashReporter.feedbackIntent("idea")) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_feedback_idea_action))
                                }
                            }
                        }
                    }

                    "reset" -> {
                        SettingsGroup {
                            SettingsActionCard(
                                title = stringResource(R.string.settings_reset_to_defaults),
                                description = stringResource(R.string.settings_reset_to_defaults_description)
                            ) {
                                TvAwareButton(onClick = { showResetConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.settings_reset_to_defaults_action))
                                }
                            }
                        }
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            SettingsActionCard(
                                title = stringResource(R.string.settings_factory_reset),
                                description = stringResource(R.string.settings_factory_reset_description)
                            ) {
                                val factoryResetSource = remember { MutableInteractionSource() }
                                OutlinedButton(
                                    onClick = { showFactoryResetConfirm = true },
                                    interactionSource = factoryResetSource,
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth().focusHighlight(factoryResetSource)
                                ) {
                                    Text(stringResource(R.string.settings_factory_reset_action))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteSource?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSource = null },
            title = { Text(stringResource(R.string.settings_delete_source_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_source_confirm_message, source.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.reject()
                    onDeleteSource(source)
                    pendingDeleteSource = null
                }) { Text(stringResource(R.string.settings_delete_source)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSource = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showForceScanConfirm) {
        AlertDialog(
            onDismissRequest = { showForceScanConfirm = false },
            title = { Text(stringResource(R.string.settings_rescan_force_confirm_title)) },
            text = { Text(stringResource(R.string.settings_rescan_force_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onRescanForceNow()
                    showForceScanConfirm = false
                }) { Text(stringResource(R.string.settings_rescan_force_now_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showForceScanConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showClearDownloadsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsConfirm = false },
            title = {
                Text(
                    stringResource(
                        R.string.settings_downloads_clear_confirm_title,
                        formatBytes(downloadsSizeBytes ?: 0L)
                    )
                )
            },
            text = { Text(stringResource(R.string.settings_downloads_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.reject()
                        onClearDownloads()
                        showClearDownloadsConfirm = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_downloads_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val resetDoneMessage = stringResource(R.string.settings_reset_done)
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_reset_to_defaults)) },
            text = { Text(stringResource(R.string.settings_reset_to_defaults_confirm)) },
            // Resolved here rather than inside the click lambda: reading resources off
            // LocalContext at click time misses a configuration change (lint's own point).

            confirmButton = {
                TextButton(onClick = {
                    haptics.reject()
                    onResetToDefaults()
                    showResetConfirm = false
                    coroutineScope.launch { snackbarHostState.showSnackbar(resetDoneMessage) }
                }) { Text(stringResource(R.string.settings_reset_to_defaults_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showTvModeWarning) {
        AlertDialog(
            onDismissRequest = { showTvModeWarning = false },
            title = { Text(stringResource(R.string.settings_ui_mode_tv_warning_title)) },
            text = { Text(stringResource(R.string.settings_ui_mode_tv_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.reject()
                    onUiModeChange(UiMode.TV)
                    showTvModeWarning = false
                }) { Text(stringResource(R.string.settings_ui_mode_tv_warning_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTvModeWarning = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showFactoryResetConfirm) {
        AlertDialog(
            onDismissRequest = { showFactoryResetConfirm = false },
            title = { Text(stringResource(R.string.settings_factory_reset)) },
            text = { Text(stringResource(R.string.settings_factory_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.reject()
                    onFactoryReset()
                    showFactoryResetConfirm = false
                }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.settings_factory_reset_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryResetConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    showDevPasswordGenerated?.let { password ->
        AlertDialog(
            onDismissRequest = { showDevPasswordGenerated = null; onRememberDevAccess(); onDevAccessGranted() },
            title = { Text(stringResource(R.string.settings_dev_password_generated_title)) },
            text = { Text(stringResource(R.string.settings_dev_password_generated_message, password)) },
            confirmButton = {
                TextButton(onClick = { showDevPasswordGenerated = null; onRememberDevAccess(); onDevAccessGranted() }) {
                    Text(stringResource(R.string.settings_dev_password_saved))
                }
            }
        )
    }

    if (showDevPasswordEntry) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDevPasswordEntry = false },
            title = { Text(stringResource(R.string.settings_dev_password_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; devPasswordError = false },
                        label = { Text(stringResource(R.string.settings_dev_password_label)) },
                        isError = devPasswordError,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true
                    )
                    if (devPasswordError) {
                        Text(
                            stringResource(R.string.settings_dev_password_wrong),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onVerifyDevPassword(input)) {
                        showDevPasswordEntry = false
                        onRememberDevAccess()
                        onDevAccessGranted()
                    } else {
                        devPasswordError = true
                    }
                }) { Text(stringResource(R.string.settings_dev_access_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDevPasswordEntry = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
