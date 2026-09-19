package com.illusion.app.data.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the parsing this app does on whatever a TV happens to send back - the part most likely to
 * differ between devices and the only part testable without a real renderer on the network.
 */
class DlnaParsingTest {

    @Test
    fun `location header is read regardless of case and spacing`() {
        val reply = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\n" +
            "Location:  http://192.168.1.50:9197/dmr\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        assertEquals("http://192.168.1.50:9197/dmr", locationHeader(reply))
    }

    @Test
    fun `reply without a location is ignored`() {
        assertNull(locationHeader("HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\n\r\n"))
    }

    @Test
    fun `description yields the AVTransport control url, not another service's`() {
        val xml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <friendlyName>Гостиная TV</friendlyName>
                <manufacturer>Samsung</manufacturer>
                <modelName>QE55</modelName>
                <UDN>uuid:abc-123</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/upnp/control/rendering</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/upnp/control/avtransport</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent().toByteArray()

        val device = parseDescription(xml, "http://192.168.1.50:9197/dmr/desc.xml")

        requireNotNull(device)
        assertEquals("uuid:abc-123", device.udn)
        assertEquals("Гостиная TV", device.friendlyName)
        assertEquals("http://192.168.1.50:9197/upnp/control/avtransport", device.controlUrl)
    }

    @Test
    fun `URLBase wins over the description url when the device publishes one`() {
        val xml = """
            <root>
              <URLBase>http://10.0.0.7:8200/</URLBase>
              <device>
                <friendlyName>Receiver</friendlyName>
                <UDN>uuid:xyz</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>ctl/AVTransport</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent().toByteArray()

        val device = parseDescription(xml, "http://10.0.0.7:49152/description.xml")

        assertEquals("http://10.0.0.7:8200/ctl/AVTransport", device?.controlUrl)
    }

    @Test
    fun `a device with no AVTransport service is not offered as a cast target`() {
        val xml = """
            <root><device><friendlyName>Printer</friendlyName><UDN>uuid:p</UDN>
            <serviceList><service>
              <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
              <controlURL>/ctl</controlURL>
            </service></serviceList></device></root>
        """.trimIndent().toByteArray()

        assertNull(parseDescription(xml, "http://10.0.0.9/desc.xml"))
    }

    @Test
    fun `durations round-trip through the wire format`() {
        assertEquals(0L, parseDuration("0:00:00"))
        assertEquals(754_000L, parseDuration("00:12:34"))
        assertEquals(754_000L, parseDuration("0:12:34.000"))
        assertEquals(3_661_000L, parseDuration("1:01:01"))
        assertEquals("1:01:01", formatDuration(3_661_000L))
        assertEquals("0:00:00", formatDuration(-5L))
    }

    @Test
    fun `NOT_IMPLEMENTED and friends parse to null instead of zero`() {
        assertNull(parseDuration("NOT_IMPLEMENTED"))
        assertNull(parseDuration(null))
        assertNull(parseDuration("12:34"))
    }

    @Test
    fun `soap values are read out of a real envelope`() {
        val body = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <Track>1</Track>
                  <TrackDuration>1:45:00</TrackDuration>
                  <RelTime>0:03:20</RelTime>
                </u:GetPositionInfoResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        assertEquals("1:45:00", soapValue(body, "TrackDuration"))
        assertEquals("0:03:20", soapValue(body, "RelTime"))
        assertNull(soapValue(body, "AbsTime"))
    }

    @Test
    fun `stream urls are escaped inside the DIDL metadata`() {
        val didl = didlLite("http://192.168.1.2:8080/stream?path=a%20b&size=10", "Тит<л>", "video/mp4")
        assert(didl.contains("&amp;size=10")) { didl }
        assert(didl.contains("Тит&lt;л&gt;")) { didl }
    }
}
