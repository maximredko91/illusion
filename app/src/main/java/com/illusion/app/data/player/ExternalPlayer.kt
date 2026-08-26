package com.illusion.app.data.player

import android.content.Intent
import android.net.Uri

/**
 * Builds an ACTION_VIEW Intent to hand playback off to whatever external video player the user
 * picks (VLC, MX Player, Samsung's own Video Player, ...), instead of this app's own
 * SmbDataSource-based player.
 *
 * A completed download already has a real local `content://` Uri any player can open directly.
 * A still-SMB-only item has no such Uri - this app's own playback path is a custom Media3
 * DataSource, not a real file or content provider an external app could read from - so that case
 * goes through [StreamingService] instead, which re-serves the SMB file as a plain loopback HTTP
 * URL any player understands, same shape as [forUrl] below. A literal `smb://user:pass@host/...`
 * Uri was tried first, but only VLC/MX Player implement their own SMB client to actually open one
 * - every other player (Samsung's stock Video Player included, confirmed on-device) simply has no
 * intent-filter for that scheme at all, so it never resolved to anything for them.
 */
object ExternalPlayer {
    /**
     * [packageName] pins the Intent to one specific app (the user's Settings choice, see
     * InstalledPlayerApps) so it launches directly - null leaves it implicit, which makes Android
     * show its own disambiguation dialog when more than one app matches, or launch the sole match
     * directly.
     */
    fun forUrl(uri: Uri, title: String, packageName: String? = null, grantReadPermission: Boolean = false): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            var flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (grantReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
            addFlags(flags)
            putExtra("title", title)
            packageName?.let { setPackage(it) }
        }

    fun forDownload(contentUri: String, title: String, packageName: String? = null): Intent =
        forUrl(Uri.parse(contentUri), title, packageName, grantReadPermission = true)
}
