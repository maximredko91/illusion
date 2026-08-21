package com.seance.app.data.scan

import android.content.Context
import androidx.work.Data
import androidx.work.workDataOf
import com.seance.app.R

data class ScanProgress(
    val sourceIndex: Int,
    val sourceCount: Int,
    val currentSourceName: String,
    val filesScanned: Int,
    val filesTotal: Int
) {
    /** One-line summary for the foreground-service progress notification - mirrors what ScanProgressScreen shows for the same phase. */
    fun toNotificationText(context: Context): String =
        context.getString(R.string.scan_progress_source, sourceIndex + 1, sourceCount, currentSourceName)

    fun toData(): Data = workDataOf(
        KEY_SOURCE_INDEX to sourceIndex,
        KEY_SOURCE_COUNT to sourceCount,
        KEY_SOURCE_NAME to currentSourceName,
        KEY_FILES_SCANNED to filesScanned,
        KEY_FILES_TOTAL to filesTotal
    )

    companion object {
        /** [filesTotal] value while still walking directories - the eventual total isn't known yet. */
        const val TOTAL_UNKNOWN = -1

        const val KEY_SOURCE_INDEX = "source_index"
        const val KEY_SOURCE_COUNT = "source_count"
        const val KEY_SOURCE_NAME = "source_name"
        const val KEY_FILES_SCANNED = "files_scanned"
        const val KEY_FILES_TOTAL = "files_total"

        fun fromData(data: Data): ScanProgress? {
            val sourceCount = data.getInt(KEY_SOURCE_COUNT, -1)
            if (sourceCount < 0) return null
            return ScanProgress(
                sourceIndex = data.getInt(KEY_SOURCE_INDEX, 0),
                sourceCount = sourceCount,
                currentSourceName = data.getString(KEY_SOURCE_NAME) ?: "",
                filesScanned = data.getInt(KEY_FILES_SCANNED, 0),
                filesTotal = data.getInt(KEY_FILES_TOTAL, 0)
            )
        }
    }
}
