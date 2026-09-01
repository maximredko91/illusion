package com.illusion.app.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * TV/phone-shared wrappers around the handful of Material3 controls reused across every
 * Settings/form screen (Switch, AssistChip, Button, OutlinedButton, IconButton). Originally these
 * branched to the real androidx.tv.material3 component on TV for native D-pad focus scale/glow -
 * reverted (2026-09-01) after repeated on-device confirmation that tv-material's default focused
 * container/content colors aren't fully covered by the border/borderVariant tokens already tied to
 * the app's accent in Theme.kt's tvColorScheme: IconButton, then OutlinedButton, each independently
 * confirmed focusing to a plain near-white filled shape with its own content unreadable inside it.
 * Two different components hitting the same "focus just goes all white" failure by two different
 * internal color roles means it isn't one narrow miss to patch - the hand-rolled [focusHighlight]
 * border avoids the whole class of tv-material default-token-color problems by never filling a
 * background at all, just scaling and outlining in the theme's own primary color. Phone/tablet
 * paths are unchanged from before.
 *
 * D-pad focus/click itself doesn't depend on which of these is used - Compose's own
 * Modifier.clickable (what every plain Material3 control above already builds on) is focusable and
 * responds to DPAD_CENTER/ENTER the same way every other focusHighlight()'d element in the app
 * already does (see the D-pad audit in project memory).
 */
@Composable
fun TvAwareSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, interactionSource = interactionSource, modifier = modifier.focusHighlight(interactionSource))
}

@Composable
fun TvAwareAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    AssistChip(
        onClick = onClick,
        label = label,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.focusHighlight(interactionSource)
    )
}

/** A chip that toggles a "selected" visual state (e.g. the active sort option) - custom colors
 * when selected, since plain AssistChip has no `selected` concept of its own. */
@Composable
fun TvAwareSelectableChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    AssistChip(
        onClick = onClick,
        label = label,
        interactionSource = interactionSource,
        modifier = modifier.focusHighlight(interactionSource),
        colors = if (selected) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primary,
                labelColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            AssistChipDefaults.assistChipColors()
        }
    )
}

@Composable
fun TvAwareButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        modifier = modifier.focusHighlight(interactionSource)
    ) { content() }
}

/**
 * Plain (no tooltip) icon-only button - back arrows, close buttons, etc. See [TooltipIconButton]
 * for the tooltip-carrying variant used in top-bar action rows.
 *
 * scaleOnFocus = false, same reasoning as [TooltipIconButton] - every caller of this one is also
 * an edge/corner-anchored icon (top-bar actions, Details' floating back/home/close), so scaling up
 * from center risks the same "runs off the edge" overflow. Border-only focus indication instead.
 */
@Composable
fun TvAwareIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, interactionSource = interactionSource, modifier = modifier.focusHighlight(interactionSource, scaleOnFocus = false)) { content() }
}

@Composable
fun TvAwareOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.focusHighlight(interactionSource)
    ) { content() }
}
