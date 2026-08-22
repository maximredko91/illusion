package com.seance.app.ui.common

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * A gentler fling than the platform default - flicking the Library grid felt too sharp/abrupt
 * (fast flicks decelerated almost immediately, more like a hard stop than a glide). Damping the
 * initial fling velocity before feeding it into the same spline-based decay curve the platform
 * uses keeps the deceleration curve itself untouched (so it still feels native), just softer at
 * the top end.
 */
@Composable
fun rememberSmoothFlingBehavior(dampingFactor: Float = 0.7f): FlingBehavior {
    val flingSpec = rememberSplineBasedDecay<Float>()
    return remember(flingSpec, dampingFactor) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                var lastValue = 0f
                var remainingVelocity = initialVelocity
                AnimationState(initialValue = 0f, initialVelocity = initialVelocity * dampingFactor)
                    .animateDecay(flingSpec) {
                        val delta = value - lastValue
                        val consumed = scrollBy(delta)
                        lastValue = value
                        remainingVelocity = velocity
                        if (abs(delta - consumed) > 0.5f) cancelAnimation()
                    }
                return remainingVelocity
            }
        }
    }
}
