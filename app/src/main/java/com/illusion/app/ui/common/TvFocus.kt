package com.illusion.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

/**
 * D-pad/remote focus feedback for the TV Box target. Material3's default ripple only reacts to
 * press/hover, not focus-by-itself, so without this a remote user moving between cards/buttons
 * sees no indication of where focus currently is. Reads focus off the same [interactionSource]
 * already passed to the caller's clickable/selectable - no extra focusable() node, so it doesn't
 * disturb focus traversal order. Inert on touch-only devices: focus only moves there via an
 * explicit click, which is already visualized by the ripple.
 */
@Composable
fun Modifier.focusHighlight(
    interactionSource: InteractionSource,
    shape: Shape = RoundedCornerShape(8.dp),
    color: Color = MaterialTheme.colorScheme.primary
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "tvFocusScale")
    return this
        .scale(scale)
        .then(
            if (isFocused) Modifier.border(3.dp, color, shape) else Modifier
        )
}

/**
 * A stacked column of text fields (SMB source form, etc.) was found completely unusable by
 * D-pad on a real Android TV: once a field is actually focused for editing, up/down are consumed
 * by the text field itself to move the text cursor between lines rather than propagating up to
 * Compose's own focus search - there's no remote-only way to "tab out" of a field otherwise, so
 * once in, you're stuck. Left/right are left alone (still moves the cursor within the current
 * line, which is the only way to edit position on a remote with no keyboard) - only up/down are
 * reinterpreted as "move to the next/previous focusable", matching how a vertically-stacked form
 * should behave on a D-pad in the first place.
 */
@Composable
fun Modifier.dpadFieldNavigation(): Modifier {
    val focusManager = LocalFocusManager.current
    return this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
            Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
            else -> false
        }
    }
}
