package com.illusion.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Backup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.domain.model.SortOrder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ListItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.colorResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
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
import com.illusion.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.data.local.entity.SmbSourceEntity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.TvAwareOutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.TvAwareSwitch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.focusHighlight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.segmentTick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.library.sortLabel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.MenuShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ColumnScope

/** Title shown in the TopAppBar once a category row has been tapped - keys match [SettingsScreen]'s `category` values. */
@Composable
internal fun categoryTitle(key: String): String = when (key) {
    "smb_sources" -> stringResource(R.string.settings_smb_sources)
    "ui_mode" -> stringResource(R.string.settings_ui_mode_section)
    "screen_mode" -> stringResource(R.string.settings_screen_mode_section)
    "performance" -> stringResource(R.string.settings_performance_section)
    "library" -> stringResource(R.string.settings_library_section)
    "player" -> stringResource(R.string.settings_player_section)
    "downloads" -> stringResource(R.string.settings_downloads)
    "backup" -> stringResource(R.string.settings_backup)
    "add_media" -> stringResource(R.string.settings_add_media)
    "feedback" -> stringResource(R.string.settings_feedback)
    "reset" -> stringResource(R.string.settings_reset_section)
    "about" -> stringResource(R.string.settings_about_section)
    "libraries" -> stringResource(R.string.settings_about_libraries)
    else -> stringResource(R.string.settings_title)
}

