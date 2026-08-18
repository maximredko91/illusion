package com.seance.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
    seekDurationSeconds: Flow<Int>,
    onSeekDurationChange: (Int) -> Unit,
    cacheSizeBytes: Long?,
    onRefreshCacheSize: () -> Unit,
    onOpenCache: () -> Unit,
    uiMode: Flow<UiMode?>,
    onUiModeChange: (UiMode) -> Unit,
    defaultSortOrder: Flow<SortOrder>,
    onDefaultSortOrderChange: (SortOrder) -> Unit,
    hapticsEnabled: Flow<Boolean>,
    onHapticsEnabledChange: (Boolean) -> Unit,
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
    hasDevPassword: () -> Boolean,
    onGenerateDevPassword: () -> String,
    onVerifyDevPassword: (String) -> Boolean,
    isDevAccessRemembered: () -> Boolean,
    onRememberDevAccess: () -> Unit,
    onForgetDevAccess: () -> Unit,
    onDevAccessGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUiMode by uiMode.collectAsState(initial = null)
    val currentDefaultSortOrder by defaultSortOrder.collectAsState(initial = SortOrder.DATE_ADDED)
    val hapticsOn by hapticsEnabled.collectAsState(initial = true)
    val chargingOnly by requireChargingForHeavyTasks.collectAsState(initial = true)
    val rescanHours by rescanIntervalHours.collectAsState(initial = 24)
    val seekSeconds by seekDurationSeconds.collectAsState(initial = 10)
    val downloadsFolder by downloadsFolderUri.collectAsState(initial = null)
    val context = LocalContext.current
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
                title = { Text(stringResource(R.string.settings_title)) },
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
            SettingsGroupLabel(stringResource(R.string.settings_smb_sources))
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
                                        onDeleteSource(source)
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

            SettingsGroupLabel(stringResource(R.string.settings_ui_mode_section))
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
            }

            SettingsGroupLabel(stringResource(R.string.settings_library_section))
            SettingsGroup(modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_default_sort_order)) },
                    trailingContent = { DefaultSortOrderMenu(currentDefaultSortOrder, onDefaultSortOrderChange) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingsGroupLabel(stringResource(R.string.settings_scan_section))
            SettingsGroup {
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

            SettingsGroupLabel(stringResource(R.string.settings_player_section))
            SettingsGroup {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_seek_duration)) },
                    trailingContent = { SeekDurationMenu(seekSeconds, onSeekDurationChange) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingsGroupLabel(stringResource(R.string.settings_cache))
            SettingsGroup {
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

            SettingsGroupLabel(stringResource(R.string.settings_downloads))
            SettingsGroup {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_downloads_folder)) },
                    supportingContent = { Text(DownloadStorage.folderDisplayName(context, downloadsFolder)) },
                    trailingContent = {
                        Row {
                            val openFolderSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = { context.startActivity(DownloadStorage.openFolderIntent(context, downloadsFolder)) },
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

            SettingsGroupLabel(stringResource(R.string.settings_backup))
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

            // Entry point into the developer-only "add media" scraper - a visible settings entry,
            // password-gated (see DevAccessStore's KDoc for why this is a deterrent, not real
            // security), no longer hidden behind repeated taps on the version string.
            var showDevPasswordEntry by remember { mutableStateOf(false) }
            var showDevPasswordGenerated by remember { mutableStateOf<String?>(null) }
            var devPasswordError by remember { mutableStateOf(false) }

            SettingsGroupLabel(stringResource(R.string.settings_add_media))
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

/** Groups related settings rows into one visually bounded card, so adjacent unrelated rows don't blend together. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = content
    )
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
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

private val SEEK_DURATION_OPTIONS = listOf(5, 10, 15, 20, 25, 30)

@Composable
private fun SeekDurationMenu(seconds: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val triggerSource = remember { MutableInteractionSource() }
        OutlinedButton(onClick = { expanded = true }, interactionSource = triggerSource, modifier = Modifier.focusHighlight(triggerSource)) {
            Text(stringResource(R.string.settings_seek_duration_seconds, seconds))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SEEK_DURATION_OPTIONS.forEach { option ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_seek_duration_seconds, option)) },
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

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f ГБ".format(mb / 1024) else "%.1f МБ".format(mb)
}
