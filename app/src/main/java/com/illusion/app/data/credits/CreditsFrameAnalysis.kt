package com.illusion.app.data.credits

import com.illusion.app.domain.model.PostCreditsScene

/**
 * Finding a film's credits and the scenes hidden in them by what the PICTURE looks like - the
 * film counterpart of the audio intro/credits search, which needs a second episode to compare
 * against and so can't work on a film.
 *
 * The idea: a credits roll is a black screen with some text on it; a scene is an ordinary picture.
 * Frames are sampled every few seconds over the closing stretch of the film, each one classified
 * as "credit-like" or not ([isCreditLikeFrame]), and the resulting sequence is read as
 * film -> credits -> (scene -> credits)* -> maybe a final scene ([findCreditsLayout]).
 *
 * Known blind spots, by construction: credits over live footage or on a coloured/animated
 * background don't look like credits here (Marvel's stylised main-on-end titles are an example -
 * a mid-credits scene that follows them and precedes the black roll can't be told from the film
 * itself), and a long, genuinely pitch-black scene near the end can pass for a credits roll. The
 * manual marker stays as the way to fix either.
 *
 * Plain Kotlin with no Android types, so all of it is unit-tested (CreditsFrameAnalysisTest).
 */
class FrameSample(val timeMs: Long, val creditLike: Boolean)

/** Where the credits start and which scenes interrupt or follow them. */
class CreditsLayout(val creditsStartMs: Long, val scenes: List<PostCreditsScene>)

/**
 * True when at least half of the frame's central area is black.
 *
 * Only the centre is looked at: a 2.39:1 film in a 16:9 container carries black bars on every
 * frame, and counting them would make the whole film look half-black. "Black" is a low absolute
 * threshold rather than "dark" - a night scene is dark but noisy, a credits background is
 * actually black, and that difference is the whole signal. Half, not most: scaled down to
 * thumbnail size, a dense two-column roll smears into grey over a good third of the frame.
 *
 * [pixels] are ARGB ints, row by row, as `Bitmap.getPixels` returns them.
 */
