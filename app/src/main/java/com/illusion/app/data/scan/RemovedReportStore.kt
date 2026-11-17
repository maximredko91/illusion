package com.illusion.app.data.scan

import android.content.Context
import java.io.File

/** What one scan removed from the library: [itemCount] library items under [titles] (a series is one title). */
data class RemovedReport(val atMs: Long, val itemCount: Int, val titles: List<String>)

/**
 * Keeps the list of what the last scan removed because its files were gone from the server.
 *
 * The scan's own result screen and notification show it once; close them and there was no way to
 * find out what had disappeared. Only the latest scan that actually removed something is kept - a
 * later scan that removed nothing doesn't erase it. A plain file: it's a note for the user to
 * read, not library data, and the full list doesn't fit WorkManager's output.
 */
class RemovedReportStore(context: Context) {
    private val file = File(context.filesDir, "last_scan_removed.txt")

    fun save(report: RemovedReport) {
        runCatching {
            val lines = listOf(report.atMs.toString(), report.itemCount.toString()) +
                report.titles.take(MAX_TITLES).map { it.replace('\n', ' ').replace('\r', ' ') }
            file.writeText(lines.joinToString("\n"))
        }
    }

    fun load(): RemovedReport? = runCatching {
        val lines = file.readLines()
        RemovedReport(
            atMs = lines[0].toLong(),
            itemCount = lines[1].toInt(),
            titles = lines.drop(2).filter { it.isNotEmpty() }
        )
    }.getOrNull()

    private companion object {
        const val MAX_TITLES = 2000
    }
}
