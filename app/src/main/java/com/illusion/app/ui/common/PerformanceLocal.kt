package com.illusion.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * True when [com.illusion.app.domain.model.PerformanceMode.ECONOMICAL] is in effect (either
 * picked directly, or resolved from AUTO via [com.illusion.app.data.settings.DevicePerformance] -
 * see the provider in IllusionNavHost's composition root). Read directly by the handful of
 * genuinely expensive per-frame effects that have no other natural hook (the shimmer loading
 * animation - see [shimmer]'s own KDoc - and the decorative gradient backgrounds), and via
 * [economicalDurationMs] by every Crossfade/AnimatedVisibility transition. Defaults to false so
 * any composable read outside the real provider (previews, tests) stays at full quality.
 */
val LocalEconomicalMode = compositionLocalOf { false }

/**
 * [normalMs] in Maximum/Auto-on-capable-hardware, floored to [economicalMs] in Economical mode -
 * a Crossfade/AnimatedVisibility still needs a nonzero duration to actually cross-fade rather than
 * pop with no animation at all (jarring on its own), so this doesn't go all the way to 0.
 */
@Composable
fun economicalDurationMs(normalMs: Int, economicalMs: Int = 80): Int =
    if (LocalEconomicalMode.current) economicalMs else normalMs
