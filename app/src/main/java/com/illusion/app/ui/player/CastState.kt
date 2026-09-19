package com.illusion.app.ui.player

import com.illusion.app.data.cast.DlnaDevice

/**
 * Everything the player UI needs to know about casting to a TV over DLNA.
 *
 * Deliberately a separate StateFlow rather than another field on [PlayerUiState]: `playItem`/
 * `playTrailerItem` rebuild that state object from scratch on every episode change, and anything
 * living there has to be manually carried across (the decoder-mode setting was silently reset that
 * exact way, see CLAUDE.md). A cast session outlives those transitions by nature - it isn't tied
 * to whatever the local player happens to be doing.
 */
data class CastUiState(
    /** The picker dialog is open - devices may still be loading. */
    val isPickerOpen: Boolean = false,
    val isSearching: Boolean = false,
    val devices: List<DlnaDevice> = emptyList(),
    val googleDevices: List<com.illusion.app.data.cast.GoogleCastDevice> = emptyList(),
    val googleDeviceName: String? = null,
    /** Non-null while a file is playing on a renderer. */
    val device: DlnaDevice? = null,
    val isConnecting: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null
) {
    val isCasting: Boolean get() = device != null || googleDeviceName != null
    val deviceName: String get() = googleDeviceName ?: device?.displayName.orEmpty()
}
