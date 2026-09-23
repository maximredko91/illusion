package com.illusion.app.ui.settings

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.ui.common.TvAwareButton
import com.illusion.app.ui.common.TvAwareSwitch
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.common.reject
import com.illusion.app.ui.common.segmentTick
import androidx.compose.foundation.layout.ColumnScope

/** Категория «Источники SMB»: список подключённых шар. Удаление подтверждает сам [SettingsScreen]. */
@Composable
internal fun ColumnScope.SettingsSourcesCategory(
    sources: List<SmbSourceEntity>,
    sourcesMissingPassword: Set<Long>,
    onAddSource: () -> Unit,
    onEditSource: (SmbSourceEntity) -> Unit,
    onSourceEnabledChange: (SmbSourceEntity, Boolean) -> Unit,
    onRequestDelete: (SmbSourceEntity) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    SettingsGroup {
        if (sources.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.settings_no_sources))
                Spacer(Modifier.height(12.dp))
                TvAwareButton(onClick = onAddSource, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.settings_add_source_action))
                }
            }
        } else {
            sources.forEachIndexed { index, source ->
                if (index > 0) SettingsDivider()
                val rowSource = remember { MutableInteractionSource() }
                ListItem(
                    headlineContent = { Text(source.displayName) },
                    supportingContent = {
                        Column {
                            val root = source.rootPath.trim('\\', '/')
                            Text(
                                if (root.isBlank()) "\\\\${source.host}\\${source.share}"
                                else "\\\\${source.host}\\${source.share}\\$root"
                            )
                            if (source.id in sourcesMissingPassword) {
                                Text(
                                    stringResource(R.string.settings_source_missing_password),
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    stringResource(
                                        if (source.enabled) R.string.settings_source_connected
                                        else R.string.settings_source_disabled
                                    ),
                                    color = if (source.enabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
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
                                    onRequestDelete(source)
                                },
                                interactionSource = deleteSource,
                                modifier = Modifier.focusHighlight(deleteSource)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.settings_delete_source)
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
