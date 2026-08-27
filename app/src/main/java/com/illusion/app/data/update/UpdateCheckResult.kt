package com.illusion.app.data.update

/** See [UpdateChecker.checkForUpdate]'s own KDoc for why [Failed] is kept distinct from [UpToDate]. */
sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    /** [checkedVersionInfo] is a short "what did the check actually see" detail (e.g. the local
     * manifest's own versionCode/versionName) - null for GitHub, where "up to date" alone is
     * already unambiguous confirmation the API call succeeded. For a local SMB manifest, plain
     * "up to date" doesn't distinguish "read the manifest, it says the same version" from some
     * silent success-shaped failure - showing what was actually found gives real confirmation
     * the manifest was reachable and parsed, not just that no error was thrown. */
    data class UpToDate(val checkedVersionInfo: String? = null) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}
