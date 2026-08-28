package com.illusion.app.ui.player

import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import com.illusion.app.domain.model.PlayerBufferSize

/**
 * Picks buffer sizes based on [bufferSize]:
 * - AUTO scales with the current network's estimated downstream bandwidth: longer buffers on a
 *   weak Wi-Fi link (fewer stalls, slower start), shorter ones on a fast link (quicker start,
 *   less memory held). Estimated once when the player is created for a session - it does not
 *   react to the connection changing mid-playback.
 * - INCREASED/MAXIMUM ignore the bandwidth estimate and use a fixed, larger target buffer (min/max)
 *   - added after AUTO's own high-bandwidth-link branch still rebuffered often on a real
 *   high-bitrate 4K remux (a link can report a high bandwidth capability while the SMB transfer
 *   itself doesn't sustain it, e.g. NAS-side disk/CPU contention). `bufferForPlaybackAfterRebufferMs`
 *   deliberately stays modest even in these profiles - it's how much buffer must accumulate before
 *   resuming after a stall, and when real download throughput is only marginally above the file's
 *   bitrate, the WALL-CLOCK time to accumulate N seconds of buffer is roughly
 *   N / (downloadRate/bitrate - 1) - a thin margin turns a "bigger" threshold into a much longer
 *   real wait, not just a few extra seconds (confirmed on-device: raising this to 10s made a
 *   marginal-bandwidth 4K file's post-stall recovery feel stuck for a long time, worse than
 *   before). The min/max target is what actually reduces stall frequency; this threshold only
 *   needs to be just large enough to avoid immediately re-stalling.
 */
fun buildAdaptiveLoadControl(context: Context, bufferSize: PlayerBufferSize): LoadControl {
    val profile = when (bufferSize) {
        PlayerBufferSize.AUTO -> autoProfile(estimateDownstreamKbps(context))
        PlayerBufferSize.INCREASED -> BufferProfile(
            minBufferMs = 60_000,
            maxBufferMs = 180_000,
            bufferForPlaybackMs = 3_000,
            bufferForPlaybackAfterRebufferMs = 5_000
        )
        PlayerBufferSize.MAXIMUM -> BufferProfile(
            minBufferMs = 90_000,
            maxBufferMs = 300_000,
            bufferForPlaybackMs = 5_000,
            bufferForPlaybackAfterRebufferMs = 8_000
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

private fun autoProfile(bandwidthKbps: Int): BufferProfile = when {
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
