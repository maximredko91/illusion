package com.seance.app.ui.player

import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Small cross-cutting signal between [com.seance.app.MainActivity] (which owns the actual
 * enterPictureInPictureMode call) and the player screen (the only place that knows whether
 * PiP makes sense right now and what aspect ratio the video is).
 */
object PipController {
    var isPlayerActive by mutableStateOf(false)
    var isInPipMode by mutableStateOf(false)
    var aspectRatio by mutableStateOf(Rational(16, 9))

    /**
     * Set by [com.seance.app.ui.player.PlayerScreen] while it's on screen, invoked by
     * [com.seance.app.MainActivity.onStop] when the activity stops WHILE still marked as being in
     * PiP - on some OEM skins (confirmed on this device's MIUI build), tapping the PiP window's own
     * close (X) button does not reliably finish() the activity the way stock Android does, so
     * ExoPlayer's ViewModel never got onCleared() and kept playing audio with no window showing for
     * it at all. onStop() firing while isInPipMode is still true only happens when the PiP surface
     * itself has gone away (normal PiP keeps the activity STARTED), so it's a safe, OEM-independent
     * signal that the window was actually closed rather than just entered.
     */
    var onPipClosed: (() -> Unit)? = null
}