fun isCreditLikeFrame(pixels: IntArray, width: Int, height: Int): Boolean {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return false
    val top = height * 20 / 100
    val bottom = height * 80 / 100
    val left = width * 15 / 100
    val right = width * 85 / 100
    var black = 0
    var total = 0
    for (y in top until bottom) {
        for (x in left until right) {
            val pixel = pixels[y * width + x]
            val luma = (((pixel shr 16) and 0xFF) * 299 + ((pixel shr 8) and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
            if (luma <= BLACK_LUMA) black++
            total++
        }
    }
    return total > 0 && black * 2 >= total
}

/**
 * Reads a time-ordered run of [samples] (covering the end of the film up to [durationMs]) as
 * credits and scenes, or returns null when nothing in it looks like a credits roll.
 *
 * Three steps: a short dark stretch inside the film (a fade, a night shot) is not credits; a short
 * bright stretch inside the credits (a logo, a dedication card) is not a scene; then the longest
 * credit-like run is taken as the roll (with an earlier part of it attached if only a scene-sized
 * gap separates them), and every remaining non-credit run after the start is a scene.
 */
fun findCreditsLayout(samples: List<FrameSample>, durationMs: Long): CreditsLayout? {
    if (samples.size < 2) return null
    val credit = BooleanArray(samples.size) { samples[it].creditLike }
    // A run [from, to) lasts until the next sample's time, or to the end of the film for the last one.
    fun runMs(from: Int, to: Int) = (if (to < samples.size) samples[to].timeMs else durationMs) - samples[from].timeMs

    for ((from, to) in runsOf(credit, value = true)) {
        if (runMs(from, to) < MIN_CREDIT_RUN_MS) credit.fill(false, from, to)
    }
    for ((from, to) in runsOf(credit, value = false)) {
        // The leading non-credit run is the film itself - however little of it the window caught.
        if (from > 0 && runMs(from, to) < MIN_SCENE_MS) credit.fill(true, from, to)
    }

    // The roll itself is the LONGEST black stretch, not the first one: a dark finale is black for a
    // minute too, and taking the first long-enough run made a whole film's last quarter hour come
    // out as a "scene" (seen on the first real film this ran on). Earlier black stretches are then
    // attached only if what separates them from the roll is short enough to be a mid-credits scene.
    val creditRuns = runsOf(credit, value = true)
    val rollIndex = creditRuns.indices.maxByOrNull { runMs(creditRuns[it].first, creditRuns[it].second) } ?: return null
    if (runMs(creditRuns[rollIndex].first, creditRuns[rollIndex].second) < MIN_CREDITS_MS) return null
    var creditsStart = creditRuns[rollIndex].first
    for (index in rollIndex - 1 downTo 0) {
        val (from, to) = creditRuns[index]
        val gapMs = samples[creditsStart].timeMs - samples[to].timeMs
        if (runMs(from, to) < MIN_LEADING_ROLL_MS || gapMs > MAX_SCENE_MS) break
        creditsStart = from
    }
    val scenes = runsOf(credit, value = false)
        .filter { (from, _) -> from > creditsStart }
        // A scene has to be mostly picture as SAMPLED, before the smoothing above. Two bright
        // frames 20 seconds apart inside the roll (logos, a song-credits card) turn the black
        // between them into a "short credit run", which the first pass erases - and the three
        // together then read as a 25-second scene. Seen on Eternals: a marker in the middle of
        // the roll, five minutes after the real mid-credits scene.
        .filter { (from, to) -> (from until to).count { !samples[it].creditLike } * 2 > to - from }
        .map { (from, to) ->
            PostCreditsScene(
                startMs = samples[from].timeMs,
                endMs = if (to < samples.size) samples[to].timeMs else durationMs
            )
        }
    return CreditsLayout(samples[creditsStart].timeMs, scenes)
}

/**
 * Where an EPISODE's credits start, or null. Stricter than [findCreditsLayout] on purpose: an
 * episode's roll is short, so the "longest black stretch of 45 seconds" test that works for a film
 * can't be used - instead the roll has to run to the very end of the file (a closing logo shorter
 * than a scene is absorbed), which a dark scene mid-episode never does. An episode that ends on a
 * preview or a stinger after its credits is therefore left unmarked rather than guessed at.
 */
fun findEpisodeCreditsStart(samples: List<FrameSample>, durationMs: Long): Long? {
    if (samples.size < 2) return null
    val credit = BooleanArray(samples.size) { samples[it].creditLike }
    fun runMs(from: Int, to: Int) = (if (to < samples.size) samples[to].timeMs else durationMs) - samples[from].timeMs

    for ((from, to) in runsOf(credit, value = false)) {
        if (from > 0 && runMs(from, to) < MIN_SCENE_MS) credit.fill(true, from, to)
    }
    val (from, to) = runsOf(credit, value = true).lastOrNull() ?: return null
    if (to < samples.size || runMs(from, to) < MIN_CREDIT_RUN_MS) return null
    // Black from the first sample on means the window never saw the episode itself: either the
    // whole tail is one dark scene or the roll is longer than the window - no boundary to report.
    if (from == 0) return null
    return samples[from].timeMs
}

/** Maximal runs of [value] in [flags], as (from, toExclusive) index pairs. */
private fun runsOf(flags: BooleanArray, value: Boolean): List<Pair<Int, Int>> {
    val runs = ArrayList<Pair<Int, Int>>()
    var from = -1
    for (index in flags.indices) {
        if (flags[index] == value) {
            if (from < 0) from = index
        } else if (from >= 0) {
            runs += from to index
            from = -1
        }
    }
    if (from >= 0) runs += from to flags.size
    return runs
}

/** Luma (0-255) at or below which a pixel counts as black. Leaves room for limited-range video
 * whose black comes out as 16 rather than 0. */
private const val BLACK_LUMA = 20

/** A dark stretch shorter than this is a fade or a night shot, not a piece of the credits. */
const val MIN_CREDIT_RUN_MS = 20_000L

/** A bright stretch inside the credits shorter than this is a logo or a card, not a scene. */
const val MIN_SCENE_MS = 15_000L

/** The longest black stretch has to last at least this long to be a credits roll at all. */
const val MIN_CREDITS_MS = 45_000L

/** A black stretch before the main roll counts as the roll's first part only if it's this long. */
const val MIN_LEADING_ROLL_MS = 30_000L

/** ...and only if no more than this separates it from the roll - longer than any mid-credits scene. */
const val MAX_SCENE_MS = 180_000L
