package com.seance.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp

/** Animated diagonal sweep (top-left to bottom-right) used as a loading placeholder for images and skeleton rows - driven by [LocalShimmerProgress] so every shimmering card on screen pulses in sync. Band width scales with each element's own size (via [drawWithCache]) instead of a fixed pixel span, so it looks right on both small grid cells and large hero images. */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val progressState = LocalShimmerProgress.current
    return this.drawWithCache {
        val diagonal = size.width + size.height
        val bandWidth = diagonal / 2f
        onDrawBehind {
            val progress = progressState.value
            val peak = (1f - kotlin.math.abs(progress - 0.5f) * 2f).coerceIn(0f, 1f)
            val color = lerp(base, highlight, peak)
            val center = progress * (diagonal + bandWidth) - bandWidth / 2f
            val start = center - bandWidth / 2f
            val end = center + bandWidth / 2f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(base, color, base),
                    start = Offset(start, start),
                    end = Offset(end, end)
                )
            )
        }
    }
}
