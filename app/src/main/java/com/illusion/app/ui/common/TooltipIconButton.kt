package com.illusion.app.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `IconButton` with a long-press tooltip showing [label] - the same-looking row of icon-only
 * actions (favorites/history/downloads/settings) repeats identically across Home, Library and
 * Search's top bars, and while each icon is fairly standard, nothing visible names what it does
 * on first use. `contentDescription` alone only reaches a screen reader, not a sighted user
 * hovering/long-pressing to check before tapping.
 *
 * Always the plain Material3 IconButton + hand-rolled [focusHighlight] (border+scale), even on TV
 * - tried androidx.tv.material3.IconButton first (matching PosterCard's tv-material Card), but its
 * default focused-state container/content colors aren't covered by the border/borderVariant tokens
 * already tied to the app's accent in Theme.kt's tvColorScheme, and confirmed on-device: focusing
 * this button paints it a plain white filled circle with the icon itself unreadable inside it -
 * same "focus just goes all white" failure mode that border/borderVariant was fixing for cards,
 * just on a token IconButton doesn't expose the same way. The hand-rolled border avoids the whole
 * class of tv-material default-token-color problems by never filling a background at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        // scaleOnFocus = false - these sit in a fixed top-bar Row (search/favorites/history/
        // downloads/settings), often flush against the real screen edge on TV; scaling up from
        // center pushed the outer half of that growth past the edge for whichever icon was last
        // in the row. Border-only focus indication avoids that.
        IconButton(onClick = onClick, interactionSource = interactionSource, modifier = modifier.focusHighlight(interactionSource, scaleOnFocus = false)) {
            Icon(icon, contentDescription = label)
        }
    }
}
