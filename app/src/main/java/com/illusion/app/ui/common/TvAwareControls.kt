@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.illusion.app.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.illusion.app.domain.model.UiMode

/**
 * TV/phone-branching wrappers around the handful of Material3 controls reused across every
 * Settings/form screen (Switch, AssistChip, Button, OutlinedButton) - same reasoning as
 * PosterCard/TooltipIconButton: TV gets the real androidx.tv.material3 component (native D-pad
 * focus scale/glow/border, matching Google's own TV design guidance) instead of a plain Material3
 * control plus the hand-rolled focusHighlight() modifier. Phone/tablet paths are byte-for-byte
 * unchanged. Centralized here (rather than branching at every call site) so each control only
 * needs verifying once - see project memory "ATV UI rewrite plan".
 *
 * IMPORTANT: touch-testing TV mode on a phone screen (no real D-pad) makes every one of these
 * controls appear completely untappable - verified via javap that tv-material's click handling
 * (`SurfaceClickableUtilsKt.tvClickable` -> `SurfaceImplKt.handleDPadEnter`) is gated on the
 * element already being D-pad-focused, not plain touch dispatch. This is NOT a bug to fix here -
 * it's expected on the real target (Xiaomi TV Box, no touchscreen, real remote). Verify TV mode
 * changes on the actual TV Box hardware, not by tapping TV mode on a phone.
 */
@Composable
fun TvAwareSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalUiMode.current == UiMode.TV) {
        androidx.tv.material3.Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, modifier = modifier)
    } else {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, modifier = modifier)
    }
}

@Composable
fun TvAwareAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (LocalUiMode.current == UiMode.TV) {
        androidx.tv.material3.AssistChip(onClick = onClick, modifier = modifier, enabled = enabled, content = label)
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        AssistChip(
            onClick = onClick,
            label = label,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = modifier.focusHighlight(interactionSource)
        )
    }
}

/** A chip that toggles a "selected" visual state (e.g. the active sort option) - phone uses a
 * plain AssistChip with custom colors when selected, TV uses tv-material's real FilterChip
 * (its `selected` param is exactly this concept, unlike AssistChip which has none). */
@Composable
fun TvAwareSelectableChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalUiMode.current == UiMode.TV) {
        androidx.tv.material3.FilterChip(selected = selected, onClick = onClick, modifier = modifier, content = label)
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        AssistChip(
            onClick = onClick,
            label = label,
            interactionSource = interactionSource,
            modifier = modifier.focusHighlight(interactionSource),
            colors = if (selected) {
                androidx.compose.material3.AssistChipDefaults.assistChipColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    labelColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            } else {
                androidx.compose.material3.AssistChipDefaults.assistChipColors()
            }
        )
    }
}

@Composable
fun TvAwareButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.material3.ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit
) {
    if (LocalUiMode.current == UiMode.TV) {
        // tv-material's Button internally centers its own Row (Arrangement.Center, verified via
        // javap) but that Row wraps tightly to content width - it's the OUTER Surface/Box that
        // decides where that tight Row sits when the button itself is stretched wider (e.g. via
        // Modifier.weight(1f) in a Row of buttons), and that outer alignment defaults to the
        // start, not the center. Confirmed on-device: "Смотреть"/"Скачать" rendered as icon+text
        // hugging the left edge of a much wider pill. Forcing our own fillMaxWidth() + Center Row
        // here overrides that regardless of tv-material's own default.
        androidx.tv.material3.Button(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = contentPadding) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                content()
            }
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Button(
            onClick = onClick,
            enabled = enabled,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            modifier = modifier.focusHighlight(interactionSource)
        ) { content() }
    }
}

/** Plain (no tooltip) icon-only button - back arrows, close buttons, etc. See [TooltipIconButton] for the tooltip-carrying variant used in top-bar action rows. */
@Composable
fun TvAwareIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalUiMode.current == UiMode.TV) {
        androidx.tv.material3.IconButton(onClick = onClick, modifier = modifier) { content() }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        IconButton(onClick = onClick, interactionSource = interactionSource, modifier = modifier.focusHighlight(interactionSource)) { content() }
    }
}

@Composable
fun TvAwareOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (LocalUiMode.current == UiMode.TV) {
        // See TvAwareButton's own comment - tv-material's outer Surface/Box doesn't center a
        // stretched button's content on its own.
        androidx.tv.material3.OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                content()
            }
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = modifier.focusHighlight(interactionSource)
        ) { content() }
    }
}
