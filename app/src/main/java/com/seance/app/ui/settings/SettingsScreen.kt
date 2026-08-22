package com.seance.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import com.seance.app.domain.model.SortOrder
import com.seance.app.domain.model.UiMode
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seance.app.R
import com.seance.app.data.backup.BackupSource
import com.seance.app.data.download.DownloadStorage
import com.seance.app.data.local.entity.SmbSourceEntity
import com.seance.app.ui.common.focusHighlight
import com.seance.app.ui.common.reject
import com.seance.app.ui.common.segmentTick
import com.seance.app.ui.common.toggle
import com.seance.app.ui.library.sortLabel
import kotlinx.coroutines.flow.Flow

private val RESCAN_OPTIONS = listOf(0, 6, 12, 24, 48)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sources: List<SmbSourceEntity>,
    requireChargingForHeavyTasks: Flow<Boolean>,
    rescanIntervalHours: Flow<Int>,
    playerMode: Flow<com.seance.app.domain.model.PlayerMode>,
    onPlayerModeChange: (com.seance.app.domain.model.PlayerMode) -> Unit,
    cacheSizeBytes: Long?,
    onRefreshCacheSize: () -> Unit,
    onOpenCache: () -> Unit,
    uiMode: Flow<UiMode?>,
    onUiModeChange: (UiMode) -> Unit,
    defaultSortOrder: Flow<SortOrder>,
    onDefaultSortOrderChange: (SortOrder) -> Unit,
    hapticsEnabled: Flow<Boolean>,
    onHapticsEnabledChange: (Boolean) -> Unit,
    predictiveBackEnabled: Flow<Boolean>,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    accentColor: Flow<com.seance.app.domain.model.AccentColor>,
    onAccentColorChange: (com.seance.app.domain.model.AccentColor) -> Unit,
    onToggleChargingRequirement: (Boolean) -> Unit,
    onRescanIntervalChange: (Int) -> Unit,
    onRescanNow: () -> Unit,
    downloadsFolderUri: Flow<String?>,
    onPickDownloadsFolder: (android.net.Uri?) -> Unit,
    downloadsSizeBytes: Long?,
    onRefreshDownloadsSize: () -> Unit,
    onClearDownloads: () -> Unit,
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
    onResetToDefaults: () -> Unit,
    onFactoryReset: () -> Unit,
    hasDevPassword: () -> Boolean,
    onGenerateDevPassword: () -> String,
    onVerifyDevPassword: (String) -> Boolean,
    isDevAccessRemembered: () -> Boolean,
    onRememberDevAccess: () -> Unit,
    onForgetDevAccess: () -> Unit,
    onDevAccessGranted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUiMode by uiMode.collectAsState(initial = null)
    val currentDefaultSortOrder by defaultSortOrder.collectAsState(initial = SortOrder.DATE_ADDED)
    val currentPlayerMode by playerMode.collectAsState(initial = com.seance.app.domain.model.PlayerMode.INTERNAL)
    val hapticsOn by hapticsEnabled.collectAsState(initial = true)
    val predictiveBackOn by predictiveBackEnabled.collectAsState(initial = true)
    val currentAccentColor by accentColor.collectAsState(initial = com.seance.app.domain.model.AccentColor.DEFAULT)
    val chargingOnly by requireChargingForHeavyTasks.collectAsState(initial = true)
    val rescanHours by rescanIntervalHours.collectAsState(initial = 48)
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
    val noFileAppMessage = stringResource(R.string.settings_downloads_no_file_app)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onPickDownloadsFolder(uri)
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = com.seance.app.ui.common.rememberLatchedStatusBarsInsets(),
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
                        val addSourceSource = remember { MutableInteractionSource() }
                        IconButton(onClick = onAddSource, interactionSource = addSourceSource, modifier = Modifier.focusHighlight(addSourceSource)) {
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
                            icon = Icons.Default.Storage,
                            onClick = { selectedCategory = "smb_sources" }
                        )
                        SettingsDivider()
                        CategoryRow(
                            title = stringResource(R.string.settings_ui_mode_section),
                            description = stringResource(R.string.settings_category_ui_mode_description),
                            icon = Icons.Default.Tune,
                            onClick = { selectedCategory = "ui_mode" }
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
                            icon = Icons.Default.DeleteSweep,
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
                            icon = Icons.Default.Movie,
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

                        Text(
                            text = stringResource(R.string.settings_version, com.seance.app.BuildConfig.VERSION_NAME, com.seance.app.BuildConfig.VERSION_CODE),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
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
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_ui_mode_tv)) },
                                trailingContent = {
                                    RadioButton(
                                        selected = currentUiMode == UiMode.TV,
                                        onClick = { onUiModeChange(UiMode.TV) }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusHighlight(tvRowSource)
                                    .clickable(interactionSource = tvRowSource, indication = LocalIndication.current) { onUiModeChange(UiMode.TV) }
                            )
                            SettingsDivider()
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_haptics)) },
                                supportingContent = { Text(stringResource(R.string.settings_haptics_description)) },
                                trailingContent = {
                                    Switch(
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
                                    Switch(
                                        checked = predictiveBackOn,
                                        onCheckedChange = onPredictiveBackEnabledChange
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Accent color merged in here (was its own top-level category) per user
                        // feedback - it's another interface-level appearance choice, same as the
                        // phone/TV mode and haptics/predictive-back switches above.
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
                                com.seance.app.domain.model.AccentColor.entries.forEach { color ->
                                    AccentColorSwatch(
                                        color = color,
                                        selected = color == currentAccentColor,
                                        onClick = { onAccentColorChange(color) }
                                    )
                                }
                            }
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
                            ListItem(
                                // Folded into headlineContent - see the "player" branch's own
                                // ListItem below for why (ListItem top-aligns trailing content
                                // whenever supportingContent is present).
                                headlineContent = {
                                    Column {
                                        Text(stringResource(R.string.settings_rescan_now))
                                        Text(
                                            stringResource(R.string.settings_rescan_now_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingContent = {
                                    val rescanNowSource = remember { MutableInteractionSource() }
                                    OutlinedButton(
                                        onClick = onRescanNow,
                                        enabled = sources.isNotEmpty(),
                                        interactionSource = rescanNowSource,
                                        modifier = Modifier.focusHighlight(rescanNowSource)
                                    ) {
                                        Text(stringResource(R.string.settings_rescan_now_action))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SettingsDivider()
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_rescan_interval)) },
                                trailingContent = { RescanIntervalMenu(rescanHours, onRescanIntervalChange) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SettingsDivider()
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_charging_only)) },
                                supportingContent = { Text(stringResource(R.string.settings_charging_only_description)) },
                                trailingContent = {
                                    Switch(
                                        checked = chargingOnly,
                                        onCheckedChange = { enabled ->
                                            haptics.toggle(enabled)
                                            onToggleChargingRequirement(enabled)
                                        }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
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
                        }
                    }

                    "downloads" -> {
                        SettingsGroup {
                            ListItem(
                                headlineContent = {
                                    Column {
                                        Text(stringResource(R.string.settings_downloads_folder))
                                        Text(
                                            DownloadStorage.folderDisplayName(context, downloadsFolder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        val openFolderSource = remember { MutableInteractionSource() }
                                        IconButton(
                                            onClick = {
                                                val intent = DownloadStorage.openFolderIntent(context, downloadsFolder)
                                                if (intent != null) {
                                                    runCatching { context.startActivity(intent) }
                                                        .onFailure { android.widget.Toast.makeText(context, noFileAppMessage, android.widget.Toast.LENGTH_SHORT).show() }
                                                } else {
                                                    android.widget.Toast.makeText(context, noFileAppMessage, android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            interactionSource = openFolderSource,
                                            modifier = Modifier.focusHighlight(openFolderSource)
                                        ) {
                                            Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.settings_downloads_open_folder))
                                        }
                                        val chooseFolderSource = remember { MutableInteractionSource() }
                                        OutlinedButton(
                                            onClick = { folderPicker.launch(DownloadStorage.pickerInitialUri()) },
                                            interactionSource = chooseFolderSource,
                                            modifier = Modifier.focusHighlight(chooseFolderSource)
                                        ) {
                                            Text(stringResource(R.string.settings_downloads_choose_folder))
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SettingsDivider()
                            // Not a ListItem with a trailing button - "Скачано: 0,0 МБ" plus a
                            // long "Удалить все загрузки" button squeezed into ListItem's shared
                            // headline/trailing Row left too little width for the text in portrait,
                            // wrapping it awkwardly mid-value ("0,0" / "МБ" on separate lines). Full
                            // row width for the text, button on its own line below instead.
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    if (downloadsSizeBytes != null) {
                                        stringResource(R.string.settings_downloads_size, formatBytes(downloadsSizeBytes))
                                    } else {
                                        stringResource(R.string.settings_cache_size_unknown)
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                                    val clearDownloadsSource = remember { MutableInteractionSource() }
                                    OutlinedButton(
                                        onClick = onClearDownloads,
                                        interactionSource = clearDownloadsSource,
                                        modifier = Modifier.focusHighlight(clearDownloadsSource)
                                    ) {
                                        Text(stringResource(R.string.settings_downloads_clear))
                                    }
                                }
                            }
                        }
                    }

                    "backup" -> {
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_backup)) },
                                trailingContent = {
                                    Row {
                                        val exportSource = remember { MutableInteractionSource() }
                                        TextButton(
                                            onClick = { exportLauncher.launch("seance-backup.json") },
                                            interactionSource = exportSource,
                                            modifier = Modifier.focusHighlight(exportSource)
                                        ) {
                                            Text(stringResource(R.string.settings_backup_export))
                                        }
                                        val importSource = remember { MutableInteractionSource() }
                                        TextButton(
                                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                                            interactionSource = importSource,
                                            modifier = Modifier.focusHighlight(importSource)
                                        ) {
                                            Text(stringResource(R.string.settings_backup_import))
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    "add_media" -> {
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(stringResource(R.string.settings_add_media), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.settings_add_media_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (isDevAccessRemembered()) {
                                        val forgetSource = remember { MutableInteractionSource() }
                                        TextButton(
                                            onClick = onForgetDevAccess,
                                            interactionSource = forgetSource,
                                            modifier = Modifier.focusHighlight(forgetSource)
                                        ) {
                                            Text(stringResource(R.string.settings_dev_access_forget))
                                        }
                                    }
                                    val addMediaSource = remember { MutableInteractionSource() }
                                    TextButton(
                                        onClick = {
                                            when {
                                                isDevAccessRemembered() -> onDevAccessGranted()
                                                hasDevPassword() -> showDevPasswordEntry = true
                                                else -> showDevPasswordGenerated = onGenerateDevPassword()
                                            }
                                        },
                                        interactionSource = addMediaSource,
                                        modifier = Modifier.focusHighlight(addMediaSource)
                                    ) {
                                        Text(stringResource(R.string.settings_add_media_open))
                                    }
                                }
                            }
                        }
                    }

                    "feedback" -> {
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            ListItem(
                                headlineContent = {
                                    Column {
                                        Text(stringResource(R.string.settings_feedback))
                                        Text(
                                            stringResource(R.string.settings_feedback_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingContent = {
                                    val feedbackSource = remember { MutableInteractionSource() }
                                    OutlinedButton(
                                        onClick = { context.startActivity(android.content.Intent.createChooser(com.seance.app.data.crash.CrashReporter.feedbackIntent(), null)) },
                                        interactionSource = feedbackSource,
                                        modifier = Modifier.focusHighlight(feedbackSource)
                                    ) {
                                        Text(stringResource(R.string.settings_feedback_action))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    "reset" -> {
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            ListItem(
                                headlineContent = {
                                    Column {
                                        Text(stringResource(R.string.settings_reset_to_defaults))
                                        Text(
                                            stringResource(R.string.settings_reset_to_defaults_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingContent = {
                                    val resetSource = remember { MutableInteractionSource() }
                                    OutlinedButton(
                                        onClick = { showResetConfirm = true },
                                        interactionSource = resetSource,
                                        modifier = Modifier.focusHighlight(resetSource)
                                    ) {
                                        Text(stringResource(R.string.settings_reset_to_defaults_action))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                            ListItem(
                                headlineContent = {
                                    Column {
                                        Text(stringResource(R.string.settings_factory_reset))
                                        Text(
                                            stringResource(R.string.settings_factory_reset_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingContent = {
                                    val factoryResetSource = remember { MutableInteractionSource() }
                                    OutlinedButton(
                                        onClick = { showFactoryResetConfirm = true },
                                        interactionSource = factoryResetSource,
                                        modifier = Modifier.focusHighlight(factoryResetSource),
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text(stringResource(R.string.settings_factory_reset_action))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )
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
    "library" -> stringResource(R.string.settings_library_section)
    "scan" -> stringResource(R.string.settings_scan_section)
    "player" -> stringResource(R.string.settings_player_section)
    "downloads" -> stringResource(R.string.settings_downloads)
    "backup" -> stringResource(R.string.settings_backup)
    "add_media" -> stringResource(R.string.settings_add_media)
    "feedback" -> stringResource(R.string.settings_feedback)
    "reset" -> stringResource(R.string.settings_reset_section)
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
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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

/** One swatch in the accent-color picker - the swatch shows [AccentColor.lightPrimary] regardless of the active theme (dark or light), since it's a color *choice*, not a themed surface. */
@Composable
private fun AccentColorSwatch(
    color: com.seance.app.domain.model.AccentColor,
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
            .background(color.lightPrimary)
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
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White
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
        OutlinedButton(onClick = { expanded = true }, interactionSource = triggerSource, modifier = Modifier.focusHighlight(triggerSource)) {
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

@Composable
private fun RescanIntervalMenu(hours: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        OutlinedButton(onClick = { expanded = true }, interactionSource = triggerSource, modifier = Modifier.focusHighlight(triggerSource)) {
            Text(rescanLabel(hours))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RESCAN_OPTIONS.forEach { option ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(rescanLabel(option)) },
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
private fun rescanLabel(hours: Int): String =
    if (hours <= 0) stringResource(R.string.settings_rescan_off) else stringResource(R.string.settings_rescan_hours, hours)

@Composable
private fun PlayerModeMenu(current: com.seance.app.domain.model.PlayerMode, onChange: (com.seance.app.domain.model.PlayerMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        OutlinedButton(onClick = { expanded = true }, interactionSource = triggerSource, modifier = Modifier.focusHighlight(triggerSource)) {
            Text(playerModeLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.seance.app.domain.model.PlayerMode.entries.forEach { mode ->
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
private fun playerModeLabel(mode: com.seance.app.domain.model.PlayerMode): String = when (mode) {
    com.seance.app.domain.model.PlayerMode.INTERNAL -> stringResource(R.string.settings_player_mode_internal)
    com.seance.app.domain.model.PlayerMode.EXTERNAL -> stringResource(R.string.settings_player_mode_external)
    com.seance.app.domain.model.PlayerMode.ASK -> stringResource(R.string.settings_player_mode_ask)
}

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f ГБ".format(mb / 1024) else "%.1f МБ".format(mb)
}
