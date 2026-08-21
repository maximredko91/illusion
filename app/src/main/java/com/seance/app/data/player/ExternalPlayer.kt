package com.seance.app.data.player

import android.content.Intent
import android.net.Uri
import com.seance.app.data.local.entity.SmbSourceEntity

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
    fun forDownload(contentUri: String, title: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(contentUri), "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("title", title)
        }

    fun forSmbSource(source: SmbSourceEntity, password: String?, filePath: String, title: String): Intent {
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
        }
    }
}
