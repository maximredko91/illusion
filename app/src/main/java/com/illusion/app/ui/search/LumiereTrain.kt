package com.illusion.app.ui.search

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.illusion.app.R
import com.illusion.app.ui.common.LocalEconomicalMode
import kotlinx.coroutines.delay

/**
 * Easter egg: searching for the Lumières (or their train) sends a locomotive across the search
 * screen, with a caption naming the 1895 film audiences are said to have fled from. Deliberately
 * undocumented outside this file and CLAUDE.md.
 *
 * Drawn over the screen but takes no pointer input, so the search field and results underneath
 * stay fully usable while it passes. The train is plain emoji - it runs right to left because
 * that's the way the locomotive glyph faces. With animations off (economical mode, or the system
 * setting) it simply stands in the middle for a moment instead.
 */
@Composable
internal fun BoxScope.LumiereTrain(query: String) {
    val summoned = remember(query) { isLumiereQuery(query) }
    val economical = LocalEconomicalMode.current
    val progress = remember { Animatable(0f) }
    var running by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(summoned) {
        if (!summoned) return@LaunchedEffect
        moving = !economical && ValueAnimator.areAnimatorsEnabled()
        progress.snapTo(if (moving) 0f else 0.5f)
        running = true
        if (moving) progress.animateTo(1f, tween(TRAVEL_MS, easing = LinearEasing)) else delay(STANDING_MS)
        running = false
    }
    if (!running) return

    BoxWithConstraints(Modifier.matchParentSize()) {
        val width = constraints.maxWidth
        Text(
            "🚂🚃🚃🚃",
            fontSize = 56.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.CenterStart)
                // From just past the right edge to a full screen-width past the left one: the
                // train is narrower than the screen, so that takes it out of sight on both ends.
                .offset { IntOffset((width - progress.value * 2f * width).toInt(), 0) }
        )
        Text(
            stringResource(R.string.search_lumiere_caption),
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .graphicsLayer {
                    // In over the first tenth of the run, out over the last.
                    alpha = if (moving) (minOf(progress.value, 1f - progress.value) / 0.1f).coerceIn(0f, 1f) else 1f
                }
                .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/** Whether [query] is one of the spellings that summon the train - whole query, not a substring. */
internal fun isLumiereQuery(query: String): Boolean =
    query.trim().lowercase().replace('ё', 'е').replace('è', 'e') in LUMIERE_QUERIES

private val LUMIERE_QUERIES = setOf(
    "люмьер", "люмьеры", "братья люмьер", "прибытие поезда", "lumiere", "lumiere brothers"
)

private const val TRAVEL_MS = 4500
private const val STANDING_MS = 2500L
