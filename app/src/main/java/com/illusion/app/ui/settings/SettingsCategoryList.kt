package com.illusion.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import kotlinx.coroutines.flow.Flow
import androidx.compose.foundation.layout.ColumnScope

/** Корневой список категорий настроек (когда ни одна категория не открыта). */
@Composable
internal fun ColumnScope.SettingsCategoryList(
    cacheSizeBytes: Long?,
    showDeveloperEntry: Boolean,
    onOpenCategory: (String) -> Unit,
    onOpenCache: () -> Unit
) {
    // Двенадцать пунктов шли одним списком, где «Кеш», «Загрузки» и «Библиотека»
    // были разбросаны между «Режимом экрана» и «Производительностью». Сгруппированы
    // по смыслу: где лежат файлы, как выглядит, как играет, всё остальное.
    SettingsSectionLabel(stringResource(R.string.settings_section_group_library))
    CategoryRow(
        title = stringResource(R.string.settings_smb_sources),
        description = stringResource(R.string.settings_category_smb_sources_description),
        icon = Icons.Default.Dns,
        onClick = { onOpenCategory("smb_sources") }
    )
    SettingsDivider(indented = true)
    CategoryRow(
        title = stringResource(R.string.settings_library_section),
        description = stringResource(R.string.settings_category_library_description),
        icon = Icons.Default.VideoLibrary,
        onClick = { onOpenCategory("library") }
    )
    SettingsDivider(indented = true)
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
    SettingsDivider(indented = true)
    CategoryRow(
        title = stringResource(R.string.settings_downloads),
        description = stringResource(R.string.settings_category_downloads_description),
        icon = Icons.Default.Download,
        onClick = { onOpenCategory("downloads") }
    )
    SettingsDivider(indented = true)
    CategoryRow(
        title = stringResource(R.string.settings_backup),
        description = stringResource(R.string.settings_category_backup_description),
        icon = Icons.Default.Backup,
        onClick = { onOpenCategory("backup") }
    )

    SettingsSectionLabel(stringResource(R.string.settings_section_group_appearance))
    CategoryRow(
        title = stringResource(R.string.settings_ui_mode_section),
        description = stringResource(R.string.settings_category_ui_mode_description),
        icon = Icons.Default.Palette,
        onClick = { onOpenCategory("ui_mode") }
    )
    SettingsDivider(indented = true)
    CategoryRow(
        title = stringResource(R.string.settings_screen_mode_section),
        description = stringResource(R.string.settings_category_screen_mode_description),
        icon = Icons.Default.Devices,
        onClick = { onOpenCategory("screen_mode") }
    )
    SettingsDivider(indented = true)
    CategoryRow(
        title = stringResource(R.string.settings_performance_section),
        description = stringResource(R.string.settings_category_performance_description),
        icon = Icons.Default.Speed,
        onClick = { onOpenCategory("performance") }
    )

    SettingsSectionLabel(stringResource(R.string.settings_section_group_playback))
    CategoryRow(
        title = stringResource(R.string.settings_player_section),
        description = stringResource(R.string.settings_category_player_description),
        icon = Icons.Default.PlayCircle,
        onClick = { onOpenCategory("player") }
    )

    SettingsSectionLabel(stringResource(R.string.settings_section_group_other))
    CategoryRow(
        title = stringResource(R.string.settings_feedback),
        description = stringResource(R.string.settings_feedback_description),
        icon = Icons.Default.Feedback,
        onClick = { onOpenCategory("feedback") }
    )
    SettingsDivider(indented = true)
    CategoryRow(
        title = stringResource(R.string.settings_reset_section),
        description = stringResource(R.string.settings_category_reset_description),
        icon = Icons.Default.RestartAlt,
        onClick = { onOpenCategory("reset") }
    )
    SettingsDivider(indented = true)
    CategoryRow(
        title = stringResource(R.string.settings_about_section),
        description = stringResource(R.string.settings_version, com.illusion.app.BuildConfig.VERSION_NAME),
        icon = Icons.Default.Info,
        onClick = { onOpenCategory("about") }
    )
    // Developer-only flow stays visually separate from ordinary settings and
    // remains hidden on TV, where its own screen isn't D-pad enabled.
    if (showDeveloperEntry) {
        SettingsSectionLabel(stringResource(R.string.settings_section_group_developer))
        CategoryRow(
            title = stringResource(R.string.settings_add_media),
            description = stringResource(R.string.settings_add_media_description),
            icon = Icons.Default.LibraryAdd,
            onClick = { onOpenCategory("add_media") }
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}