/** One radio-selectable Phone/TV option on "Тип устройства" - same leading tonal-icon-container look as [CategoryRow], own card per option like "Сброс"'s two cards, plus a one-line description (neither existed before, per feedback). */
@Composable
internal fun ScreenModeOptionRow(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Column {
                Text(title)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = {
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
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                Color.Transparent
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .focusHighlight(interactionSource)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
    )
}

/** One row on the top-level settings screen - tapping it navigates into that category's own full-screen content (or, for Cache, straight to its own real screen). */
@Composable
internal fun CategoryRow(title: String, description: String?, icon: ImageVector, onClick: () -> Unit) {
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
internal fun SettingsDivider(indented: Boolean = false) {
    androidx.compose.material3.HorizontalDivider(
        // Rows with a leading icon get an inset divider (starts where the text starts), so the
        // line doesn't cut across the icon column - the usual Material list treatment.
        modifier = Modifier.padding(start = if (indented) 72.dp else 16.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** Переключатель украшения: название, честная подпись «что даёт и чем платит», сам тумблер. */
@Composable
internal fun VisualEffectToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { TvAwareSwitch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Small all-caps-ish label above a run of related category rows. */
@Composable
internal fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
internal fun accentColorLabel(color: com.illusion.app.domain.model.AccentColor): String = when (color) {
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
internal fun appIconLabel(icon: com.illusion.app.domain.model.AppIcon): String = when (icon) {
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

internal fun appIconForegroundRes(icon: com.illusion.app.domain.model.AppIcon): Int = when (icon) {
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
internal fun AppIconSwatch(
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
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
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
        // Выбор отмечался по-разному в соседних секциях: у цвета - галочка, у значка - только
        // рамка. Теперь везде рамка акцентом плюс галочка; у значка она в углу, чтобы не
        // закрывать сам рисунок.
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(16.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
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
internal fun AccentColorSwatch(
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
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
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
internal fun DefaultSortOrderMenu(current: SortOrder, onChange: (SortOrder) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // Ширина выпадающего списка привязана к ширине самой кнопки: по умолчанию
    // DropdownMenu сжимается по самому длинному пункту и выглядит как огрызок посреди
    // широкой капсулы, а не как её раскрытие.
    val menuDensity = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier.onSizeChanged {
            menuWidth = with(menuDensity) { it.width.toDp() }
        }
    ) {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(sortLabel(current))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MenuShape,
            modifier = if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier
        ) {
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

internal val TV_OVERSCAN_MARGIN_OPTIONS = listOf(0, 2, 4, 6, 8, 10)

@Composable
internal fun TvOverscanMarginMenu(percent: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    // Ширина выпадающего списка привязана к ширине самой кнопки: по умолчанию
    // DropdownMenu сжимается по самому длинному пункту и выглядит как огрызок посреди
    // широкой капсулы, а не как её раскрытие.
    val menuDensity = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier.onSizeChanged {
            menuWidth = with(menuDensity) { it.width.toDp() }
        }
    ) {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (percent <= 0) stringResource(R.string.settings_tv_overscan_margin_off) else "$percent%")
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MenuShape,
            modifier = if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier
        ) {
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
internal fun ThemeModeMenu(current: com.illusion.app.domain.model.ThemeMode, onChange: (com.illusion.app.domain.model.ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // Ширина выпадающего списка привязана к ширине самой кнопки: по умолчанию
    // DropdownMenu сжимается по самому длинному пункту и выглядит как огрызок посреди
    // широкой капсулы, а не как её раскрытие.
    val menuDensity = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier.onSizeChanged {
            menuWidth = with(menuDensity) { it.width.toDp() }
        }
    ) {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(themeModeLabel(current))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MenuShape,
            modifier = if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier
        ) {
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
internal fun themeModeLabel(mode: com.illusion.app.domain.model.ThemeMode): String = when (mode) {
    com.illusion.app.domain.model.ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_mode_system)
    com.illusion.app.domain.model.ThemeMode.LIGHT -> stringResource(R.string.settings_theme_mode_light)
    com.illusion.app.domain.model.ThemeMode.DARK -> stringResource(R.string.settings_theme_mode_dark)
    com.illusion.app.domain.model.ThemeMode.BLACK -> stringResource(R.string.settings_theme_mode_black)
}

@Composable
internal fun PlayerModeMenu(current: com.illusion.app.domain.model.PlayerMode, onChange: (com.illusion.app.domain.model.PlayerMode) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // Ширина выпадающего списка привязана к ширине самой кнопки: по умолчанию
    // DropdownMenu сжимается по самому длинному пункту и выглядит как огрызок посреди
    // широкой капсулы, а не как её раскрытие.
    val menuDensity = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier.onSizeChanged {
            menuWidth = with(menuDensity) { it.width.toDp() }
        }
    ) {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(playerModeLabel(current))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MenuShape,
            modifier = if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier
        ) {
            com.illusion.app.domain.model.PlayerMode.entries.forEach { mode ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(playerModeLabel(mode)) },
                    trailingIcon = {
                        if (mode == current) Icon(Icons.Default.Check, contentDescription = null)
                    },
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
internal fun PlayerBufferSizeMenu(current: com.illusion.app.domain.model.PlayerBufferSize, onChange: (com.illusion.app.domain.model.PlayerBufferSize) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // Ширина выпадающего списка привязана к ширине самой кнопки: по умолчанию
    // DropdownMenu сжимается по самому длинному пункту и выглядит как огрызок посреди
    // широкой капсулы, а не как её раскрытие.
    val menuDensity = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier.onSizeChanged {
            menuWidth = with(menuDensity) { it.width.toDp() }
        }
    ) {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(playerBufferSizeLabel(current))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MenuShape,
            modifier = if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier
        ) {
            com.illusion.app.domain.model.PlayerBufferSize.entries.forEach { size ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(playerBufferSizeLabel(size)) },
                    trailingIcon = {
                        if (size == current) Icon(Icons.Default.Check, contentDescription = null)
                    },
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
internal fun playerBufferSizeLabel(size: com.illusion.app.domain.model.PlayerBufferSize): String = when (size) {
    com.illusion.app.domain.model.PlayerBufferSize.AUTO -> stringResource(R.string.settings_player_buffer_size_auto)
    com.illusion.app.domain.model.PlayerBufferSize.INCREASED -> stringResource(R.string.settings_player_buffer_size_increased)
    com.illusion.app.domain.model.PlayerBufferSize.MAXIMUM -> stringResource(R.string.settings_player_buffer_size_maximum)
}

@Composable
internal fun PerformanceModeOptionRow(
    mode: com.illusion.app.domain.model.PerformanceMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .focusHighlight(interactionSource)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptics.segmentTick()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(performanceModeLabel(mode), style = MaterialTheme.typography.titleMedium)
            Text(
                performanceModeDescription(mode),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = {
                haptics.segmentTick()
                onClick()
            }
        )
    }
}

@Composable
internal fun performanceModeDescription(mode: com.illusion.app.domain.model.PerformanceMode): String = when (mode) {
    com.illusion.app.domain.model.PerformanceMode.AUTO -> stringResource(R.string.settings_performance_mode_auto_description)
    com.illusion.app.domain.model.PerformanceMode.MAXIMUM -> stringResource(R.string.settings_performance_mode_maximum_description)
    com.illusion.app.domain.model.PerformanceMode.ECONOMICAL -> stringResource(R.string.settings_performance_mode_economical_description)
}

@Composable
internal fun performanceModeLabel(mode: com.illusion.app.domain.model.PerformanceMode): String = when (mode) {
    com.illusion.app.domain.model.PerformanceMode.AUTO -> stringResource(R.string.settings_performance_mode_auto)
    com.illusion.app.domain.model.PerformanceMode.MAXIMUM -> stringResource(R.string.settings_performance_mode_maximum)
    com.illusion.app.domain.model.PerformanceMode.ECONOMICAL -> stringResource(R.string.settings_performance_mode_economical)
}

@Composable
internal fun UpdateSourceMenu(current: com.illusion.app.domain.model.UpdateSource, onChange: (com.illusion.app.domain.model.UpdateSource) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // Ширина выпадающего списка привязана к ширине самой кнопки: по умолчанию
    // DropdownMenu сжимается по самому длинному пункту и выглядит как огрызок посреди
    // широкой капсулы, а не как её раскрытие.
    val menuDensity = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier.onSizeChanged {
            menuWidth = with(menuDensity) { it.width.toDp() }
        }
    ) {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(updateSourceLabel(current))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MenuShape,
            modifier = if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier
        ) {
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
internal fun updateSourceLabel(source: com.illusion.app.domain.model.UpdateSource): String = when (source) {
    com.illusion.app.domain.model.UpdateSource.GITHUB -> stringResource(R.string.settings_update_source_github)
    com.illusion.app.domain.model.UpdateSource.LOCAL -> stringResource(R.string.settings_update_source_local)
}

@Composable
internal fun LocalUpdateSourceMenu(
    sources: List<SmbSourceEntity>,
    currentId: Long?,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val current = sources.firstOrNull { it.id == currentId }
    // Ширина выпадающего списка привязана к ширине самой кнопки: по умолчанию
    // DropdownMenu сжимается по самому длинному пункту и выглядит как огрызок посреди
    // широкой капсулы, а не как её раскрытие.
    val menuDensity = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier.onSizeChanged {
            menuWidth = with(menuDensity) { it.width.toDp() }
        }
    ) {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(current?.displayName ?: stringResource(R.string.settings_local_update_source_pick))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MenuShape,
            modifier = if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier
        ) {
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

internal val UPDATE_CHECK_INTERVAL_OPTIONS = listOf(0, 24, 168, 720)

internal fun updateCheckIntervalLabel(hours: Int): String = when {
    hours <= 0 -> "Выключено"
    hours < 168 -> "Раз в сутки"
    hours < 720 -> "Раз в неделю"
    else -> "Раз в месяц"
}

@Composable
internal fun UpdateCheckIntervalMenu(currentHours: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // Ширина выпадающего списка привязана к ширине самой кнопки: по умолчанию
    // DropdownMenu сжимается по самому длинному пункту и выглядит как огрызок посреди
    // широкой капсулы, а не как её раскрытие.
    val menuDensity = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier.onSizeChanged {
            menuWidth = with(menuDensity) { it.width.toDp() }
        }
    ) {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(updateCheckIntervalLabel(currentHours))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MenuShape,
            modifier = if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier
        ) {
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
internal fun ExternalPlayerAppMenu(currentPackage: String?, onChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    // Scanned once per composition rather than observed live - installed apps don't change while
    // this menu is open, and re-querying PackageManager on every recomposition would be wasteful.
    val apps = remember { com.illusion.app.data.player.InstalledPlayerApps.list(context) }
    val systemLabel = stringResource(R.string.settings_external_player_app_system)
    val currentLabel = apps.find { it.packageName == currentPackage }?.label ?: systemLabel
    // Ширина выпадающего списка привязана к ширине самой кнопки: по умолчанию
    // DropdownMenu сжимается по самому длинному пункту и выглядит как огрызок посреди
    // широкой капсулы, а не как её раскрытие.
    val menuDensity = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier.onSizeChanged {
            menuWidth = with(menuDensity) { it.width.toDp() }
        }
    ) {
        val triggerSource = remember { MutableInteractionSource() }
        TvAwareOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(currentLabel)
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MenuShape,
            modifier = if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier
        ) {
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
internal fun playerModeLabel(mode: com.illusion.app.domain.model.PlayerMode): String = when (mode) {
    com.illusion.app.domain.model.PlayerMode.INTERNAL -> stringResource(R.string.settings_player_mode_internal)
    com.illusion.app.domain.model.PlayerMode.EXTERNAL -> stringResource(R.string.settings_player_mode_external)
    com.illusion.app.domain.model.PlayerMode.ASK -> stringResource(R.string.settings_player_mode_ask)
}

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f ГБ".format(mb / 1024) else "%.1f МБ".format(mb)
}
