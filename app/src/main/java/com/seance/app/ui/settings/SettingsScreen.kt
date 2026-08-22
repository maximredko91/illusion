package com.seance.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import com.seance.app.domain.model.SortOrder
import com.seance.app.domain.model.UiMode
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    // Session-only (not persisted) collapse state, keyed by section title - all expanded by
    // default. One shared map instead of a separate `var expanded by remember` per section since
    // there are a dozen-plus of them here.
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
    fun isSectionExpanded(key: String) = expandedSections[key] != false
    fun toggleSection(key: String) { expandedSections[key] = !isSectionExpanded(key) }
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = com.seance.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    val backSource = remember { MutableInteractionSource() }
                    IconButton(onClick = onBack, interactionSource = backSource, modifier = Modifier.focusHighlight(backSource)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
                    }
                },
                actions = {
                    val addSourceSource = remember { MutableInteractionSource() }
                    IconButton(onClick = onAddSource, interactionSource = addSourceSource, modifier = Modifier.focusHighlight(addSourceSource)) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_source))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            CollapsibleSettingsHeader(stringResource(R.string.settings_smb_sources), isSectionExpanded("smb_sources"), { toggleSection("smb_sources") })
            SettingsGroup(visible = isSectionExpanded("smb_sources")) {
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

            CollapsibleSettingsHeader(stringResource(R.string.settings_ui_mode_section), isSectionExpanded("ui_mode"), { toggleSection("ui_mode") })
            SettingsGroup(visible = isSectionExpanded("ui_mode")) {
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
                                // Fires regardless of direction (even turning off) - this Switch's
                                // own toggle click is itself gated by LocalHapticFeedback, so it's
                                // the last tactile confirmation the user gets either way.
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

            CollapsibleSettingsHeader(stringResource(R.string.settings_accent_color), isSectionExpanded("accent_color"), { toggleSection("accent_color") })
            SettingsGroup(visible = isSectionExpanded("accent_color"), modifier = Modifier.padding(bottom = 24.dp)) {
                // FlowRow, not a plain Row - 7 swatches at 40dp + spacing (~352dp) can exceed a
                // narrow phone's available width once the Card's own padding is subtracted, and a
                // plain Row doesn't wrap. Wrapping to a second line reaches every swatch without
                // needing a horizontal scroll (tried first, dropped per user feedback - swatches
                // should all be visible at once, not scrolled through).
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

            CollapsibleSettingsHeader(stringResource(R.string.settings_library_section), isSectionExpanded("library"), { toggleSection("library") })
            SettingsGroup(visible = isSectionExpanded("library"), modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_default_sort_order)) },
                    trailingContent = { DefaultSortOrderMenu(currentDefaultSortOrder, onDefaultSortOrderChange) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CollapsibleSettingsHeader(stringResource(R.string.settings_scan_section), isSectionExpanded("scan"), { toggleSection("scan") })
            SettingsGroup(visible = isSectionExpanded("scan")) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_rescan_now)) },
                    supportingContent = { Text(stringResource(R.string.settings_rescan_now_description)) },
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

            CollapsibleSettingsHeader(stringResource(R.string.settings_player_section), isSectionExpanded("player"), { toggleSection("player") })
            SettingsGroup(visible = isSectionExpanded("player")) {
                // Was a one-off "open in external player" button inside the player's own settings
                // sheet (had to be tapped every single playback) - moved here as a persistent
                // default per user feedback: choose the player once, not every time.
                ListItem(
                    // Description folded into headlineContent (not a separate supportingContent
                    // slot) specifically so the trailing chip centers vertically - Material3's
                    // ListItem deliberately top-aligns trailing content whenever supportingContent
                    // is present (2/3-line item spec), which read as misaligned next to the other
                    // single-line rows' centered chips in this same group.
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

            CollapsibleSettingsHeader(stringResource(R.string.settings_cache), isSectionExpanded("cache"), { toggleSection("cache") })
            SettingsGroup(visible = isSectionExpanded("cache")) {
                val cacheRowSource = remember { MutableInteractionSource() }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_cache)) },
                    supportingContent = {
                        Text(
                            if (cacheSizeBytes != null) {
                                stringResource(R.string.settings_cache_size, formatBytes(cacheSizeBytes))
                            } else {
                                stringResource(R.string.settings_cache_size_unknown)
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusHighlight(cacheRowSource)
                        .clickable(interactionSource = cacheRowSource, indication = LocalIndication.current) { onOpenCache() }
                )
            }

            CollapsibleSettingsHeader(stringResource(R.string.settings_downloads), isSectionExpanded("downloads"), { toggleSection("downloads") })
            SettingsGroup(visible = isSectionExpanded("downloads")) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_downloads_folder)) },
                    supportingContent = { Text(DownloadStorage.folderDisplayName(context, downloadsFolder)) },
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
                ListItem(
                    headlineContent = {
                        Text(
                            if (downloadsSizeBytes != null) {
                                stringResource(R.string.settings_downloads_size, formatBytes(downloadsSizeBytes))
                            } else {
                                stringResource(R.string.settings_cache_size_unknown)
                            }
                        )
                    },
                    trailingContent = {
                        val clearDownloadsSource = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = onClearDownloads,
                            interactionSource = clearDownloadsSource,
                            modifier = Modifier.focusHighlight(clearDownloadsSource)
                        ) {
                            Text(stringResource(R.string.settings_downloads_clear))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CollapsibleSettingsHeader(stringResource(R.string.settings_backup), isSectionExpanded("backup"), { toggleSection("backup") })
            SettingsGroup(visible = isSectionExpanded("backup"), modifier = Modifier.padding(bottom = 24.dp)) {
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

            // Entry point into the developer-only "add media" scraper - a visible settings entry,
            // password-gated (see DevAccessStore's KDoc for why this is a deterrent, not real
            // security), no longer hidden behind repeated taps on the version string.
            var showDevPasswordEntry by remember { mutableStateOf(false) }
            var showDevPasswordGenerated by remember { mutableStateOf<String?>(null) }
            var devPasswordError by remember { mutableStateOf(false) }

            CollapsibleSettingsHeader(stringResource(R.string.settings_add_media), isSectionExpanded("add_media"), { toggleSection("add_media") })
            SettingsGroup(visible = isSectionExpanded("add_media"), modifier = Modifier.padding(bottom = 24.dp)) {
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

            CollapsibleSettingsHeader(stringResource(R.string.settings_feedback), isSectionExpanded("feedback"), { toggleSection("feedback") })
            SettingsGroup(visible = isSectionExpanded("feedback"), modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_feedback)) },
                    supportingContent = { Text(stringResource(R.string.settings_feedback_description)) },
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

            var showResetConfirm by remember { mutableStateOf(false) }
            CollapsibleSettingsHeader(stringResource(R.string.settings_reset_section), isSectionExpanded("reset"), { toggleSection("reset") })
            SettingsGroup(visible = isSectionExpanded("reset"), modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_reset_to_defaults)) },
                    supportingContent = { Text(stringResource(R.string.settings_reset_to_defaults_description)) },
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

            var showFactoryResetConfirm by remember { mutableStateOf(false) }
            SettingsGroup(visible = isSectionExpanded("reset"), modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_factory_reset)) },
                    supportingContent = { Text(stringResource(R.string.settings_factory_reset_description)) },
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

            Text(
                text = stringResource(R.string.settings_version, com.seance.app.BuildConfig.VERSION_NAME, com.seance.app.BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

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
    }
}

/** Section label shown above a [SettingsGroup] card, matching Material3's grouped-settings idiom. */
@Composable
internal fun SettingsGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

/**
 * Clickable section header that collapses the group(s) below it - per feedback, the settings
 * screen had grown into one long stretched scroll with a dozen-plus sections all expanded at
 * once. Expanded by default (nothing is hidden on first open, only once the user deliberately
 * collapses a section they're not using right now) - collapse state is session-only (not
 * persisted), matching how the rest of this screen's transient UI state already behaves.
 */
@Composable
internal fun CollapsibleSettingsHeader(text: String, expanded: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onToggle)
            .focusHighlight(interactionSource)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/** Groups related settings rows into one visually bounded card, so adjacent unrelated rows don't blend together. */
@Composable
internal fun SettingsGroup(visible: Boolean = true, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible = visible) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            content = content
        )
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
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
}

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f ГБ".format(mb / 1024) else "%.1f МБ".format(mb)
}
