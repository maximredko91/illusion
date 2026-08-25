package com.illusion.app.data.update

/** See [UpdateChecker.checkForUpdate]'s own KDoc for why [Failed] is kept distinct from [UpToDate]. */
sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}
