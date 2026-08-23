package com.seance.app.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether the device is currently on Wi-Fi/Ethernet - what SMB streaming actually needs, unlike
 * NET_CAPABILITY_INTERNET (can be true on mobile data, which still can't reach a home NAS). Used
 * to give a real reason ("no Wi-Fi" vs "load failed") when a poster/fanart fails to load instead
 * of a bare fallback icon with no explanation.
 */
fun isOnLocalNetwork(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
