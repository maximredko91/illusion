package com.seance.app.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.seance.app.domain.model.UiMode

/**
 * The user's explicitly-chosen interface mode (asked once at onboarding, changeable in Settings -
 * no runtime `UiModeManager`/leanback detection). Provided once near the NavHost root so any
 * screen can branch on it (D-pad focus visuals, TV-oriented layout) without threading it through
 * every composable's parameter list.
 */
val LocalUiMode = compositionLocalOf { UiMode.PHONE }
