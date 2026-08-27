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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.illusion.app.domain.model.UiMode

/**
 * D-pad/remote focus feedback for the TV Box target. Material3's default ripple only reacts to
 * press/hover, not focus-by-itself, so without this a remote user moving between cards/buttons
 * sees no indication of where focus currently is. Reads focus off the same [interactionSource]
 * already passed to the caller's clickable/selectable - no extra focusable() node, so it doesn't
 * disturb focus traversal order.
 *
 * Explicitly gated on [UiMode.TV] (not just "inert on touch" as originally written) - once this
 * got applied to every field in the SMB source form for the D-pad fix, a phone user tapping into
 * a field to type kept it focused for as long as they were typing, unlike a button's brief
 * click-then-unfocus, so this border+scale stayed up the whole time instead of blipping past like
 * the ripple it was meant to sit alongside - confirmed on-device as a real regression ("огромная
 * рамка" appearing on every text field tap). A phone/tablet already has Material3's own built-in
 * focused-state styling on interactive controls; this is purely the TV-only addition on top.
 */
@Composable
fun Modifier.focusHighlight(
    interactionSource: InteractionSource,
    shape: Shape = RoundedCornerShape(8.dp),
    color: Color = MaterialTheme.colorScheme.primary
): Modifier {
    if (LocalUiMode.current != UiMode.TV) return this
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
/**
 * Details screen floats its back/home buttons as a fixed overlay OUTSIDE the scrollable content
 * column (so they stay put while the fanart/poster/etc. below scroll away) - on a real Android TV
 * this turned out to strand D-pad focus: with nothing else composed above them and no explicit
 * traversal link into the separate scrollable subtree below, DirectionDown from back/home simply
 * had no reachable candidate, leaving the user able to toggle only between those two buttons and
 * never reach the poster, play button, or anything else on the page at all (confirmed on-device:
 * "могу переключаться между стрелочкой назад и домиком, больше ничего"). This explicitly bridges
 * that gap - DirectionDown from the annotated element jumps straight to [target] instead of
 * relying on 2D spatial search to find it across that subtree boundary on its own.
 */
@Composable
fun Modifier.bridgeFocusDown(target: FocusRequester): Modifier =
    this.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
            runCatching { target.requestFocus() }
            true
        } else {
            false
        }
    }

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
