package com.illusion.app.ui.player

import androidx.annotation.StringRes
import com.illusion.app.R
import com.illusion.app.work.IntroDetectWorker

/**
 * Progress of the automatic (audio-fingerprint) intro search - see `data/intro/IntroDetector`.
 *
 * Its own StateFlow rather than a [PlayerUiState] field for the same reason as [CastUiState]: that
 * state object is rebuilt from scratch on every episode change.
 */
data class IntroDetectUiState(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    /** Set once a search has succeeded this session - the markers themselves live in Room. */
    val foundEndMs: Long? = null,
    /** One of IntroDetectWorker's ERROR_* constants. */
    val error: String? = null
)

@StringRes
fun introDetectErrorText(error: String): Int = when (error) {
    IntroDetectWorker.ERROR_NOT_FOUND -> R.string.player_detect_intro_not_found
    IntroDetectWorker.ERROR_NO_REFERENCE -> R.string.player_detect_intro_no_reference
    IntroDetectWorker.ERROR_NOT_SERIES -> R.string.player_detect_intro_not_series
    else -> R.string.player_detect_intro_failed
}
