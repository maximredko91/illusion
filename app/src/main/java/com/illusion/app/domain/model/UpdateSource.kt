package com.illusion.app.domain.model

/** Where UpdateViewModel checks for a newer build - GitHub Releases (the original, works over
 * the internet) or a manifest+APKs published to a local SMB source (see LocalUpdateChecker),
 * for updating devices with flaky/no internet but a working home-network connection to the NAS. */
enum class UpdateSource {
    GITHUB,
    LOCAL
}
