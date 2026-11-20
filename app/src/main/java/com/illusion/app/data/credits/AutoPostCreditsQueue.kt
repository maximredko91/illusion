package com.illusion.app.data.credits

import android.content.Context
import java.io.File

/**
 * Films waiting for the automatic credits/scene search that follows a library scan (see
 * LibraryScanWorker and PostCreditsDetectWorker.drainQueue). The film counterpart of
 * AutoIntroQueue, and a plain file for the same reasons: a first-ever scan can add more films
 * than fit into WorkManager's input data, and an entry is removed only once its film has been
 * dealt with, so a killed process picks up where it was.
 */
class AutoPostCreditsQueue(context: Context) {
    private val file = File(context.filesDir, "auto_post_credits_queue.txt")

    fun add(stableIds: Collection<String>) {
        if (stableIds.isEmpty()) return
        synchronized(LOCK) { write(LinkedHashSet(read()).apply { addAll(stableIds) }) }
    }

    /** How many films are waiting - for the "search markers?" offer. */
    fun count(): Int = synchronized(LOCK) { read().size }

    fun peek(): String? = synchronized(LOCK) { read().firstOrNull() }

    fun remove(stableId: String) {
        synchronized(LOCK) { write(read() - stableId) }
    }

    // One stableId per line - it's a hex hash, so it can't contain a line break.
    private fun read(): List<String> =
        runCatching { file.readLines().filter { it.isNotEmpty() } }.getOrDefault(emptyList())

    private fun write(stableIds: Collection<String>) {
        runCatching {
            if (stableIds.isEmpty()) file.delete() else file.writeText(stableIds.joinToString("\n"))
        }
    }

    private companion object {
        /** The scan worker adds while the search worker removes - one lock for every instance. */
        val LOCK = Any()
    }
}
