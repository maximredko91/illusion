package com.seance.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seance.app.domain.model.UiMode

/**
 * Poster card min-width for grids (`GridCells.Adaptive`) and carousels. 120dp is sized for
 * phone arm's-length viewing; on a TV Box a couch-distance "10-foot UI" needs meaningfully
 * bigger cards, not just more of the same small ones - `GridCells.Adaptive` already recomputes
 * column count for the wider screen on its own, so leaving minSize fixed just produced far more
 * small cards on a TV instead of fewer, bigger ones.
 */
@Composable
fun posterCardMinWidth(): Dp = if (LocalUiMode.current == UiMode.TV) 176.dp else 120.dp
