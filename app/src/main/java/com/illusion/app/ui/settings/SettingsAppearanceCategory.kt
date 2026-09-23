package com.illusion.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
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
import com.illusion.app.ui.common.TvAwareSwitch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.toggle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ColumnScope

/** Категория «Интерфейс»: тема, переключатели оформления, акцентный цвет и значок приложения. */
@Composable
internal fun ColumnScope.SettingsAppearanceCategory(
    isTvMode: Boolean,
    effectiveDarkTheme: Boolean,
    currentThemeMode: com.illusion.app.domain.model.ThemeMode,
    onThemeModeChange: (com.illusion.app.domain.model.ThemeMode) -> Unit,
    hapticsOn: Boolean,
    onHapticsEnabledChange: (Boolean) -> Unit,
    predictiveBackOn: Boolean,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    glassEffectOn: Boolean,
    onGlassEffectEnabledChange: (Boolean) -> Unit,
    posterAccentOn: Boolean,
    onPosterAccentEnabledChange: (Boolean) -> Unit,
    parallaxOn: Boolean,
    onParallaxEnabledChange: (Boolean) -> Unit,
    currentAccentColor: com.illusion.app.domain.model.AccentColor,
    onAccentColorChange: (com.illusion.app.domain.model.AccentColor) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    // PackageManager's own component-enabled state (see IconVariantManager) is already the
    // persistent source of truth - no need to duplicate it into SettingsRepository/DataStore.
    var currentAppIcon by remember { mutableStateOf(com.illusion.app.data.appicon.IconVariantManager.current(context)) }
    SettingsGroup {
        SettingsActionCard(title = stringResource(R.string.settings_theme_mode)) {
            ThemeModeMenu(currentThemeMode, onThemeModeChange, modifier = Modifier.fillMaxWidth())
        }
        // Both are touch-only concepts - haptics needs a vibration motor no
        // remote/TV box has, and predictive back is Android's edge-swipe
        // gesture preview, which doesn't exist without a touchscreen to swipe
        // on. Dead, confusing toggles on TV rather than hidden clutter.
        if (!isTvMode) {
        SettingsDivider()
        // Economical performance mode already force-mutes real haptic output
        // (IllusionNavHost's gated HapticFeedback - see PerformanceMode's own
        // KDoc) regardless of this switch's own state - leaving the switch
        // itself interactive read as "this setting didn't actually do
        // anything" since flipping it produced no felt difference while
        // economical was active. Disabled outright instead, same fix as the
        // player's sharpen toggle.
        val hapticsDisabledByPerformanceMode = com.illusion.app.ui.common.LocalEconomicalMode.current
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_haptics)) },
            supportingContent = {
                Text(
                    if (hapticsDisabledByPerformanceMode) {
                        stringResource(R.string.settings_haptics_disabled_by_performance_mode)
                    } else {
                        stringResource(R.string.settings_haptics_description)
                    }
                )
            },
            trailingContent = {
                TvAwareSwitch(
                    checked = hapticsOn,
                    enabled = !hapticsDisabledByPerformanceMode,
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
        SettingsDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_glass_effect)) },
            supportingContent = { Text(stringResource(R.string.settings_glass_effect_description)) },
            trailingContent = {
                TvAwareSwitch(
                    checked = glassEffectOn,
                    onCheckedChange = onGlassEffectEnabledChange
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        )
        // Украшения по отдельности: у каждого в подписи сказано, что оно даёт
        // и чем за это платит - решение принимается на месте, без догадок.
        SettingsDivider()
        Text(
            stringResource(R.string.settings_visuals_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        VisualEffectToggle(
            title = stringResource(R.string.settings_poster_accent),
            description = stringResource(R.string.settings_poster_accent_description),
            checked = posterAccentOn,
            onCheckedChange = onPosterAccentEnabledChange
        )
        SettingsDivider()
        VisualEffectToggle(
            title = stringResource(R.string.settings_parallax),
            description = stringResource(R.string.settings_parallax_description),
            checked = parallaxOn,
            onCheckedChange = onParallaxEnabledChange
        )
        }
    }

    // Accent color merged in here (was its own top-level category) per user
    // feedback - it's another interface-level appearance choice, same as the
    // theme/haptics/predictive-back switches above. Phone/TV mode used to live
    // in this same screen too but moved out to its own "Тип устройства" category -
    // it's a structural/functional choice (which whole layout the app uses),
    // not an appearance one like everything else here.
    var accentColorExpanded by remember { mutableStateOf(false) }
    SettingsGroup(modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)
        ) {
            Text(
                stringResource(R.string.settings_accent_color),
                style = MaterialTheme.typography.titleSmall
            )
            // Текущее значение рядом с заголовком - чтобы свёрнутая секция говорила,
            // что выбрано, как это делает кнопка выбора темы выше.
            Text(
                accentColorLabel(currentAccentColor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            com.illusion.app.ui.common.TvAwareIconButton(onClick = { accentColorExpanded = !accentColorExpanded }) {
                Icon(
                    if (accentColorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (accentColorExpanded) R.string.settings_collapse else R.string.settings_expand
                    )
                )
            }
        }
        // FlowRow, not a plain Row - 7 swatches at 40dp + spacing (~352dp) can
        // exceed a narrow phone's available width once the Card's own padding
        // is subtracted, and a plain Row doesn't wrap. Collapsed to maxLines = 1
        // by default (per feedback - one row of icons, expandable) rather than
        // always showing every swatch at once, which pushed this whole category
        // panel quite tall for something most visits don't need to touch.
        androidx.compose.foundation.layout.FlowRow(
            maxLines = if (accentColorExpanded) Int.MAX_VALUE else 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // В свёрнутом виде FlowRow показывал первые три цвета по порядку enum, и
            // выбранного среди них могло не быть вовсе - текущий акцент был не виден,
            // пока не развернёшь список. Выбранный всегда идёт первым.
            val orderedAccents = if (accentColorExpanded) {
                com.illusion.app.domain.model.AccentColor.entries.toList()
            } else {
                com.illusion.app.domain.model.AccentColor.entries.sortedByDescending { it == currentAccentColor }
            }
            orderedAccents.forEach { color ->
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
    var appIconExpanded by remember { mutableStateOf(false) }
    SettingsGroup(modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)
        ) {
            Text(
                stringResource(R.string.settings_app_icon),
                style = MaterialTheme.typography.titleSmall
            )
            // Текущее значение рядом с заголовком - как у акцентного цвета выше.
            Text(
                appIconLabel(currentAppIcon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            com.illusion.app.ui.common.TvAwareIconButton(onClick = { appIconExpanded = !appIconExpanded }) {
                Icon(
                    if (appIconExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (appIconExpanded) R.string.settings_collapse else R.string.settings_expand
                    )
                )
            }
        }
        androidx.compose.foundation.layout.FlowRow(
            maxLines = if (appIconExpanded) Int.MAX_VALUE else 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Свёрнутый список начинается с выбранного значка, как и у акцентного цвета.
            val orderedIcons = if (appIconExpanded) {
                com.illusion.app.domain.model.AppIcon.entries.toList()
            } else {
                com.illusion.app.domain.model.AppIcon.entries.sortedByDescending { it == currentAppIcon }
            }
            orderedIcons.forEach { icon ->
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
