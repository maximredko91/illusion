package com.illusion.app.data.player

import android.content.Intent
import android.net.Uri
import com.illusion.app.data.local.entity.SmbSourceEntity

/**
 * Builds an ACTION_VIEW Intent to hand playback off to whatever external video player the user
 * picks (VLC, MX Player, ...), instead of this app's own SmbDataSource-based player.
 *
 * A completed download already has a real local `content://` Uri any player can open directly.
 * A still-SMB-only item has no such Uri - this app's own playback path is a custom Media3
 * DataSource, not a real file or content provider an external app could read from - so instead
 * this builds a literal `smb://user:pass@host/share/path` Uri, which players that support SMB
 * network shares natively (VLC, MX Player) can open themselves. That does mean the source's SMB
 * credentials are placed in the Intent, visible to whichever single app is launched to handle it
 * (not broadcast) - acceptable for a home NAS on the local network, and no different in kind from
 * this app already holding those same credentials to stream it itself.
 */
object ExternalPlayer {
    /**
     * [packageName] pins the Intent to one specific app (the user's Settings choice, see
     * InstalledPlayerApps) so it launches directly - null leaves it implicit, which makes Android
     * show its own disambiguation dialog when more than one app matches, or launch the sole match
     * directly.
     */
    fun forDownload(contentUri: String, title: String, packageName: String? = null): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(contentUri), "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("title", title)
            packageName?.let { setPackage(it) }
        }

    fun forSmbSource(
        source: SmbSourceEntity,
        password: String?,
        filePath: String,
        title: String,
        packageName: String? = null
    ): Intent {
        val encodedPath = filePath
            .trimStart('/')
            .split('/')
            .joinToString("/") { Uri.encode(it) }
        val userInfo = "${Uri.encode(source.username)}:${Uri.encode(password.orEmpty())}"
        val url = "smb://$userInfo@${source.host}/${Uri.encode(source.share)}/$encodedPath"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("title", title)
            packageName?.let { setPackage(it) }
        }
    }
}
