package com.illusion.app.data.smb

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

/**
 * This device's IPv4 addresses on the currently active network, read from the OS's own
 * LinkProperties rather than by walking NetworkInterface - same reasoning as
 * [com.illusion.app.data.player.StreamingService.localIpv4Address]: with a VPN or tethering up,
 * interface enumeration returns addresses that have nothing to do with the LAN the NAS is on,
 * which is exactly the comparison [subnetMismatchHint] needs to get right.
 */
fun localIpv4Addresses(context: Context): List<LocalIpv4> {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
    val network = manager.activeNetwork ?: return emptyList()
    val linkProperties = manager.getLinkProperties(network) ?: return emptyList()
    return linkProperties.linkAddresses.mapNotNull { linkAddress ->
        val address = linkAddress.address as? Inet4Address ?: return@mapNotNull null
        if (address.isLoopbackAddress || address.isAnyLocalAddress) return@mapNotNull null
        val hostAddress = address.hostAddress ?: return@mapNotNull null
        LocalIpv4(hostAddress, linkAddress.prefixLength)
    }
}
