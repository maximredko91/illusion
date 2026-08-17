package com.seance.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp

/** Animated horizontal sweep used as a loading placeholder for images and skeleton rows - driven by [LocalShimmerProgress] so every shimmering card on screen pulses in sync. */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val progress = LocalShimmerProgress.current.value
    val peak = (1f - kotlin.math.abs(progress - 0.5f) * 2f).coerceIn(0f, 1f)
    val color = lerp(base, highlight, peak)
    return this.background(
        Brush.linearGradient(
            colors = listOf(base, color, base),
            start = Offset(progress * 600f - 300f, 0f),
            end = Offset(progress * 600f, 0f)
        )
    )
}
