package com.illusion.app.domain.model

/**
 * One switch that governs several independent visual-cost features at once (shimmer loading
 * animation, Crossfade/AnimatedVisibility transition durations, decorative gradient backgrounds,
 * the GPU sharpen shader, haptics) rather than a pile of separate toggles - see
 * [com.illusion.app.ui.common.LocalEconomicalMode]'s own KDoc for the full list of what actually
 * changes. AUTO resolves once per app process via [com.illusion.app.data.settings.DevicePerformance] -
 * MAXIMUM/ECONOMICAL always win over that guess when picked explicitly.
 */
enum class PerformanceMode {
    AUTO,
    MAXIMUM,
    ECONOMICAL
}
