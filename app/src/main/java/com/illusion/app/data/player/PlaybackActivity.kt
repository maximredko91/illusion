package com.illusion.app.data.player

/**
 * Whether the live player is actively decoding video right now - checked by [com.illusion.app.data.scan.ThumbnailGenerator]
 * before/during its own frame extraction so its MediaMetadataRetriever-driven decoder churn
 * doesn't starve the live player of the same shared hardware video decoder. Confirmed on-device:
 * with ThumbnailGenerationWorker running concurrently with playback, real playback went black
 * (audio kept playing fine - a separate, much cheaper decoder - but video never got a stable
 * frame) while logcat showed hundreds of MediaCodec configure() calls/minute from the background
 * sprite generation contending for the same hardware AVC decoder instance.
 *
 * A plain `@Volatile var`, not a Compose `mutableStateOf` like [com.illusion.app.ui.player.PipController] -
 * this is read from a background WorkManager coroutine, not observed by any composition.
 */
object PlaybackActivity {
    @Volatile
    var isActive: Boolean = false
}
