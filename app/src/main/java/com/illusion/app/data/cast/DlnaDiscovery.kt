package com.illusion.app.data.cast

import android.content.Context
import android.net.wifi.WifiManager
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Finds UPnP MediaRenderers (TVs, receivers) on the local network by plain SSDP, with no UPnP
 * library: an M-SEARCH datagram to the SSDP multicast group, then one HTTP GET per answering
 * device for its description XML (friendly name + the AVTransport service's control URL).
 *
 * Deliberately hand-rolled rather than pulling in Cling/jUPnP - this app needs exactly two things
 * from UPnP (find renderers, drive AVTransport), and those libraries bring a whole device stack,
 * their own HTTP servers and an Android service to host them. Same reasoning as the rest of the
 * app's no-extra-abstraction style.
 *
 * Needs `ACCESS_LOCAL_NETWORK` (declared for SMB already - the same OS gate applies to any local
 * socket on targetSdk 37) and takes a multicast lock for the duration, or Wi-Fi hardware filtering
 * drops the replies on some devices.
 */
class DlnaDiscovery(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(DESCRIPTION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(DESCRIPTION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    /**
     * Searches for [SEARCH_DURATION_MS] and returns everything that answered, de-duplicated by
     * UDN. Devices routinely miss a single M-SEARCH (or answer late), so the request goes out
     * [SEARCH_ATTEMPTS] times while the same socket keeps listening.
     */
    suspend fun discover(): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val multicastLock = runCatching {
            context.getSystemService(WifiManager::class.java)
                ?.createMulticastLock("illusion-dlna")
                ?.apply { setReferenceCounted(true); acquire() }
        }.getOrNull()

        val locations = try {
            searchLocations()
        } finally {
            runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        }

        locations.mapNotNull { location -> runCatching { describe(location) }.getOrNull() }
            .distinctBy { it.udn }
            .sortedBy { it.displayName.lowercase() }
    }

    /** The LOCATION header of every distinct SSDP reply within the search window. */
    private fun searchLocations(): Set<String> {
        val locations = linkedSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.broadcast = true
            val group = InetAddress.getByName(SSDP_HOST)
            val payload = buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: $SSDP_HOST:$SSDP_PORT\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 2\r\n")
                append("ST: $SEARCH_TARGET\r\n")
                append("\r\n")
            }.toByteArray()

            val deadline = System.currentTimeMillis() + SEARCH_DURATION_MS
            var sent = 0
            var nextSendAt = 0L
            val buffer = ByteArray(8 * 1024)
            while (System.currentTimeMillis() < deadline) {
                if (sent < SEARCH_ATTEMPTS && System.currentTimeMillis() >= nextSendAt) {
                    runCatching {
                        socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(group, SSDP_PORT)))
                    }
                    sent++
                    nextSendAt = System.currentTimeMillis() + RESEND_INTERVAL_MS
                }
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val reply = String(packet.data, 0, packet.length)
                locationHeader(reply)?.let(locations::add)
            }
        }
        return locations
    }

    private fun describe(location: String): DlnaDevice? {
        val response = http.newCall(Request.Builder().url(location).build()).execute()
        val body = response.use { if (!it.isSuccessful) null else it.body?.bytes() } ?: return null
        return parseDescription(body, location)
    }

    private companion object {
        const val SSDP_HOST = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val SEARCH_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1"
        const val SEARCH_DURATION_MS = 3500L
        const val RESEND_INTERVAL_MS = 900L
        const val SEARCH_ATTEMPTS = 3
        const val SOCKET_TIMEOUT_MS = 400
        const val DESCRIPTION_TIMEOUT_MS = 4000L
    }
}

/** LOCATION (case-insensitive, as devices spell it however they like) out of an SSDP reply. */
internal fun locationHeader(reply: String): String? = reply.lineSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
    ?.drop("LOCATION:".length)
    ?.trim()
    ?.takeIf { it.startsWith("http", ignoreCase = true) }

/**
 * Pulls the friendly name and the AVTransport control URL out of a UPnP device description.
 *
 * Control URLs come relative far more often than absolute, and the base they resolve against is
 * `URLBase` when the device publishes one, otherwise the description URL itself - both handled
 * here so callers only ever see an absolute URL.
 */
internal fun parseDescription(xml: ByteArray, descriptionUrl: String): DlnaDevice? {
    val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
    parser.setInput(ByteArrayInputStream(xml), null)

    var friendlyName: String? = null
    var manufacturer: String? = null
    var modelName: String? = null
    var udn: String? = null
    var urlBase: String? = null
    var controlUrl: String? = null

    // Service blocks carry their own serviceType/controlURL pair; only AVTransport's counts, and a
    // device can list several services before it.
    var currentServiceType: String? = null
    var currentControlUrl: String? = null
    var inService = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                "service" -> {
                    inService = true
                    currentServiceType = null
                    currentControlUrl = null
                }
                "friendlyname" -> if (friendlyName == null) friendlyName = parser.nextTextOrNull()
                "manufacturer" -> if (manufacturer == null) manufacturer = parser.nextTextOrNull()
                "modelname" -> if (modelName == null) modelName = parser.nextTextOrNull()
                "udn" -> if (udn == null) udn = parser.nextTextOrNull()
                "urlbase" -> if (urlBase == null) urlBase = parser.nextTextOrNull()
                "servicetype" -> if (inService) currentServiceType = parser.nextTextOrNull()
                "controlurl" -> if (inService) currentControlUrl = parser.nextTextOrNull()
            }
            XmlPullParser.END_TAG -> if (parser.name.equals("service", ignoreCase = true)) {
                if (controlUrl == null &&
                    currentServiceType?.contains("AVTransport", ignoreCase = true) == true &&
                    !currentControlUrl.isNullOrBlank()
                ) {
                    controlUrl = currentControlUrl
                }
                inService = false
            }
        }
        event = parser.next()
    }

    val relative = controlUrl ?: return null
    val absolute = resolveUpnpUrl(relative, urlBase, descriptionUrl) ?: return null
    return DlnaDevice(
        udn = udn?.takeIf { it.isNotBlank() } ?: absolute,
        friendlyName = friendlyName.orEmpty(),
        manufacturer = manufacturer,
        modelName = modelName,
        controlUrl = absolute
    )
}

/** Absolute form of a (usually relative) UPnP URL: against `URLBase` if the device gave one, else
 * against the description URL it came from. */
internal fun resolveUpnpUrl(url: String, urlBase: String?, descriptionUrl: String): String? = runCatching {
    if (url.startsWith("http", ignoreCase = true)) return@runCatching url
    val base = urlBase?.takeIf { it.isNotBlank() } ?: descriptionUrl
    URI(base).resolve(url).toString()
}.getOrNull()

private fun XmlPullParser.nextTextOrNull(): String? = runCatching { nextText()?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()
