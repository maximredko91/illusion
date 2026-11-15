package com.illusion.app.domain.model

/**
 * One scene after (or in the middle of) a title's credits. A film can have several - Marvel's
 * mid-credits and post-credits pair being the usual example.
 *
 * [endMs] is known when the automatic search found the scene (it sees where the credits resume)
 * and null for a manual mark, where the user only ever points at where a scene starts.
 */
data class PostCreditsScene(val startMs: Long, val endMs: Long? = null)

/**
 * Reads `MediaItemEntity.postCreditsScenesMs` - "start-end" or bare "start" entries separated by
 * commas, e.g. `7084000-7141000,7702000`. Falls back to [legacyStartMs] (the single
 * `postCreditsStartMs` column that existed before several scenes were supported) so a mark made on
 * an older build keeps working without a data migration. Malformed entries are skipped, never
 * thrown on: a bad marker must not take the player down.
 */
fun parsePostCreditsScenes(raw: String?, legacyStartMs: Long?): List<PostCreditsScene> {
    val parsed = raw.orEmpty().split(',').mapNotNull { entry ->
        val parts = entry.trim().split('-')
        val start = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        if (start < 0) return@mapNotNull null
        val end = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > start }
        PostCreditsScene(start, end)
    }
    if (parsed.isNotEmpty()) return parsed.sortedBy { it.startMs }
    return listOfNotNull(legacyStartMs?.takeIf { it >= 0 }?.let { PostCreditsScene(it) })
}

/** Inverse of [parsePostCreditsScenes]; null for an empty list so the column goes back to NULL. */
fun encodePostCreditsScenes(scenes: List<PostCreditsScene>): String? =
    scenes.sortedBy { it.startMs }
        .joinToString(",") { scene -> scene.endMs?.let { "${scene.startMs}-$it" } ?: scene.startMs.toString() }
        .takeIf { it.isNotEmpty() }
