package com.seance.app.ui.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.seance.app.ui.common.focusHighlight
import com.seance.app.ui.common.reject
import com.seance.app.ui.common.toggle
import com.seance.app.work.PosterPreloadWorker
import com.seance.app.work.WorkScheduler
import kotlinx.coroutines.flow.Flow

/** Split out of [SettingsScreen] into its own destination so the main Settings list doesn't have to carry cache-management UI (size, clear, poster-caching toggle + preload progress) inline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen(
    cacheSizeBytes: Long?,
    onRefreshCacheSize: () -> Unit,
    onClearCache: () -> Unit,
    posterCachingEnabled: Flow<Boolean>,
    onSetPosterCachingEnabled: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cachingEnabled by posterCachingEnabled.collectAsState(initial = true)
    val haptics = LocalHapticFeedback.current
    var showDisableCachingConfirm by remember { mutableStateOf(false) }
    // Clearing the cache used to fire immediately (onClearCache called straight from the button) -
    // inconsistent with the toggle right below it, which confirms a much less destructive action.
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    val preloadWorkInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WorkScheduler.POSTER_PRELOAD_WORK_NAME)
    }.collectAsState(initial = emptyList())
    val preloadInfo = preloadWorkInfos.firstOrNull()
    val preloadRunning = preloadInfo?.state == WorkInfo.State.RUNNING || preloadInfo?.state == WorkInfo.State.ENQUEUED

    LaunchedEffect(Unit) { onRefreshCacheSize() }

    if (showDisableCachingConfirm) {
        AlertDialog(
            onDismissRequest = { showDisableCachingConfirm = false },
            title = { Text(stringResource(R.string.settings_poster_cache_disable_title)) },
            text = { Text(stringResource(R.string.settings_poster_cache_disable_message)) },
            confirmButton = {
                val confirmSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        haptics.reject()
                        showDisableCachingConfirm = false
                        onSetPosterCachingEnabled(false)
                    },
                    interactionSource = confirmSource,
                    modifier = Modifier.focusHighlight(confirmSource)
                ) { Text(stringResource(R.string.settings_poster_cache_disable_confirm)) }
            },
            dismissButton = {
                val cancelSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showDisableCachingConfirm = false },
                    interactionSource = cancelSource,
                    modifier = Modifier.focusHighlight(cancelSource)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_cache_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_cache_clear_confirm_message)) },
            confirmButton = {
                val confirmSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        haptics.reject()
                        showClearCacheConfirm = false
                        onClearCache()
                    },
                    interactionSource = confirmSource,
                    modifier = Modifier.focusHighlight(confirmSource)
                ) { Text(stringResource(R.string.settings_cache_clear)) }
            },
            dismissButton = {
                val cancelSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showClearCacheConfirm = false },
                    interactionSource = cancelSource,
                    modifier = Modifier.focusHighlight(cancelSource)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = com.seance.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.settings_cache)) },
                navigationIcon = {
                    val backSource = remember { MutableInteractionSource() }
                    IconButton(onClick = onBack, interactionSource = backSource, modifier = Modifier.focusHighlight(backSource)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
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
            SettingsGroup(modifier = Modifier.padding(top = 12.dp)) {
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
                        val clearCacheSource = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = { showClearCacheConfirm = true },
                            interactionSource = clearCacheSource,
                            modifier = Modifier.focusHighlight(clearCacheSource)
                        ) {
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
        }
    }
}
