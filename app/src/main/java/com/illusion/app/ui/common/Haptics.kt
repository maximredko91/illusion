package com.illusion.app.ui.common

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** Light tick for taps that open something (poster cards, list rows). */
fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.ContextClick)

/** For a toggle/switch flipping on or off. */
fun HapticFeedback.toggle(on: Boolean) =
    performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

/** Stepping through a discrete set of options (sort order, segmented tabs, filters). */
fun HapticFeedback.segmentTick() = performHapticFeedback(HapticFeedbackType.SegmentTick)

/** A destructive action being confirmed (delete). */
fun HapticFeedback.reject() = performHapticFeedback(HapticFeedbackType.Reject)
