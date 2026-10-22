package com.illusion.app.data.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class SubnetHintTest {

    private val phoneOnLan = listOf(LocalIpv4("192.168.2.70", 24))

    @Test
    fun hostInAnotherSubnetIsReported() {
        // The real 2026-10-22 failure: router moved to 192.168.2.0/24, source still said 192.168.1.1.
        val hint = subnetMismatchHint("192.168.1.1", phoneOnLan)
        assertEquals("адрес 192.168.1.1 не в текущей сети (телефон в 192.168.2.0/24)", hint)
    }

    @Test
    fun hostInTheSameSubnetGivesNoHint() {
        assertNull(subnetMismatchHint("192.168.2.1", phoneOnLan))
    }

    @Test
    fun prefixLengthDecidesTheSubnetNotTheThirdOctet() {
        // Same addresses as the mismatch case, but a /16 really does contain 192.168.1.1.
        assertNull(subnetMismatchHint("192.168.1.1", listOf(LocalIpv4("192.168.2.70", 16))))
    }

    @Test
    fun hostNameGivesNoHint() {
        // A name may legitimately resolve anywhere - nothing can be concluded from its text.
        assertNull(subnetMismatchHint("nas.local", phoneOnLan))
    }

    @Test
    fun malformedAddressGivesNoHint() {
        assertNull(subnetMismatchHint("192.168.1", phoneOnLan))
        assertNull(subnetMismatchHint("192.168.1.999", phoneOnLan))
        assertNull(subnetMismatchHint("192.168.1.x", phoneOnLan))
    }

    @Test
    fun withoutAnyLocalAddressNothingIsClaimed() {
        assertNull(subnetMismatchHint("192.168.1.1", emptyList()))
    }

    @Test
    fun loopbackAndLinkLocalAreIgnored() {
        // Only self-assigned/loopback addresses means the phone has no LAN to compare against,
        // so claiming the NAS is "in another network" would be guesswork.
        val useless = listOf(LocalIpv4("127.0.0.1", 8), LocalIpv4("169.254.3.4", 16))
        assertNull(subnetMismatchHint("192.168.1.1", useless))
    }

    @Test
    fun aMatchOnAnySubnetSuppressesTheHint() {
        val dualHomed = listOf(LocalIpv4("10.0.0.5", 24), LocalIpv4("192.168.1.70", 24))
        assertNull(subnetMismatchHint("192.168.1.1", dualHomed))
    }

    @Test
    fun timeoutMessageCarriesTheHintWhenTheSubnetDiffers() {
        val message = classifySmbError(SocketTimeoutException(), "192.168.1.1", phoneOnLan)
        assertTrue(message, message.contains("не в текущей сети"))
        assertTrue(message, message.contains("192.168.2.0/24"))
    }

    @Test
    fun timeoutKeepsTheGenericMessageWhenTheAddressLooksRight() {
        // A reachable-looking address that simply doesn't answer - the NAS is off, and telling the
        // user to "fix the address" would send them to change something already correct.
        val message = classifySmbError(SocketTimeoutException(), "192.168.2.1", phoneOnLan)
        assertTrue(message, message.contains("не отвечает"))
    }
}
