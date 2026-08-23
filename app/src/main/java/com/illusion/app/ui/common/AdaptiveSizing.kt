package com.illusion.app.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.illusion.app.domain.model.UiMode

/**
 * Poster card width for the Home carousels (a `LazyRow` sizes each item to this fixed width
 * directly, it isn't fed to `GridCells.Adaptive` anywhere anymore - see [posterGridColumns] for
 * grids). 120dp is sized for phone arm's-length viewing; on a TV Box a couch-distance "10-foot
 * UI" needs meaningfully bigger cards.
 */
@Composable
fun posterCardMinWidth(): Dp = if (LocalUiMode.current == UiMode.TV) 176.dp else 120.dp

/**
 * Column strategy for poster grids (Library/Search/Favorites/Person). `GridCells.Fixed(2)` was
 * tried first (to guarantee "2 columns, 2 rows visible" in portrait) and rejected - a *fixed*
 * column count doesn't adapt to orientation, so in landscape the same 2 columns stretched each
 * card far wider than a poster should ever be, and cropping the poster image down to that
 * much-too-wide box mangled it into an unrecognizable strip. `Adaptive` recomputes column count
 * from whatever width is actually available, so portrait gets ~2 columns and landscape gets more
 * (narrower, still poster-shaped) columns instead of 2 stretched-out ones.
 *
 * Phone landscape uses a smaller minSize than portrait (narrower columns, so a shorter
 * aspect-ratio-locked poster height) - landscape has much less vertical room than portrait to
 * begin with, and the same minSize as portrait produced cards tall enough that the title/year
 * text below the poster fell outside the visible single row entirely.
 */
@Composable
fun posterGridColumns(): GridCells {
    if (LocalUiMode.current == UiMode.TV) return GridCells.Adaptive(minSize = 176.dp)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    return GridCells.Adaptive(minSize = if (isLandscape) 100.dp else 150.dp)
}
