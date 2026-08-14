package com.seance.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * Navigation-Compose scopes [AnimatedVisibilityScope] per destination and doesn't thread it (or
 * the shared [SharedTransitionScope] wrapping the whole NavHost) through arbitrary nested
 * composables on its own, so screens/cards that want to participate in a poster shared-element
 * transition read it from here instead of taking it as an explicit parameter everywhere.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Stable shared-element key for a poster/fanart transitioning between a grid card and its details hero image. */
fun posterTransitionKey(stableId: String) = "poster-$stableId"
