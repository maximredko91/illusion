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
import com.illusion.app.domain.model.UiMode

/**
 * `IconButton` with a long-press tooltip showing [label] - the same-looking row of icon-only
 * actions (favorites/history/downloads/settings) repeats identically across Home, Library and
 * Search's top bars, and while each icon is fairly standard, nothing visible names what it does
 * on first use. `contentDescription` alone only reaches a screen reader, not a sighted user
 * hovering/long-pressing to check before tapping.
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
        // TV gets androidx.tv.material3.IconButton (native focus scale/glow, same reasoning as
        // PosterCard's tv-material Card) instead of a plain Material3 IconButton with the
        // hand-rolled focusHighlight() modifier. Only verify on the real TV Box with a real
        // remote - see TvAwareControls.kt's KDoc for why touch-testing this on a phone looks
        // completely broken (expected, not a bug).
        if (LocalUiMode.current == UiMode.TV) {
            androidx.tv.material3.IconButton(onClick = onClick, interactionSource = interactionSource, modifier = modifier) {
                Icon(icon, contentDescription = label)
            }
        } else {
            IconButton(onClick = onClick, interactionSource = interactionSource, modifier = modifier.focusHighlight(interactionSource)) {
                Icon(icon, contentDescription = label)
            }
        }
    }
}
