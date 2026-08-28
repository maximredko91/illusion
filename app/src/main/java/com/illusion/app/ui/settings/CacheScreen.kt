package com.illusion.app.ui.settings

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.illusion.app.R
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.reject
import com.illusion.app.work.PosterPreloadWorker
import com.illusion.app.work.WorkScheduler
import kotlinx.coroutines.flow.Flow

/** Split out of [SettingsScreen] into its own destination so the main Settings list doesn't have to carry cache-management UI (size, clear, poster-caching toggle + preload progress) inline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen(
    cacheSizeBytes: Long?,
    onRefreshCacheSize: () -> Unit,
    onClearCache: () -> Unit,
    posterCacheSizeBytes: Long?,
    onClearPosterCache: () -> Unit,
    fanartCacheSizeBytes: Long?,
    onClearFanartCache: () -> Unit,
    imageCacheLimitMb: Flow<Int>,
    onSetImageCacheLimitMb: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cacheLimitMb by imageCacheLimitMb.collectAsState(initial = com.illusion.app.data.settings.IMAGE_CACHE_LIMIT_MB_DEFAULT)
    val haptics = LocalHapticFeedback.current
    // Clearing the cache used to fire immediately (onClearCache called straight from the button) -
    // a destructive action deserves a confirm step of its own.
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showClearPosterCacheConfirm by remember { mutableStateOf(false) }
    var showClearFanartCacheConfirm by remember { mutableStateOf(false) }

    val preloadWorkInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WorkScheduler.POSTER_PRELOAD_WORK_NAME)
    }.collectAsState(initial = emptyList())
    val preloadInfo = preloadWorkInfos.firstOrNull()
    val preloadRunning = preloadInfo?.state == WorkInfo.State.RUNNING || preloadInfo?.state == WorkInfo.State.ENQUEUED

    LaunchedEffect(Unit) { onRefreshCacheSize() }

    if (showClearPosterCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearPosterCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_poster_cache_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_poster_cache_clear_confirm_message)) },
            confirmButton = {
                val confirmSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        haptics.reject()
                        showClearPosterCacheConfirm = false
                        onClearPosterCache()
                    },
                    interactionSource = confirmSource,
                    modifier = Modifier.focusHighlight(confirmSource)
                ) { Text(stringResource(R.string.settings_cache_clear)) }
            },
            dismissButton = {
                val cancelSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showClearPosterCacheConfirm = false },
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

    if (showClearFanartCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearFanartCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_fanart_cache_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_fanart_cache_clear_confirm_message)) },
            confirmButton = {
                val confirmSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        haptics.reject()
                        showClearFanartCacheConfirm = false
                        onClearFanartCache()
                    },
                    interactionSource = confirmSource,
                    modifier = Modifier.focusHighlight(confirmSource)
                ) { Text(stringResource(R.string.settings_cache_clear)) }
            },
            dismissButton = {
                val cancelSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showClearFanartCacheConfirm = false },
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
                windowInsets = com.illusion.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.settings_cache)) },
                navigationIcon = {
                    com.illusion.app.ui.common.TvAwareIconButton(onClick = onBack) {
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
                        com.illusion.app.ui.common.TvAwareOutlinedButton(onClick = { showClearCacheConfirm = true }) {
                            Text(stringResource(R.string.settings_cache_clear))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsDivider()
                ListItem(
                    headlineContent = {
                        Text(
                            if (posterCacheSizeBytes != null) {
                                stringResource(R.string.settings_poster_cache_size, formatBytes(posterCacheSizeBytes))
                            } else {
                                stringResource(R.string.settings_cache_size_unknown)
                            }
                        )
                    },
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
                                preloadInfo?.state == WorkInfo.State.SUCCEEDED -> stringResource(R.string.settings_poster_preload_done)
                                else -> stringResource(R.string.settings_poster_cache_description)
                            }
                        )
                    },
                    trailingContent = {
                        com.illusion.app.ui.common.TvAwareOutlinedButton(onClick = { showClearPosterCacheConfirm = true }) {
                            Text(stringResource(R.string.settings_cache_clear))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsDivider()
                ListItem(
                    headlineContent = {
                        Text(
                            if (fanartCacheSizeBytes != null) {
                                stringResource(R.string.settings_fanart_cache_size, formatBytes(fanartCacheSizeBytes))
                            } else {
                                stringResource(R.string.settings_cache_size_unknown)
                            }
                        )
                    },
                    supportingContent = { Text(stringResource(R.string.settings_fanart_cache_description)) },
                    trailingContent = {
                        com.illusion.app.ui.common.TvAwareOutlinedButton(onClick = { showClearFanartCacheConfirm = true }) {
                            Text(stringResource(R.string.settings_cache_clear))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_image_cache_limit)) },
                    supportingContent = { Text(stringResource(R.string.settings_image_cache_limit_description)) },
                    trailingContent = { ImageCacheLimitMenu(cacheLimitMb, onSetImageCacheLimitMb) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ImageCacheLimitMenu(currentMb: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(256, 512, 1024, 2048)
    androidx.compose.foundation.layout.Box {
        val triggerSource = remember { MutableInteractionSource() }
        // A fixed min width, not just wrap-content: "256 МБ"/"512 МБ" vs "1 ГБ"/"2 ГБ" are different
        // pixel widths, and this button sits in a ListItem's trailingContent next to a long
        // supportingContent description - letting the button's width vary with the label reflowed
        // that description onto a different number of lines on every selection, visibly "jumping".
        com.illusion.app.ui.common.TvAwareOutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.widthIn(min = 96.dp)
        ) {
            Text(imageCacheLimitLabel(currentMb))
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val itemSource = remember { MutableInteractionSource() }
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(imageCacheLimitLabel(option)) },
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

private fun imageCacheLimitLabel(mb: Int): String = if (mb >= 1024) "${mb / 1024} ГБ" else "$mb МБ"
