package com.seance.app.ui.player

import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Picks buffer sizes based on the current network's estimated downstream bandwidth: longer
 * buffers on a weak Wi-Fi link (fewer stalls, slower start), shorter ones on a fast link
 * (quicker start, less memory held). Estimated once when the player is created for a session -
 * it does not react to the connection changing mid-playback.
 */
fun buildAdaptiveLoadControl(context: Context): LoadControl {
    val bandwidthKbps = estimateDownstreamKbps(context)

    val profile = when {
        bandwidthKbps in 0..4_000 -> BufferProfile(
            minBufferMs = 30_000,
            maxBufferMs = 90_000,
            bufferForPlaybackMs = 5_000,
            bufferForPlaybackAfterRebufferMs = 8_000
        )
        bandwidthKbps > 15_000 -> BufferProfile(
            minBufferMs = 15_000,
            maxBufferMs = 30_000,
            bufferForPlaybackMs = 1_000,
            bufferForPlaybackAfterRebufferMs = 2_000
        )
        else -> BufferProfile(
            minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            bufferForPlaybackAfterRebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )
    }

    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            profile.minBufferMs,
            profile.maxBufferMs,
            profile.bufferForPlaybackMs,
            profile.bufferForPlaybackAfterRebufferMs
        )
        .build()
}

/** Returns the active network's estimated downstream bandwidth in kbps, or -1 if unknown. */
private fun estimateDownstreamKbps(context: Context): Int {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return -1
    val network = connectivityManager.activeNetwork ?: return -1
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return -1
    return capabilities.linkDownstreamBandwidthKbps
}

private data class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
)
