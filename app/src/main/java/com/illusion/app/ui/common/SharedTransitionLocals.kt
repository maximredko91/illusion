package com.illusion.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf

/**
 * Navigation-Compose scopes [AnimatedVisibilityScope] per destination and doesn't thread it (or
 * the shared [SharedTransitionScope] wrapping the whole NavHost) through arbitrary nested
 * composables on its own, so screens/cards that want to participate in a poster shared-element
 * transition read it from here instead of taking it as an explicit parameter everywhere.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * A single shared shimmer clock for the whole app, read by every [shimmer] modifier instance
 * instead of each one running its own [androidx.compose.animation.core.rememberInfiniteTransition].
 * With one clock per card, a grid of several simultaneously-loading posters shimmered out of phase
 * with each other - each card's independent timer started at a slightly different composition
 * moment, so the highlight band sat at a different position on each card at any given instant,
 * reading as a wave sweeping diagonally across the grid rather than a uniform pulse.
 *
 * Holds the [State] object itself, not the unwrapped float - the provider passes it through
 * without ever reading `.value`, so only the leaf `.current.value` read inside [shimmer] recomposes
 * per animation frame. Unwrapping it one level up (`by`-delegating to a plain Float before handing
 * it to [androidx.compose.runtime.CompositionLocalProvider]) made *that* call site's whole
 * enclosing scope re-run on every frame instead - confirmed via logcat timestamps to be a full
 * screen-tree recomposition storm at ~90-120Hz, which is what actually read as a diagonal
 * flash/wipe on tab switches, not the nav transition or image loading.
 */
val LocalShimmerProgress = compositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/** Stable shared-element key for a poster/fanart transitioning between a grid card and its details hero image. */
fun posterTransitionKey(stableId: String) = "poster-$stableId"

/**
 * sharedElement()'s default boundsTransform is an independent spring - it settles on its own
 * schedule, out of step with the surrounding NavHost slide/fade (a fixed 350ms tween, see
 * IllusionNavGraph's NAV_TRANSITION_MS). The two fighting was what actually read as the poster
 * transition moving smoothly and then visibly pausing partway through: the tween finishes at
 * 350ms and stops, while the spring is still catching up (or vice versa). Using the same tween
 * here keeps the poster's bounds moving in lockstep with the rest of the transition.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val PosterBoundsTransform = BoundsTransform { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
