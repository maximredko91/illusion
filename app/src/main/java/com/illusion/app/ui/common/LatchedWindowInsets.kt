package com.illusion.app.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay

/**
 * `WindowInsets.statusBars`/`.navigationBars` (Compose's ambient snapshot) were observed to
 * transiently report 0 on this device even with no Dialog involved - the same genuine Compose/OS
 * insets-redispatch race already fixed once for the Details screen's status-bar bleed-through
 * (see that fix's KDoc). It also hits the main tab screens' own `TopAppBar`/`NavigationBar` - both
 * paint their background across whatever inset they're given, so a transiently-zero inset there
 * leaves a plain unpainted (background-colored, effectively black against their lighter tonal
 * surface) strip exactly where the status/navigation bar sits. Cross-checking against the real,
 * current View-system insets (`ViewCompat.getRootWindowInsets`, queried fresh every recomposition)
 * and latching onto the largest value either source has ever reported fixes it the same way - the
 * real bar height doesn't shrink mid-session in practice, so a regression to a smaller/zero value
 * is always the race, never a legitimate change.
 */
/**
 * Returns the *state object* itself, not its current value - the caller must read `.intValue` (or
 * `.value`) directly inside its own composable body. A first version of this returned a plain
 * `Int`, which reads the underlying state only inside *this* function's own scope (where `return
 * tick` evaluates the delegate) - the caller receives an ordinary value with no subscription of
 * its own, so it never recomposed when the delayed tick actually fired. Handing back the state
 * object and letting the caller dereference it is what makes the caller's own scope the one that
 * gets invalidated.
 */
@Composable
private fun rememberRecheckTick(): androidx.compose.runtime.MutableIntState {
    val tick = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(200)
        tick.intValue++
    }
    return tick
}

/** Latched replacement for `TopAppBarDefaults.windowInsets` - pass to `TopAppBar(windowInsets = ...)`. */
@Composable
fun rememberLatchedStatusBarsInsets(): WindowInsets {
    val density = LocalDensity.current
    val view = LocalView.current
    var latchedDp by remember { mutableStateOf(0.dp) }
    val ambientDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val viewDp = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.statusBars())
        ?.top
        ?.let { with(density) { it.toDp() } }
        ?: 0.dp
    rememberRecheckTick().intValue
    val liveDp = maxOf(ambientDp, viewDp)
    if (liveDp > latchedDp) latchedDp = liveDp
    return WindowInsets(top = latchedDp)
}

/** Latched replacement for `NavigationBarDefaults.windowInsets` - pass to `NavigationBar(windowInsets = ...)`. */
@Composable
fun rememberLatchedNavigationBarsInsets(): WindowInsets {
    val density = LocalDensity.current
    val view = LocalView.current
    var latchedDp by remember { mutableStateOf(0.dp) }
    val ambientDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val viewDp = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.navigationBars())
        ?.bottom
        ?.let { with(density) { it.toDp() } }
        ?: 0.dp
    rememberRecheckTick().intValue
    val liveDp = maxOf(ambientDp, viewDp)
    if (liveDp > latchedDp) latchedDp = liveDp
    return WindowInsets(bottom = latchedDp)
}
