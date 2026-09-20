package com.illusion.app.data.cast

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Where the renderer is in the file right now, as reported by AVTransport's GetPositionInfo. */
data class DlnaPosition(val positionMs: Long, val durationMs: Long)

/** What the renderer says it's doing - PLAYING / PAUSED_PLAYBACK / STOPPED / TRANSITIONING / ... */
data class DlnaTransportState(val state: String) {
    val isPlaying: Boolean get() = state.equals("PLAYING", ignoreCase = true)
    val isStopped: Boolean get() = state.equals("STOPPED", ignoreCase = true) ||
        state.equals("NO_MEDIA_PRESENT", ignoreCase = true)
}

/**
 * Drives one [DlnaDevice]'s AVTransport service over SOAP - the whole "play this on the TV" API
 * this app needs: hand it a URL, start/pause/stop it, seek, ask where it is.
 *
 * Every call is a plain HTTP POST with a hand-built envelope. UPnP's SOAP dialect is small and
 * fixed (InstanceID is always 0 for a single-stream renderer), so a generic SOAP stack would be
 * pure overhead here - see [DlnaDiscovery]'s KDoc for the same reasoning about UPnP libraries.
 */
class DlnaController(private val device: DlnaDevice) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Points the renderer at [url] and starts it. [title] and [mimeType] ride along as DIDL-Lite
     * metadata: a renderer is allowed to reject or mislabel a stream handed over with no metadata
     * at all, and it's what puts a real title on the TV's own on-screen display instead of a raw
     * URL.
     */
    suspend fun playUrl(url: String, title: String, mimeType: String) {
        val metadata = didlLite(url, title, mimeType)
        soap(
            "SetAVTransportURI",
            "<InstanceID>0</InstanceID>" +
                "<CurrentURI>${escapeXml(url)}</CurrentURI>" +
                "<CurrentURIMetaData>${escapeXml(metadata)}</CurrentURIMetaData>"
        )
        play()
    }

    suspend fun play() = soap("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>").let { }

    suspend fun pause() = soap("Pause", "<InstanceID>0</InstanceID>").let { }

    suspend fun stop() = soap("Stop", "<InstanceID>0</InstanceID>").let { }

    /** REL_TIME seek - the unit every renderer supports; ABS_TIME is optional and widely missing. */
    suspend fun seekTo(positionMs: Long) {
        soap("Seek", "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>${formatDuration(positionMs)}</Target>")
    }

    suspend fun position(): DlnaPosition? {
        val body = soap("GetPositionInfo", "<InstanceID>0</InstanceID>") ?: return null
        val position = parseDuration(soapValue(body, "RelTime")) ?: return null
        val duration = parseDuration(soapValue(body, "TrackDuration")) ?: 0L
        return DlnaPosition(position, duration)
    }

    /** True when this renderer published a RenderingControl service at all - see [DlnaDevice]. */
    val supportsVolume: Boolean get() = device.renderingControlUrl != null

    /** Current volume as 0..1, or null if the renderer has no RenderingControl / didn't answer. */
    suspend fun volume(): Float? {
        val url = device.renderingControlUrl ?: return null
        val body = soap(url, RENDERING_SERVICE_TYPE, "GetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel>")
            ?: return null
        val value = soapValue(body, "CurrentVolume")?.toIntOrNull() ?: return null
        return (value / 100f).coerceIn(0f, 1f)
    }

    /** [volume] is 0..1; UPnP itself works in whole percent, which is also the step every renderer's
     * own on-screen volume uses. */
    suspend fun setVolume(volume: Float) {
        val url = device.renderingControlUrl ?: return
        val percent = (volume.coerceIn(0f, 1f) * 100).toInt()
        soap(url, RENDERING_SERVICE_TYPE, "SetVolume",
            "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>$percent</DesiredVolume>")
    }

    suspend fun transportState(): DlnaTransportState? {
        val body = soap("GetTransportInfo", "<InstanceID>0</InstanceID>") ?: return null
        val state = soapValue(body, "CurrentTransportState") ?: return null
        return DlnaTransportState(state)
    }

    /** Returns the response body on success, null on any HTTP/SOAP failure - a renderer refusing
     * one command (Pause on a live stream, Seek before it has loaded) is normal and shouldn't
     * throw its way up into playback code. */
    private suspend fun soap(action: String, arguments: String): String? =
        soap(device.controlUrl, SERVICE_TYPE, action, arguments)

    private suspend fun soap(
        controlUrl: String,
        serviceType: String,
        action: String,
        arguments: String
    ): String? = withContext(Dispatchers.IO) {
        val envelope = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """ +
            """s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            "<s:Body><u:$action xmlns:u=\"$serviceType\">$arguments</u:$action></s:Body></s:Envelope>"
        val request = Request.Builder()
            .url(controlUrl)
            .addHeader("SOAPAction", "\"$serviceType#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    private companion object {
        const val SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"
        const val RENDERING_SERVICE_TYPE = "urn:schemas-upnp-org:service:RenderingControl:1"
        const val TIMEOUT_MS = 6000L
    }
}

/** Minimal DIDL-Lite item describing the stream - see [DlnaController.playUrl]. */
internal fun didlLite(url: String, title: String, mimeType: String): String =
    """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
        """xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
        """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
        """<item id="0" parentID="-1" restricted="1">""" +
        "<dc:title>${escapeXml(title)}</dc:title>" +
        "<upnp:class>object.item.videoItem</upnp:class>" +
        """<res protocolInfo="http-get:*:$mimeType:DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000">""" +
        "${escapeXml(url)}</res>" +
        "</item></DIDL-Lite>"

internal fun escapeXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

/** The text of the first `<name>` element in a SOAP response - the responses this app reads have
 * exactly one of each argument, so a full parse buys nothing over this. */
internal fun soapValue(body: String, name: String): String? {
    val open = body.indexOf("<$name>")
    if (open < 0) return null
    val close = body.indexOf("</$name>", open)
    if (close < 0) return null
    return body.substring(open + name.length + 2, close).trim().takeIf { it.isNotEmpty() }
}

/** "0:12:34" / "00:12:34.000" -> milliseconds. "NOT_IMPLEMENTED" and friends give null. */
internal fun parseDuration(value: String?): Long? {
    val parts = value?.split(':') ?: return null
    if (parts.size != 3) return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val seconds = parts[2].substringBefore('.').toLongOrNull() ?: return null
    return ((hours * 60 + minutes) * 60 + seconds) * 1000
}

internal fun formatDuration(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}
