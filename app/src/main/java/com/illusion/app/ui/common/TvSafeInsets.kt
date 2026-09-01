package com.illusion.app.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * TV overscan-safe margin, as real [WindowInsets] rather than the flat outer [androidx.compose.foundation.layout.padding]
 * IllusionNavHost used to wrap the whole nav graph in. That older approach shrank the ENTIRE UI
 * (background, NavigationRail, every screen) into a smaller centered box - confirmed as a real bug
 * per feedback ("экран просто становится меньше, а не подстраивается под размер экрана"), since
 * `Modifier.padding()` reduces available layout space for everything inside it, it doesn't rescale
 * content to fit. A real safe-area inset instead lets backgrounds/containers keep filling the whole
 * physical display - only the actual content each screen places (text, list items, icons) gets
 * nudged in from the true edge, exactly like [androidx.compose.foundation.layout.WindowInsets.safeDrawing]
 * already does for status/navigation bars, which this composes with via [WindowInsets.union] rather
 * than replacing.
 */
val LocalTvSafeMarginInsets = compositionLocalOf<WindowInsets> { WindowInsets(0, 0, 0, 0) }

/**
 * Same margin as [LocalTvSafeMarginInsets], as a plain [Dp] for non-Scaffold consumers that don't
 * want a full 4-sided [WindowInsets] object - e.g. the TV NavigationRail, which only needs to push
 * away from whichever single side is the true screen edge (not the inner edge touching content).
 */
val LocalTvSafeMarginDp = compositionLocalOf<Dp> { Dp(0f) }

/**
 * Every screen's own [androidx.compose.material3.Scaffold] should pass this (instead of its own ad
 * hoc `WindowInsets(0, 0, 0, 0)` or Scaffold's `WindowInsets.safeDrawing` default) as
 * `contentWindowInsets` - unions [LocalTvSafeMarginInsets] on top of whichever real system-bar
 * insets that screen was already asking for (usually none, if it manages status/nav bar padding
 * itself elsewhere - matching [safeDrawing]'s zero-if-unset semantics for the common case), so
 * every screen consistently gets pushed off a real TV's cropped edge without needing its own
 * bespoke margin logic.
 */
@Composable
fun tvSafeContentWindowInsets(base: WindowInsets = WindowInsets(0, 0, 0, 0)): WindowInsets =
    base.union(LocalTvSafeMarginInsets.current)

/** Same margin as [LocalTvSafeMarginInsets], applied directly to a non-Scaffold composable (e.g. NavigationRail) that isn't already reading Scaffold's own innerPadding. */
fun Modifier.tvSafeMarginPadding(insets: WindowInsets): Modifier = this.windowInsetsPadding(insets)

/** Builds [WindowInsets] worth of margin from a 0-10% figure and the real screen size in dp - see [com.illusion.app.data.settings.SettingsRepository.tvOverscanMarginPercent]'s own KDoc for why this is user-adjustable rather than a guessed constant. */
fun tvOverscanWindowInsets(marginPercent: Int, screenWidthDp: Dp, screenHeightDp: Dp, density: androidx.compose.ui.unit.Density): WindowInsets {
    if (marginPercent <= 0) return WindowInsets(0, 0, 0, 0)
    val fraction = marginPercent / 100f
    val horizontalPx = with(density) { (screenWidthDp * fraction).toPx().toInt() }
    val verticalPx = with(density) { (screenHeightDp * fraction).toPx().toInt() }
    return WindowInsets(left = horizontalPx, top = verticalPx, right = horizontalPx, bottom = verticalPx)
}
