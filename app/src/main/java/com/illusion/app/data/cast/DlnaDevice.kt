package com.illusion.app.data.cast

/**
 * One UPnP MediaRenderer found on the local network - a TV, a receiver, a DLNA app on another box.
 *
 * [controlUrl] is the absolute URL of the device's AVTransport service (the one that takes
 * SetAVTransportURI/Play/Pause/Seek), already resolved against the device description's own base
 * URL, so [DlnaController] never has to deal with the relative form devices actually publish.
 * [renderingControlUrl] is the same for the RenderingControl service (volume/mute), which is
 * optional in the spec and genuinely missing on some renderers - null then, and the UI hides its
 * volume slider rather than showing a control that silently does nothing.
 *
 * [udn] is the device's own stable identifier - used as the list key, since friendly names are not
 * guaranteed unique (two identical TVs out of the box).
 */
data class DlnaDevice(
    val udn: String,
    val friendlyName: String,
    val manufacturer: String?,
    val modelName: String?,
    val controlUrl: String,
    val renderingControlUrl: String? = null
) {
    /** What the device list shows: "Samsung TV" alone is ambiguous between two identical sets. */
    val displayName: String
        get() = listOfNotNull(friendlyName.takeIf { it.isNotBlank() }, modelName?.takeIf { it != friendlyName })
            .joinToString(" · ")
            .ifBlank { udn }
}
