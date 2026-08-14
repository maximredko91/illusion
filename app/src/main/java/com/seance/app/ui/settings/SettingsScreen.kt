package com.seance.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.seance.app.R
import com.seance.app.data.backup.BackupSource
import com.seance.app.data.download.DownloadStorage
import com.seance.app.data.local.entity.SmbSourceEntity
import com.seance.app.ui.common.reject
import com.seance.app.ui.common.toggle
import com.seance.app.work.PosterPreloadWorker
import com.seance.app.work.WorkScheduler
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
    onClearCache: () -> Unit,
    onToggleChargingRequirement: (Boolean) -> Unit,
    onRescanIntervalChange: (Int) -> Unit,
    onRescanNow: () -> Unit,
    posterCachingEnabled: Flow<Boolean>,
    onSetPosterCachingEnabled: (Boolean) -> Unit,
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
    modifier: Modifier = Modifier
) {
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
    val preloadWorkInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WorkScheduler.POSTER_PRELOAD_WORK_NAME)
    }.collectAsState(initial = emptyList())
    val preloadInfo = preloadWorkInfos.firstOrNull()
    val preloadRunning = preloadInfo?.state == WorkInfo.State.RUNNING || preloadInfo?.state == WorkInfo.State.ENQUEUED
    val cachingEnabled by posterCachingEnabled.collectAsState(initial = true)
    var showDisableCachingConfirm by remember { mutableStateOf(false) }
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
                TextButton(onClick = { onConfirmImportSource(password) }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = onSkipImportSource) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (backupMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissBackupMessage,
            title = { Text(stringResource(R.string.settings_backup)) },
            text = { Text(backupMessage) },
            confirmButton = {
                TextButton(onClick = onDismissBackupMessage) { Text(stringResource(R.string.player_close)) }
            }
        )
    }

    if (showDisableCachingConfirm) {
        AlertDialog(
            onDismissRequest = { showDisableCachingConfirm = false },
            title = { Text(stringResource(R.string.settings_poster_cache_disable_title)) },
            text = { Text(stringResource(R.string.settings_poster_cache_disable_message)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.reject()
                    showDisableCachingConfirm = false
                    onSetPosterCachingEnabled(false)
                }) { Text(stringResource(R.string.settings_poster_cache_disable_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisableCachingConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                actions = {
                    IconButton(onClick = onAddSource) {
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
                        ListItem(
                            headlineContent = { Text(source.displayName) },
                            supportingContent = { Text("\\\\${source.host}\\${source.share}") },
                            trailingContent = {
                                IconButton(onClick = {
                                    haptics.reject()
                                    onDeleteSource(source)
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.settings_delete_source)
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditSource(source) }
                        )
                    }
                }
            }

            SettingsGroupLabel(stringResource(R.string.settings_scan_section))
            SettingsGroup {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_rescan_now)) },
                    supportingContent = { Text(stringResource(R.string.settings_rescan_now_description)) },
                    trailingContent = {
                        OutlinedButton(onClick = onRescanNow, enabled = sources.isNotEmpty()) {
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
                ListItem(
                    headlineContent = {
                        Text(
                            if (cacheSizeBytes != null) {
                                stringResource(R.string.settings_cache_size, formatBytes(cacheSizeBytes))
                            } else {
                                stringResource(R.string.settings_cache_size_unknown)
                            }
                        )
                    },
                    trailingContent = {
                        OutlinedButton(onClick = onClearCache) {
                            Text(stringResource(R.string.settings_cache_clear))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_poster_caching)) },
                    supportingContent = {
                        Text(
                            when {
                                preloadRunning -> {
                                    val processed = preloadInfo?.progress?.getInt(PosterPreloadWorker.KEY_PROCESSED, 0) ?: 0
                                    val total = preloadInfo?.progress?.getInt(PosterPreloadWorker.KEY_TOTAL, 0) ?: 0
                                    if (total > 0) {
                                        stringResource(R.string.settings_poster_preload_progress, processed, total)
                                    } else {
                                        stringResource(R.string.settings_poster_preload_starting)
                                    }
                                }
                                cachingEnabled && preloadInfo?.state == WorkInfo.State.SUCCEEDED ->
                                    stringResource(R.string.settings_poster_preload_done)
                                else -> stringResource(R.string.settings_poster_caching_description)
                            }
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = cachingEnabled,
                            onCheckedChange = { enabled ->
                                haptics.toggle(enabled)
                                if (enabled) {
                                    onSetPosterCachingEnabled(true)
                                } else {
                                    showDisableCachingConfirm = true
                                }
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingsGroupLabel(stringResource(R.string.settings_downloads))
            SettingsGroup {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_downloads_folder)) },
                    supportingContent = { Text(DownloadStorage.folderDisplayName(context, downloadsFolder)) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { context.startActivity(DownloadStorage.openFolderIntent(context, downloadsFolder)) }) {
                                Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.settings_downloads_open_folder))
                            }
                            OutlinedButton(onClick = { folderPicker.launch(DownloadStorage.pickerInitialUri()) }) {
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
                        OutlinedButton(onClick = onClearDownloads) {
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
                            TextButton(onClick = { exportLauncher.launch("seance-backup.json") }) {
                                Text(stringResource(R.string.settings_backup_export))
                            }
                            TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                                Text(stringResource(R.string.settings_backup_import))
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = stringResource(R.string.settings_version, com.seance.app.BuildConfig.VERSION_NAME, com.seance.app.BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Section label shown above a [SettingsGroup] card, matching Material3's grouped-settings idiom. */
@Composable
private fun SettingsGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

/** Groups related settings rows into one visually bounded card, so adjacent unrelated rows don't blend together. */
@Composable
private fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun RescanIntervalMenu(hours: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(rescanLabel(hours))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RESCAN_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(rescanLabel(option)) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    }
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
        OutlinedButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.settings_seek_duration_seconds, seconds))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SEEK_DURATION_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_seek_duration_seconds, option)) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f ГБ".format(mb / 1024) else "%.1f МБ".format(mb)
}
