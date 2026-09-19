package com.illusion.app.data.intro

/** Where the shared stretch sits in the FIRST of the two fingerprints compared, in milliseconds. */
data class IntroMatch(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Finds the stretch of audio two episodes have in common - which, in the opening minutes of two
 * episodes of the same show, is the title sequence.
 *
 * Works the way every "same recording?" matcher does: try every plausible time offset between the
 * two fingerprints, count how many frames match at that offset (a frame matches when at most
 * [MAX_BIT_ERRORS] of its 32 bits differ), keep the best offset, then walk that alignment to find
 * the longest run of matching frames. Short gaps inside a run are tolerated - a compression
 * artefact or a dubbed line over the theme shouldn't split one intro into two halves.
 *
 * Returns null when nothing long enough lines up, which is the normal answer for a show with no
 * fixed intro at all - the caller then leaves the season unmarked rather than inventing a range.
 */
fun findSharedIntro(
    first: Fingerprint,
    second: Fingerprint,
    minDurationMs: Long = MIN_INTRO_MS,
    maxDurationMs: Long = MAX_INTRO_MS
): IntroMatch? {
    if (first.size < MIN_FRAMES || second.size < MIN_FRAMES) return null

    var bestOffset = 0
    var bestScore = 0
    for (offset in -(second.size - MIN_FRAMES)..(first.size - MIN_FRAMES)) {
        var score = 0
        val start = maxOf(0, offset)
        val end = minOf(first.size, second.size + offset)
        for (i in start until end) {
            if (Integer.bitCount(first.hashes[i] xor second.hashes[i - offset]) <= MAX_BIT_ERRORS) score++
        }
        if (score > bestScore) {
            bestScore = score
            bestOffset = offset
        }
    }
    if (bestScore < MIN_FRAMES) return null

    // Longest run at the winning alignment, allowing short non-matching gaps inside it.
    val start = maxOf(0, bestOffset)
    val end = minOf(first.size, second.size + bestOffset)
    var runStart = -1
    var runEnd = -1
    var bestStart = -1
    var bestEnd = -1
    var gap = 0
    for (i in start until end) {
        val matches = Integer.bitCount(first.hashes[i] xor second.hashes[i - bestOffset]) <= MAX_BIT_ERRORS
        if (matches) {
            if (runStart < 0) runStart = i
            runEnd = i
            gap = 0
        } else if (runStart >= 0) {
            gap++
            if (gap > MAX_GAP_FRAMES) {
                if (runEnd - runStart > bestEnd - bestStart) {
                    bestStart = runStart
                    bestEnd = runEnd
                }
                runStart = -1
                gap = 0
            }
        }
    }
    if (runStart >= 0 && runEnd - runStart > bestEnd - bestStart) {
        bestStart = runStart
        bestEnd = runEnd
    }
    if (bestStart < 0) return null

    val match = IntroMatch(
        startMs = bestStart * FRAME_HOP_MS,
        endMs = (bestEnd + 1) * FRAME_HOP_MS
    )
    return match.takeIf { it.durationMs in minDurationMs..maxDurationMs }
}

/** Of 32 bits per frame - about a quarter, the usual working threshold for this hash family. */
private const val MAX_BIT_ERRORS = 8

/** ~1.3 s of non-matching audio is bridged rather than ending the run. */
private const val MAX_GAP_FRAMES = 20

/** Shorter than this is noise, not a title sequence. */
const val MIN_INTRO_MS = 10_000L

/** Longer than this is two episodes that are literally the same file, or a matcher gone wrong. */
const val MAX_INTRO_MS = 200_000L

private val MIN_FRAMES = (MIN_INTRO_MS / FRAME_HOP_MS).toInt()
