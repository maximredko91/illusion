package com.illusion.app.data.intro

import android.content.Context
import java.io.File

/** One season of one series - also the row shape of `MediaItemDao.getSeasonsOf`. */
data class SeasonKey(val seriesStableId: String, val seasonNumber: Int)

/**
 * Seasons waiting for the intro/credits search a library scan offers (see
 * LibraryScanWorker and IntroDetectWorker.drainQueue).
 *
 * A plain file rather than WorkManager input data or a Room table: a first-ever scan can add
 * hundreds of seasons at once, which doesn't fit WorkManager's ~10 KB of input, and the list is
 * a to-do note, not library data. Being on disk also makes the search resumable - an entry is
 * removed only after its season has been dealt with, so a killed process picks up where it was.
 */
class AutoIntroQueue(context: Context) {
    private val file = File(context.filesDir, "auto_intro_queue.txt")

    fun add(seasons: Collection<SeasonKey>) {
        if (seasons.isEmpty()) return
        synchronized(LOCK) { write(LinkedHashSet(read()).apply { addAll(seasons) }) }
    }

    /** How many seasons are waiting - for the "search markers?" offer. */
    fun count(): Int = synchronized(LOCK) { read().size }

    fun peek(): SeasonKey? = synchronized(LOCK) { read().firstOrNull() }

    fun remove(season: SeasonKey) {
        synchronized(LOCK) { write(read() - season) }
    }

    // One "season<TAB>seriesStableId" per line - the id goes last because it's a path and may
    // contain anything but a line break.
    private fun read(): List<SeasonKey> = runCatching {
        file.readLines().mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            val season = parts.getOrNull(0)?.toIntOrNull()
            val seriesId = parts.getOrNull(1)
            if (season != null && !seriesId.isNullOrEmpty()) SeasonKey(seriesId, season) else null
        }
    }.getOrDefault(emptyList())

    private fun write(seasons: Collection<SeasonKey>) {
        runCatching {
            if (seasons.isEmpty()) {
                file.delete()
            } else {
                file.writeText(seasons.joinToString("\n") { "${it.seasonNumber}\t${it.seriesStableId}" })
            }
        }
    }

    private companion object {
        /** The scan worker adds while the search worker removes - one lock for every instance. */
        val LOCK = Any()
    }
}
