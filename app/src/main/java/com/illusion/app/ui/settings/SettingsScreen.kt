package com.illusion.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import com.illusion.app.domain.model.SortOrder
import com.illusion.app.domain.model.UiMode
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import com.illusion.app.data.backup.BackupSource
import com.illusion.app.data.download.DownloadStorage
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.ui.common.TvAwareOutlinedButton
import com.illusion.app.ui.common.TvAwareSwitch
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.reject
import com.illusion.app.ui.common.segmentTick
import com.illusion.app.ui.common.tick
import com.illusion.app.ui.common.toggle
import com.illusion.app.ui.library.sortLabel
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sources: List<SmbSourceEntity>,
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
    // Which category's own full-screen content is showing, or null for the top-level category
    // list - replaces an earlier collapsible-accordion design (all sections inline, expand/collapse
    // per section) per feedback that it still felt cluttered as one long scroll. rememberSaveable
    // so a config change (rotation, etc) doesn't silently kick the user back out to the category
    // list mid-edit.
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedCategory != null) { selectedCategory = null }
    val context = LocalContext.current
    // PackageManager's own component-enabled state (see IconVariantManager) is already the
    // persistent source of truth - no need to duplicate it into SettingsRepository/DataStore.
    var currentAppIcon by remember { mutableStateOf(com.illusion.app.data.appicon.IconVariantManager.current(context)) }
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
    var showTvModeWarning by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(if (selectedCategory != null) categoryTitle(selectedCategory!!) else stringResource(R.string.settings_title)) },
                navigationIcon = {
                    val backSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { if (selectedCategory != null) selectedCategory = null else onBack() },
                        interactionSource = backSource,
                        modifier = Modifier.focusHighlight(backSource)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
                    }
                },
                actions = {
                    // Only meaningful inside the SMB-sources category now - shown elsewhere it had
                    // no relation to whatever category the user was actually looking at.
                    if (selectedCategory == "smb_sources") {
                        com.illusion.app.ui.common.TvAwareIconButton(onClick = onAddSource) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_source))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        // Hoisted above Crossfade (not just rememberScrollState() inside it) specifically so the
        // top-level category list's scroll position survives leaving it for a category and coming
        // back - Crossfade disposes each target's composition once it's no longer showing, so a
        // scroll state remembered inside the content lambda got recreated at 0 on every return
        // trip instead of restoring where the user had scrolled to.
        val categoryListScrollState = rememberScrollState()
        Crossfade(targetState = selectedCategory, modifier = Modifier.fillMaxSize().padding(innerPadding), label = "settings_category") { category ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(if (category == null) categoryListScrollState else rememberScrollState())
            ) {
                when (category) {
                    null -> {
                        CategoryRow(
                            title = stringResource(R.string.settings_smb_sources),
                            description = stringResource(R.string.settings_category_smb_sources_description),
                            icon = Icons.Default.Dns,
                            onClick = { selectedCategory = "smb_sources" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_ui_mode_section),
                            description = stringResource(R.string.settings_category_ui_mode_description),
                            icon = Icons.Default.Palette,
                            onClick = { selectedCategory = "ui_mode" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_screen_mode_section),
                            description = stringResource(R.string.settings_category_screen_mode_description),
                            icon = Icons.Default.Devices,
                            onClick = { selectedCategory = "screen_mode" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_performance_section),
                            description = stringResource(R.string.settings_category_performance_description),
                            icon = Icons.Default.Speed,
                            onClick = { selectedCategory = "performance" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_library_section),
                            description = stringResource(R.string.settings_category_library_description),
                            icon = Icons.Default.VideoLibrary,
                            onClick = { selectedCategory = "library" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_scan_section),
                            description = stringResource(R.string.settings_category_scan_description),
                            icon = Icons.Default.Sync,
                            onClick = { selectedCategory = "scan" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_player_section),
                            description = stringResource(R.string.settings_category_player_description),
                            icon = Icons.Default.PlayCircle,
                            onClick = { selectedCategory = "player" }
                        )
                        SettingsDivider()
                        // Unlike the other rows, this one navigates straight to the real Cache
                        // screen (already its own NavController destination) rather than to a
                        // category panel here - nothing to reorganize, it already worked this way.
                        CategoryRow(
                            title = stringResource(R.string.settings_cache),
                            description = if (cacheSizeBytes != null) {
                                stringResource(R.string.settings_cache_size, formatBytes(cacheSizeBytes))
                            } else {
                                stringResource(R.string.settings_cache_size_unknown)
                            },
                            icon = Icons.Default.Storage,
                            onClick = onOpenCache
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_downloads),
                            description = stringResource(R.string.settings_category_downloads_description),
                            icon = Icons.Default.Download,
                            onClick = { selectedCategory = "downloads" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_backup),
                            description = stringResource(R.string.settings_category_backup_description),
                            icon = Icons.Default.Backup,
                            onClick = { selectedCategory = "backup" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_add_media),
                            description = stringResource(R.string.settings_add_media_description),
                            icon = Icons.Default.LibraryAdd,
                            onClick = { selectedCategory = "add_media" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_feedback),
                            description = stringResource(R.string.settings_feedback_description),
                            icon = Icons.Default.Feedback,
                            onClick = { selectedCategory = "feedback" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_reset_section),
                            description = stringResource(R.string.settings_category_reset_description),
                            icon = Icons.Default.RestartAlt,
                            onClick = { selectedCategory = "reset" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_about_section),
                            description = stringResource(R.string.settings_version, com.illusion.app.BuildConfig.VERSION_NAME),
                            icon = Icons.Default.Info,
                            onClick = { selectedCategory = "about" }
                        )
                    }

                    "about" -> {
                        val aboutContext = LocalContext.current
                        fun openUrl(url: String) {
                            runCatching { aboutContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                        }
                        SettingsGroup {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.app_name)) },
                                supportingContent = { Text(stringResource(R.string.settings_about_tagline)) },
                                leadingContent = {
                                    // R.mipmap.ic_launcher is Android Studio's stock template
                                    // placeholder (never updated after the real icon redesign,
                                    // see README's own icon-fix commit) - the actual mark lives in
                                    // the adaptive icon's own layers (mipmap-anydpi/ic_launcher.xml:
                                    // ic_mark foreground on an icon_bg background), reassembled
                                    // here the same way rather than pointing at the stale bitmap.
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(colorResource(R.color.icon_bg)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_mark),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SettingsDivider()
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_version, com.illusion.app.BuildConfig.VERSION_NAME)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SettingsDivider()
                            // A separate row (not the version's own supportingContent line) so it's
                            // its own tap target - tapping it 7 times triggers a small easter egg,
                            // the same "tap the build number" joke stock Android's own Settings has
                            // (there it unlocks Developer Options; this app's real dev-tools unlock
                            // is a normal visible menu entry elsewhere, per the user's own earlier
                            // preference against gating it behind tap-counting obscurity - this is
                            // purely for fun, no functional side effect).
                            var buildNumberTapCount by remember { mutableStateOf(0) }
                            var lastBuildNumberTapAt by remember { mutableStateOf(0L) }
                            val eggContext = LocalContext.current
                            // A fresh Toast.makeText().show() per tap QUEUES on Android instead of
                            // replacing the previous one - confirmed on-device: tapping through the
                            // 4/5/6 countdown fired 3 separate ~2s toasts back to back, so the real
                            // punchline on tap 7 only appeared after several seconds of stacked
                            // wait, and the system's rapid-toast rate limiting on top of that
                            // visibly truncated/overlapped the text. Cancelling the previous Toast
                            // before showing the next one collapses that into a single, instantly-
                            // updating toast, same as a live countdown should look.
                            var activeEggToast by remember { mutableStateOf<android.widget.Toast?>(null) }
                            // The punchline (unlike the short countdown hints) is a full sentence -
                            // MIUI's own Toast rendering (this device's OEM skin) truncates longer
                            // toast text to one line with an ellipsis rather than wrapping it, no
                            // matter LENGTH_LONG - confirmed on-device even after fixing the
                            // queueing above. A real dialog is never OEM-truncated like that.
                            var showEggDialog by remember { mutableStateOf(false) }
                            val buildNumberSource = remember { MutableInteractionSource() }
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_build_number, com.illusion.app.BuildConfig.VERSION_CODE)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusHighlight(buildNumberSource)
                                    .clickable(interactionSource = buildNumberSource, indication = LocalIndication.current) {
                                        val now = System.currentTimeMillis()
                                        buildNumberTapCount = if (now - lastBuildNumberTapAt > 1500) 1 else buildNumberTapCount + 1
                                        lastBuildNumberTapAt = now
                                        when {
                                            buildNumberTapCount in 4..6 -> {
                                                haptics.segmentTick()
                                                activeEggToast?.cancel()
                                                activeEggToast = android.widget.Toast.makeText(
                                                    eggContext,
                                                    eggContext.getString(R.string.settings_easter_egg_countdown, 7 - buildNumberTapCount),
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).also { it.show() }
                                            }
                                            buildNumberTapCount >= 7 -> {
                                                haptics.tick()
                                                buildNumberTapCount = 0
                                                activeEggToast?.cancel()
                                                showEggDialog = true
                                            }
                                        }
                                    }
                            )
                            if (showEggDialog) {
                                AlertDialog(
                                    onDismissRequest = { showEggDialog = false },
                                    confirmButton = {
                                        TextButton(onClick = { showEggDialog = false }) {
                                            Text(stringResource(R.string.player_close))
                                        }
                                    },
                                    text = { Text(stringResource(R.string.settings_easter_egg_message)) }
                                )
                            }
                            SettingsDivider()
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_check_updates)) },
                                supportingContent = if (upToDateMessage != null) {
                                    {
                                        LaunchedEffect(upToDateMessage) {
                                            kotlinx.coroutines.delay(4000)
                                            onDismissUpToDateMessage()
                                        }
                                        // Only the "already up to date" success message gets the
                                        // green pill treatment - a failure message (no internet,
                                        // manifest not found, ...) shares this same
                                        // supportingContent slot and should never look like a
                                        // success by association.
                                        val successPrefix = "У вас последняя версия!"
                                        if (upToDateMessage.startsWith(successPrefix)) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color(0xFF2E7D32).copy(alpha = 0.15f))
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    upToDateMessage,
                                                    color = Color(0xFF2E7D32),
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        } else {
                                            Text(upToDateMessage)
                                        }
                                    }
                                } else null,
                                trailingContent = {
                                    TvAwareOutlinedButton(onClick = onCheckForUpdates) {
                                        Text(stringResource(R.string.action_check))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SettingsDivider()
                            val currentUpdateCheckIntervalHours by updateCheckIntervalHours.collectAsState(initial = 720)
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_update_check_interval)) },
                                trailingContent = { UpdateCheckIntervalMenu(currentUpdateCheckIntervalHours, onUpdateCheckIntervalChange) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SettingsDivider()
                            val currentUpdateSource by updateSource.collectAsState(initial = com.illusion.app.domain.model.UpdateSource.GITHUB)
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_update_source)) },
                                supportingContent = { Text(stringResource(R.string.settings_update_source_hint)) },
                                trailingContent = { UpdateSourceMenu(currentUpdateSource, onUpdateSourceChange) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (currentUpdateSource == com.illusion.app.domain.model.UpdateSource.LOCAL) {
                                SettingsDivider()
                                val currentLocalUpdateSourceId by localUpdateSourceId.collectAsState(initial = null)
                                // Switching the row above to "Локально" without also picking a
                                // specific source here (an easy thing to miss - two separate
                                // dropdowns, only the first one is obviously "the switch") left
                                // localUpdateSourceId null, and the actual check silently failed
                                // with "не выбран источник" - confirmed on-device. Auto-picks the
                                // first configured source instead of requiring that second tap
                                // whenever there's an unambiguous default (exactly one, or none
                                // chosen yet) to pick.
                                LaunchedEffect(currentUpdateSource, sources) {
                                    if (currentLocalUpdateSourceId == null && sources.isNotEmpty()) {
                                        onLocalUpdateSourceIdChange(sources.first().id)
                                    }
                                }
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.settings_local_update_source)) },
                                    supportingContent = {
                                        Text(
                                            if (sources.isEmpty()) {
                                                stringResource(R.string.settings_local_update_source_none)
                                            } else {
                                                stringResource(R.string.settings_local_update_source_path_hint)
                                            }
                                        )
                                    },
                                    trailingContent = {
                                        if (sources.isNotEmpty()) {
                                            LocalUpdateSourceMenu(sources, currentLocalUpdateSourceId, onLocalUpdateSourceIdChange)
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            SettingsDivider()
                            val developerSource = remember { MutableInteractionSource() }
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_about_developer)) },
                                supportingContent = { Text("github.com/maximredko91") },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(interactionSource = developerSource, indication = LocalIndication.current) {
                                        openUrl("https://github.com/maximredko91")
                                    }
                            )
                            SettingsDivider()
                            val sourceCodeSource = remember { MutableInteractionSource() }
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_about_source_code)) },
                                supportingContent = { Text("github.com/maximredko91/illusion") },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(interactionSource = sourceCodeSource, indication = LocalIndication.current) {
                                        openUrl("https://github.com/maximredko91/illusion")
                                    }
                            )
                            SettingsDivider()
                            val licenseSource = remember { MutableInteractionSource() }
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_about_license)) },
                                supportingContent = { Text(stringResource(R.string.settings_about_license_value)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(interactionSource = licenseSource, indication = LocalIndication.current) {
                                        openUrl("https://github.com/maximredko91/illusion/blob/main/LICENSE")
                                    }
                            )
                            SettingsDivider()
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_about_libraries)) },
                                supportingContent = { Text(stringResource(R.string.settings_about_libraries_value)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    "smb_sources" -> {
                        SettingsGroup {
                            if (sources.isEmpty()) {
                                ListItem(headlineContent = { Text(stringResource(R.string.settings_no_sources)) })
                            } else {
                                sources.forEachIndexed { index, source ->
                                    if (index > 0) SettingsDivider()
                                    val rowSource = remember { MutableInteractionSource() }
                                    ListItem(
                                        headlineContent = { Text(source.displayName) },
                                        supportingContent = { Text("\\\\${source.host}\\${source.share}") },
                                        trailingContent = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Only shown once there's more than one source to choose between - a
                                                // single connected source has nothing to select against, and the
                                                // switch would just be a confusing way to fully empty the library.
                                                if (sources.size > 1) {
                                                    TvAwareSwitch(
                                                        checked = source.enabled,
                                                        onCheckedChange = {
                                                            haptics.segmentTick()
                                                            onSourceEnabledChange(source, it)
                                                        }
                                                    )
                                                }
                                                val deleteSource = remember { MutableInteractionSource() }
                                                IconButton(
                                                    onClick = {
                                                        haptics.reject()
                                                        pendingDeleteSource = source
                                                    },
                                                    interactionSource = deleteSource,
                                                    modifier = Modifier.focusHighlight(deleteSource)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = stringResource(R.string.settings_delete_source)
                                                    )
                                                }
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusHighlight(rowSource)
                                            .clickable(interactionSource = rowSource, indication = LocalIndication.current) { onEditSource(source) }
                                    )
                                }
                            }
                        }
                    }

                    "ui_mode" -> {
                        SettingsGroup {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_theme_mode)) },
                                trailingContent = { ThemeModeMenu(currentThemeMode, onThemeModeChange) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Both are touch-only concepts - haptics needs a vibration motor no
                            // remote/TV box has, and predictive back is Android's edge-swipe
                            // gesture preview, which doesn't exist without a touchscreen to swipe
                            // on. Dead, confusing toggles on TV rather than hidden clutter.
                            if (currentUiMode != UiMode.TV) {
                            SettingsDivider()
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_haptics)) },
                                supportingContent = { Text(stringResource(R.string.settings_haptics_description)) },
                                trailingContent = {
                                    TvAwareSwitch(
                                        checked = hapticsOn,
                                        onCheckedChange = { enabled ->
                                            // Fires regardless of direction (even turning off) -
                                            // this Switch's own toggle click is itself gated by
                                            // LocalHapticFeedback, so it's the last tactile
                                            // confirmation the user gets either way.
                                            haptics.toggle(enabled)
                                            onHapticsEnabledChange(enabled)
                                        }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SettingsDivider()
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_predictive_back)) },
                                supportingContent = { Text(stringResource(R.string.settings_predictive_back_description)) },
                                trailingContent = {
                                    TvAwareSwitch(
                                        checked = predictiveBackOn,
                                        onCheckedChange = onPredictiveBackEnabledChange
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            }
                        }

                        // Accent color merged in here (was its own top-level category) per user
                        // feedback - it's another interface-level appearance choice, same as the
                        // theme/haptics/predictive-back switches above. Phone/TV mode used to live
                        // in this same screen too but moved out to its own "Режим экрана" category -
                        // it's a structural/functional choice (which whole layout the app uses),
                        // not an appearance one like everything else here.
                        SettingsGroup(modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)) {
                            Text(
                                stringResource(R.string.settings_accent_color),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                            // FlowRow, not a plain Row - 7 swatches at 40dp + spacing (~352dp) can
                            // exceed a narrow phone's available width once the Card's own padding
                            // is subtracted, and a plain Row doesn't wrap. Wrapping to a second
                            // line reaches every swatch without needing a horizontal scroll (tried
                            // first, dropped per user feedback - swatches should all be visible at
                            // once, not scrolled through).
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                com.illusion.app.domain.model.AccentColor.entries.forEach { color ->
                                    // Fixed-width column, not wrap-content: a plain Column's width
                                    // follows its widest child, so a longer label ("Бирюзовый")
                                    // pushed that whole cell wider than a shorter one ("Синий") -
                                    // FlowRow packs cells by their actual width, so circles ended up
                                    // at different x-offsets per row instead of lining up in a grid.
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(76.dp)
                                    ) {
                                        AccentColorSwatch(
                                            color = if (effectiveDarkTheme) color.darkPrimary else color.lightPrimary,
                                            selected = color == currentAccentColor,
                                            onClick = { onAccentColorChange(color) }
                                        )
                                        Text(
                                            accentColorLabel(color),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = 2,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Alternate launcher icons - one per accent color, same swap-an-alias
                        // mechanism a bunch of well-known apps use (Twitter/X, Spotify, ...); see
                        // IconVariantManager's own KDoc for why this can't be a plain
                        // SettingsRepository-backed value.
                        SettingsGroup(modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)) {
                            Text(
                                stringResource(R.string.settings_app_icon),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                com.illusion.app.domain.model.AppIcon.entries.forEach { icon ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(76.dp)
                                    ) {
                                        AppIconSwatch(
                                            icon = icon,
                                            selected = icon == currentAppIcon,
                                            onClick = {
                                                com.illusion.app.data.appicon.IconVariantManager.apply(context, icon)
                                                currentAppIcon = icon
                                            }
                                        )
                                        Text(
                                            appIconLabel(icon),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = 2,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "screen_mode" -> {
                        SettingsGroup {
                            val phoneRowSource = remember { MutableInteractionSource() }
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_ui_mode_phone)) },
                                trailingContent = {
                                    RadioButton(
                                        selected = currentUiMode == UiMode.PHONE,
                                        onClick = { onUiModeChange(UiMode.PHONE) }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusHighlight(phoneRowSource)
                                    .clickable(interactionSource = phoneRowSource, indication = LocalIndication.current) { onUiModeChange(UiMode.PHONE) }
                            )
                            SettingsDivider()
                            val tvRowSource = remember { MutableInteractionSource() }
                            // Switching TO TV mode needs a confirmation first (not switching away
                            // from it, and not re-selecting it while already on it) - tv-material
                            // components only respond to a D-pad Enter while already focused, never
                            // to a plain touch tap (see TvAwareControls.kt's own KDoc) - a
                            // touch-only device stuck in TV mode can't tap its way back out of this
                            // exact screen either, since Settings' own buttons switch to tv-material
                            // the instant this takes effect. Per feedback.
                            val requestTvMode = { if (currentUiMode == UiMode.TV) Unit else showTvModeWarning = true }
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_ui_mode_tv)) },
                                trailingContent = {
                                    RadioButton(
                                        selected = currentUiMode == UiMode.TV,
                                        onClick = requestTvMode
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusHighlight(tvRowSource)
                                    .clickable(interactionSource = tvRowSource, indication = LocalIndication.current, onClick = requestTvMode)
                            )
                        }
                        // Only meaningful in TV mode (IllusionNavHost only ever applies this
                        // margin there) - how much a real TV box crops varies by device (0%, 8%,
                        // and "way too much at 8%" were all seen on different real panels this
                        // session), so this is user-adjustable rather than a single hardcoded
                        // guess baked into the app.
                        if (currentUiMode == UiMode.TV) {
                            SettingsGroup {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.settings_tv_overscan_margin)) },
                                    supportingContent = { Text(stringResource(R.string.settings_tv_overscan_margin_hint)) },
                                    trailingContent = {
                                        TvOverscanMarginMenu(currentTvOverscanMarginPercent, onTvOverscanMarginPercentChange)
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    "performance" -> {
                        SettingsGroup {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_performance_mode)) },
                                supportingContent = { Text(stringResource(R.string.settings_performance_mode_description)) },
                                trailingContent = {
                                    PerformanceModeMenu(currentPerformanceMode, onPerformanceModeChange)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    "library" -> {
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_default_sort_order)) },
                                trailingContent = { DefaultSortOrderMenu(currentDefaultSortOrder, onDefaultSortOrderChange) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    "scan" -> {
                        SettingsGroup {
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
                                    TvAwareOutlinedButton(onClick = onOpenRunningScan, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.settings_scan_running_open))
                                    }
                                }
                            } else {
                                SettingsActionCard(
                                    title = stringResource(R.string.settings_rescan_now),
                                    description = stringResource(R.string.settings_rescan_now_description)
                                ) {
                                    TvAwareOutlinedButton(
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
                                        onClick = onRescanForceNow,
                                        enabled = sources.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.settings_rescan_now_action))
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
                            ListItem(
                                // Description folded into headlineContent (not a separate
                                // supportingContent slot) specifically so the trailing chip centers
                                // vertically - Material3's ListItem deliberately top-aligns trailing
                                // content whenever supportingContent is present (2/3-line item
                                // spec), which read as misaligned next to the other single-line
                                // rows' centered chips in this same group.
                                headlineContent = {
                                    Column {
                                        Text(stringResource(R.string.settings_player_mode))
                                        Text(
                                            stringResource(R.string.settings_player_mode_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingContent = { PlayerModeMenu(currentPlayerMode, onPlayerModeChange) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Only relevant once external playback can actually happen - hidden for
                            // PlayerMode.INTERNAL rather than shown-but-disabled, since it has no
                            // effect at all in that mode.
                            if (currentPlayerMode != com.illusion.app.domain.model.PlayerMode.INTERNAL) {
                                ListItem(
                                    headlineContent = {
                                        Column {
                                            Text(stringResource(R.string.settings_external_player_app))
                                            Text(
                                                stringResource(R.string.settings_external_player_app_description),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        ExternalPlayerAppMenu(currentExternalPlayerPackage, onExternalPlayerPackageChange)
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            ListItem(
                                headlineContent = {
                                    Column {
                                        Text(stringResource(R.string.settings_player_buffer_size))
                                        Text(
                                            stringResource(R.string.settings_player_buffer_size_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingContent = {
                                    PlayerBufferSizeMenu(currentPlayerBufferSize, onPlayerBufferSizeChange)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    "downloads" -> {
                        SettingsGroup {
                            SettingsActionCard(
                                title = stringResource(R.string.settings_downloads_folder),
                                description = DownloadStorage.folderDisplayName(context, downloadsFolder)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    TvAwareOutlinedButton(
                                        onClick = {
                                            val intent = DownloadStorage.openFolderIntent(context, downloadsFolder)
                                            if (intent != null) {
                                                runCatching { context.startActivity(intent) }
                                                    .onFailure { android.widget.Toast.makeText(context, noFileAppMessage, android.widget.Toast.LENGTH_SHORT).show() }
                                            } else {
                                                android.widget.Toast.makeText(context, noFileAppMessage, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                        Text(stringResource(R.string.settings_downloads_open_folder))
                                    }
                                    TvAwareOutlinedButton(
                                        onClick = { folderPicker.launch(DownloadStorage.pickerInitialUri()) },
                                        modifier = Modifier.weight(1f)
                                    ) {
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
                                TvAwareOutlinedButton(onClick = onClearDownloads, modifier = Modifier.fillMaxWidth()) {
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
                                TvAwareOutlinedButton(
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
                                title = stringResource(R.string.settings_backup),
                                description = stringResource(R.string.settings_backup_description)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    TvAwareOutlinedButton(
                                        onClick = { exportLauncher.launch("illusion-backup.json") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.settings_backup_export))
                                    }
                                    TvAwareOutlinedButton(
                                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                                        modifier = Modifier.weight(1f)
                                    ) {
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
                                        TvAwareOutlinedButton(onClick = onForgetDevAccess, modifier = Modifier.weight(1f)) {
                                            Text(stringResource(R.string.settings_dev_access_forget))
                                        }
                                    }
                                    TvAwareOutlinedButton(
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
                                title = stringResource(R.string.settings_feedback),
                                description = stringResource(R.string.settings_feedback_description)
                            ) {
                                TvAwareOutlinedButton(
                                    onClick = { context.startActivity(android.content.Intent.createChooser(com.illusion.app.data.crash.CrashReporter.feedbackIntent(), null)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_feedback_action))
                                }
                            }
                        }
                    }

                    "reset" -> {
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            SettingsActionCard(
                                title = stringResource(R.string.settings_reset_to_defaults),
                                description = stringResource(R.string.settings_reset_to_defaults_description)
                            ) {
                                TvAwareOutlinedButton(onClick = { showResetConfirm = true }, modifier = Modifier.fillMaxWidth()) {
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

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_reset_to_defaults)) },
            text = { Text(stringResource(R.string.settings_reset_to_defaults_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.reject()
                    onResetToDefaults()
                    showResetConfirm = false
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
                }) { Text(stringResource(R.string.settings_factory_reset_action)) }
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

/** Title shown in the TopAppBar once a category row has been tapped - keys match [SettingsScreen]'s `selectedCategory` values. */
@Composable
private fun categoryTitle(key: String): String = when (key) {
    "smb_sources" -> stringResource(R.string.settings_smb_sources)
    "ui_mode" -> stringResource(R.string.settings_ui_mode_section)
    "screen_mode" -> stringResource(R.string.settings_screen_mode_section)
    "performance" -> stringResource(R.string.settings_performance_section)
    "library" -> stringResource(R.string.settings_library_section)
    "scan" -> stringResource(R.string.settings_scan_section)
    "player" -> stringResource(R.string.settings_player_section)
    "downloads" -> stringResource(R.string.settings_downloads)
    "backup" -> stringResource(R.string.settings_backup)
    "add_media" -> stringResource(R.string.settings_add_media)
    "feedback" -> stringResource(R.string.settings_feedback)
    "reset" -> stringResource(R.string.settings_reset_section)
    "about" -> stringResource(R.string.settings_about_section)
    else -> stringResource(R.string.settings_title)
}

/** One row on the top-level settings screen - tapping it navigates into that category's own full-screen content (or, for Cache, straight to its own real screen). */
@Composable
private fun CategoryRow(title: String, description: String?, icon: ImageVector, onClick: () -> Unit) {
    val rowSource = remember { MutableInteractionSource() }
    ListItem(
        // Description folded into headlineContent (not a separate supportingContent slot) for the
        // same reason as PlayerModeMenu's ListItem above - Material3 top-aligns leading/trailing
        // content whenever supportingContent is present, and since each category's description
        // wraps to a different number of lines, that made the icons look inconsistently placed row
        // to row instead of all sitting at the same relative height.
        headlineContent = {
            Column {
                Text(title)
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        leadingContent = {
            // Bare icon on the bare list background used to read as flat/generic - a rounded
            // tonal container (same idea Android's own system Settings and most polished apps use
            // for a category-icon leading slot) gives each row a bit more visual weight and ties
            // it to whatever accent color the user has picked (primaryContainer/onPrimaryContainer
            // already follow AccentColor, see Theme.kt), instead of a plain tinted glyph floating
            // on its own. Per feedback.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(rowSource)
            .clickable(interactionSource = rowSource, indication = LocalIndication.current, onClick = onClick)
    )
}

/** Groups related settings rows into one visually bounded card, so adjacent unrelated rows don't blend together. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = content
    )
}

@Composable
internal fun SettingsDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun accentColorLabel(color: com.illusion.app.domain.model.AccentColor): String = when (color) {
    com.illusion.app.domain.model.AccentColor.DEFAULT -> stringResource(R.string.accent_color_default)
    com.illusion.app.domain.model.AccentColor.ILLUSION -> stringResource(R.string.accent_color_illusion)
    com.illusion.app.domain.model.AccentColor.BLUE -> stringResource(R.string.accent_color_blue)
    com.illusion.app.domain.model.AccentColor.GREEN -> stringResource(R.string.accent_color_green)
    com.illusion.app.domain.model.AccentColor.ORANGE -> stringResource(R.string.accent_color_orange)
    com.illusion.app.domain.model.AccentColor.YELLOW -> stringResource(R.string.accent_color_yellow)
    com.illusion.app.domain.model.AccentColor.RED -> stringResource(R.string.accent_color_red)
    com.illusion.app.domain.model.AccentColor.TEAL -> stringResource(R.string.accent_color_teal)
    com.illusion.app.domain.model.AccentColor.PINK -> stringResource(R.string.accent_color_pink)
}

// Reuses the same accent_color_* strings as accentColorLabel above - AppIcon's entries are named
// 1:1 after AccentColor's (each icon variant's stroke color literally comes from that accent's own
// lightPrimary, see AppIcon's own KDoc), so there's nothing meaningfully different to say here.
@Composable
private fun appIconLabel(icon: com.illusion.app.domain.model.AppIcon): String = when (icon) {
    com.illusion.app.domain.model.AppIcon.DEFAULT -> stringResource(R.string.accent_color_default)
    com.illusion.app.domain.model.AppIcon.ILLUSION -> stringResource(R.string.accent_color_illusion)
    com.illusion.app.domain.model.AppIcon.BLUE -> stringResource(R.string.accent_color_blue)
    com.illusion.app.domain.model.AppIcon.GREEN -> stringResource(R.string.accent_color_green)
    com.illusion.app.domain.model.AppIcon.ORANGE -> stringResource(R.string.accent_color_orange)
    com.illusion.app.domain.model.AppIcon.YELLOW -> stringResource(R.string.accent_color_yellow)
    com.illusion.app.domain.model.AppIcon.RED -> stringResource(R.string.accent_color_red)
    com.illusion.app.domain.model.AppIcon.TEAL -> stringResource(R.string.accent_color_teal)
    com.illusion.app.domain.model.AppIcon.PINK -> stringResource(R.string.accent_color_pink)
}

private fun appIconForegroundRes(icon: com.illusion.app.domain.model.AppIcon): Int = when (icon) {
    com.illusion.app.domain.model.AppIcon.ILLUSION -> R.drawable.ic_mark
    com.illusion.app.domain.model.AppIcon.DEFAULT -> R.drawable.ic_mark_default
    com.illusion.app.domain.model.AppIcon.BLUE -> R.drawable.ic_mark_blue
    com.illusion.app.domain.model.AppIcon.GREEN -> R.drawable.ic_mark_green
    com.illusion.app.domain.model.AppIcon.ORANGE -> R.drawable.ic_mark_orange
    com.illusion.app.domain.model.AppIcon.YELLOW -> R.drawable.ic_mark_yellow
    com.illusion.app.domain.model.AppIcon.RED -> R.drawable.ic_mark_red
    com.illusion.app.domain.model.AppIcon.TEAL -> R.drawable.ic_mark_teal
    com.illusion.app.domain.model.AppIcon.PINK -> R.drawable.ic_mark_pink
}

/** Same size/selection-border language as [AccentColorSwatch], rounded-square instead of a circle so it reads as "icon", not "color" - the actual mark vector rendered over the launcher's real background color, not just an abstract swatch. */
@Composable
private fun AppIconSwatch(
    icon: com.illusion.app.domain.model.AppIcon,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .focusHighlight(interactionSource)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(androidx.compose.ui.res.colorResource(R.color.icon_bg))
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptics.segmentTick()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(appIconForegroundRes(icon)),
            contentDescription = null,
            modifier = Modifier.size(34.dp)
        )
    }
}

/**
 * One swatch in the accent-color picker. Takes the already-resolved [Color] rather than an
 * [AccentColor] - it used to always render `lightPrimary` regardless of which theme was actually
 * active, so in dark mode the picker's own preview didn't match what selecting that accent would
 * actually apply (dark mode uses each accent's `darkPrimary`, a different, usually more vivid
 * tone-80-ish pastel) - the caller now picks light/dark*Primary itself based on the real active
 * theme and passes the result straight through.
 */
@Composable
private fun AccentColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .focusHighlight(interactionSource)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptics.segmentTick()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            // Was a hardcoded white tint - fine against the old always-lightPrimary (tone-40,
            // reliably dark) swatches, but dark theme's darkPrimary tones are pale tone-80-ish
            // pastels a white checkmark barely shows up on.
            val checkTint = if (color.luminance() > 0.5f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = checkTint
            )
        }
    }
}

@Composable
private fun DefaultSortOrderMenu(current: SortOrder, onChange: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }) {
            Text(sortLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(sortLabel(order)) },
                    onClick = {
                        haptics.segmentTick()
                        onChange(order)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

private val TV_OVERSCAN_MARGIN_OPTIONS = listOf(0, 2, 4, 6, 8, 10)

@Composable
private fun TvOverscanMarginMenu(percent: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }) {
            Text(if (percent <= 0) stringResource(R.string.settings_tv_overscan_margin_off) else "$percent%")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TV_OVERSCAN_MARGIN_OPTIONS.forEach { option ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(if (option <= 0) stringResource(R.string.settings_tv_overscan_margin_off) else "$option%") },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

@Composable
private fun ThemeModeMenu(current: com.illusion.app.domain.model.ThemeMode, onChange: (com.illusion.app.domain.model.ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }) {
            Text(themeModeLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.illusion.app.domain.model.ThemeMode.entries.forEach { mode ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(themeModeLabel(mode)) },
                    onClick = {
                        haptics.segmentTick()
                        onChange(mode)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

@Composable
private fun themeModeLabel(mode: com.illusion.app.domain.model.ThemeMode): String = when (mode) {
    com.illusion.app.domain.model.ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_mode_system)
    com.illusion.app.domain.model.ThemeMode.LIGHT -> stringResource(R.string.settings_theme_mode_light)
    com.illusion.app.domain.model.ThemeMode.DARK -> stringResource(R.string.settings_theme_mode_dark)
    com.illusion.app.domain.model.ThemeMode.BLACK -> stringResource(R.string.settings_theme_mode_black)
}

@Composable
private fun PlayerModeMenu(current: com.illusion.app.domain.model.PlayerMode, onChange: (com.illusion.app.domain.model.PlayerMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }) {
            Text(playerModeLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.illusion.app.domain.model.PlayerMode.entries.forEach { mode ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(playerModeLabel(mode)) },
                    onClick = {
                        haptics.segmentTick()
                        onChange(mode)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

@Composable
private fun PlayerBufferSizeMenu(current: com.illusion.app.domain.model.PlayerBufferSize, onChange: (com.illusion.app.domain.model.PlayerBufferSize) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }) {
            Text(playerBufferSizeLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.illusion.app.domain.model.PlayerBufferSize.entries.forEach { size ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(playerBufferSizeLabel(size)) },
                    onClick = {
                        haptics.segmentTick()
                        onChange(size)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

@Composable
private fun playerBufferSizeLabel(size: com.illusion.app.domain.model.PlayerBufferSize): String = when (size) {
    com.illusion.app.domain.model.PlayerBufferSize.AUTO -> stringResource(R.string.settings_player_buffer_size_auto)
    com.illusion.app.domain.model.PlayerBufferSize.INCREASED -> stringResource(R.string.settings_player_buffer_size_increased)
    com.illusion.app.domain.model.PlayerBufferSize.MAXIMUM -> stringResource(R.string.settings_player_buffer_size_maximum)
}

@Composable
private fun PerformanceModeMenu(current: com.illusion.app.domain.model.PerformanceMode, onChange: (com.illusion.app.domain.model.PerformanceMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }) {
            Text(performanceModeLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.illusion.app.domain.model.PerformanceMode.entries.forEach { mode ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(performanceModeLabel(mode)) },
                    onClick = {
                        haptics.segmentTick()
                        onChange(mode)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

@Composable
private fun performanceModeLabel(mode: com.illusion.app.domain.model.PerformanceMode): String = when (mode) {
    com.illusion.app.domain.model.PerformanceMode.AUTO -> stringResource(R.string.settings_performance_mode_auto)
    com.illusion.app.domain.model.PerformanceMode.MAXIMUM -> stringResource(R.string.settings_performance_mode_maximum)
    com.illusion.app.domain.model.PerformanceMode.ECONOMICAL -> stringResource(R.string.settings_performance_mode_economical)
}

@Composable
private fun UpdateSourceMenu(current: com.illusion.app.domain.model.UpdateSource, onChange: (com.illusion.app.domain.model.UpdateSource) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }) {
            Text(updateSourceLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.illusion.app.domain.model.UpdateSource.entries.forEach { source ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(updateSourceLabel(source)) },
                    onClick = {
                        haptics.segmentTick()
                        onChange(source)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

@Composable
private fun updateSourceLabel(source: com.illusion.app.domain.model.UpdateSource): String = when (source) {
    com.illusion.app.domain.model.UpdateSource.GITHUB -> stringResource(R.string.settings_update_source_github)
    com.illusion.app.domain.model.UpdateSource.LOCAL -> stringResource(R.string.settings_update_source_local)
}

@Composable
private fun LocalUpdateSourceMenu(
    sources: List<SmbSourceEntity>,
    currentId: Long?,
    onChange: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val current = sources.firstOrNull { it.id == currentId }
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }) {
            Text(current?.displayName ?: stringResource(R.string.settings_local_update_source_pick))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sources.forEach { source ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(source.displayName) },
                    onClick = {
                        haptics.segmentTick()
                        onChange(source.id)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

private val UPDATE_CHECK_INTERVAL_OPTIONS = listOf(0, 24, 168, 720)

private fun updateCheckIntervalLabel(hours: Int): String = when {
    hours <= 0 -> "Выключено"
    hours < 168 -> "Раз в сутки"
    hours < 720 -> "Раз в неделю"
    else -> "Раз в месяц"
}

@Composable
private fun UpdateCheckIntervalMenu(currentHours: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }) {
            Text(updateCheckIntervalLabel(currentHours))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UPDATE_CHECK_INTERVAL_OPTIONS.forEach { hours ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(updateCheckIntervalLabel(hours)) },
                    onClick = {
                        haptics.segmentTick()
                        onChange(hours)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

@Composable
private fun ExternalPlayerAppMenu(currentPackage: String?, onChange: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    // Scanned once per composition rather than observed live - installed apps don't change while
    // this menu is open, and re-querying PackageManager on every recomposition would be wasteful.
    val apps = remember { com.illusion.app.data.player.InstalledPlayerApps.list(context) }
    val systemLabel = stringResource(R.string.settings_external_player_app_system)
    val currentLabel = apps.find { it.packageName == currentPackage }?.label ?: systemLabel
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }) {
            Text(currentLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val defaultSource = remember { MutableInteractionSource() }
            DropdownMenuItem(
                text = { Text(systemLabel) },
                onClick = {
                    haptics.segmentTick()
                    onChange(null)
                    expanded = false
                },
                interactionSource = defaultSource,
                modifier = Modifier.focusHighlight(defaultSource)
            )
            apps.forEach { app ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(app.label) },
                    onClick = {
                        haptics.segmentTick()
                        onChange(app.packageName)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
                )
            }
        }
    }
}

@Composable
private fun playerModeLabel(mode: com.illusion.app.domain.model.PlayerMode): String = when (mode) {
    com.illusion.app.domain.model.PlayerMode.INTERNAL -> stringResource(R.string.settings_player_mode_internal)
    com.illusion.app.domain.model.PlayerMode.EXTERNAL -> stringResource(R.string.settings_player_mode_external)
    com.illusion.app.domain.model.PlayerMode.ASK -> stringResource(R.string.settings_player_mode_ask)
}

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f ГБ".format(mb / 1024) else "%.1f МБ".format(mb)
}
