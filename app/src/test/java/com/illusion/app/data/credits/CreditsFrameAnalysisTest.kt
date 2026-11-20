package com.illusion.app.data.credits

import com.illusion.app.domain.model.PostCreditsScene
import com.illusion.app.domain.model.encodePostCreditsScenes
import com.illusion.app.domain.model.parsePostCreditsScenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsFrameAnalysisTest {

    /** One character per 5-second sample: 'C' credit-like, '.' ordinary picture. */
    private fun samples(pattern: String) =
        pattern.mapIndexed { index, char -> FrameSample(index * STEP, char == 'C') }

    private fun layout(pattern: String) = findCreditsLayout(samples(pattern), pattern.length * STEP)

    @Test
    fun plainCreditsRollHasAStartAndNoScenes() {
        val layout = layout("." .repeat(10) + "C".repeat(40))
        assertNotNull(layout)
        assertEquals(10 * STEP, layout!!.creditsStartMs)
        assertTrue(layout.scenes.isEmpty())
    }

    @Test
    fun midCreditsAndPostCreditsScenesAreBothFound() {
        // film, 60 s of credits, a 30 s scene, 120 s of credits, a 20 s scene to the very end
        val layout = layout(".".repeat(6) + "C".repeat(12) + ".".repeat(6) + "C".repeat(24) + ".".repeat(4))
        assertNotNull(layout)
        assertEquals(6 * STEP, layout!!.creditsStartMs)
        assertEquals(
            listOf(
                PostCreditsScene(18 * STEP, 24 * STEP),
                PostCreditsScene(48 * STEP, 52 * STEP)
            ),
            layout.scenes
        )
    }

    @Test
    fun twoBrightFramesInsideTheRollAreNotAScene() {
        // The tail of Eternals as sampled on the device: two lone bright frames 20 s apart in the
        // roll used to come out as a 25-second "scene"; the real one after the roll stays.
        val layout = layout(".".repeat(10) + "C".repeat(53) + ".CCC." + "C".repeat(23) + ".....C..C..C.." + "C".repeat(7))
        assertNotNull(layout)
        assertEquals(10 * STEP, layout!!.creditsStartMs)
        assertEquals(listOf(PostCreditsScene(91 * STEP, 105 * STEP)), layout.scenes)
    }

    @Test
    fun wordCountsReadColouredTitlesAndADarkSceneTheRightWayRound() {
        // Words per frame over the last 25 minutes of Eternals, as logged on the device (base 36,
        // z = 35+): coloured titles, a dark mid-credits scene, the roll, a second scene, end cards.
        // By blackness the first scene was lost; by words both come out where the user says they are.
        val words = "000000000000000000000000000000000000000003000000000000000000000080000000000000002000000000050000000010000020" +
            "000000000000000000000000000000000000e7857186476554202233222533333100060000000000000000000000057354" +
            "z".repeat(70) + "dz000000000000000570ztut"
        val samples = words.mapIndexed { index, char -> FrameSample(index * STEP, Character.digit(char, 36) >= MIN_CREDIT_WORDS) }
        val layout = findCreditsLayout(samples, words.length * STEP)
        assertNotNull(layout)
        assertEquals(144 * STEP, layout!!.creditsStartMs)
        assertEquals(
            listOf(PostCreditsScene(173 * STEP, 201 * STEP), PostCreditsScene(278 * STEP, 296 * STEP)),
            layout.scenes
        )
    }

    @Test
    fun logoInsideTheCreditsIsNotAScene() {
        // two bright samples (10 s) in the middle of the roll - shorter than a scene
        val layout = layout(".".repeat(6) + "C".repeat(20) + ".." + "C".repeat(20))
        assertNotNull(layout)
        assertTrue(layout!!.scenes.isEmpty())
    }

    @Test
    fun shortDarkShotInTheFilmIsNotTheCredits() {
        // 15 s of darkness mid-film, then the real roll later
        val layout = layout("....CCC........" + "C".repeat(30))
        assertNotNull(layout)
        assertEquals(15 * STEP, layout!!.creditsStartMs)
        assertTrue(layout.scenes.isEmpty())
    }

    @Test
    fun darkFinaleLongBeforeTheRollIsNotTheCredits() {
        // 70 s of darkness, 15 minutes of film, a 7-minute roll, a 55 s scene to the end - the
        // shape of the first real film this ran on, where the finale was taken for the credits.
        val layout = layout("C".repeat(14) + ".".repeat(180) + "C".repeat(84) + ".".repeat(11))
        assertNotNull(layout)
        assertEquals(194 * STEP, layout!!.creditsStartMs)
        assertEquals(listOf(PostCreditsScene(278 * STEP, 289 * STEP)), layout.scenes)
    }

    @Test
    fun episodeCreditsAreTheBlackRollRunningToTheEnd() {
        fun start(pattern: String) = findEpisodeCreditsStart(samples(pattern), pattern.length * STEP)
        // 40 s of credits at the very end
        assertEquals(52 * STEP, start(".".repeat(52) + "C".repeat(8)))
        // ...with a 10 s logo after them
        assertEquals(50 * STEP, start(".".repeat(50) + "C".repeat(8) + ".."))
        // a dark scene mid-episode, picture again to the end - no credits
        assertNull(start(".".repeat(20) + "C".repeat(10) + ".".repeat(30)))
        // a preview after the credits: left unmarked rather than guessed
        assertNull(start(".".repeat(40) + "C".repeat(8) + ".".repeat(12)))
        // too short to be a roll
        assertNull(start(".".repeat(58) + "CC"))
        // black for the whole window - nothing to anchor a start to
        assertNull(start("C".repeat(60)))
    }

    @Test
    fun filmWithNoBlackRollGivesNothing() {
        assertNull(layout(".".repeat(60)))
        // a dark stretch too short to be a roll
        assertNull(layout(".".repeat(20) + "C".repeat(6) + ".".repeat(20)))
    }

    @Test
    fun blackFrameWithTextIsCreditLike_ordinaryFrameIsNot() {
        val width = 160
        val height = 90
        val black = IntArray(width * height) { 0xFF000000.toInt() }
        // a column of white "text" down the middle third
        val credits = black.copyOf().also { pixels ->
            for (y in 0 until height step 3) for (x in 60 until 100) pixels[y * width + x] = 0xFFFFFFFF.toInt()
        }
        val picture = IntArray(width * height) { 0xFF5A6E82.toInt() }
        // a 2.39:1 picture inside a 16:9 frame: black bars top and bottom, picture in between
        val letterboxed = picture.copyOf().also { pixels ->
            for (y in 0 until height) if (y < 11 || y >= 79) for (x in 0 until width) pixels[y * width + x] = 0xFF000000.toInt()
        }
        assertTrue(isCreditLikeFrame(black, width, height))
        assertTrue(isCreditLikeFrame(credits, width, height))
        assertFalse(isCreditLikeFrame(picture, width, height))
        assertFalse(isCreditLikeFrame(letterboxed, width, height))
    }

    @Test
    fun scenesSurviveARoundTripThroughTheColumnFormat() {
        val scenes = listOf(PostCreditsScene(7_084_000, 7_141_000), PostCreditsScene(7_702_000))
        val encoded = encodePostCreditsScenes(scenes)
        assertEquals("7084000-7141000,7702000", encoded)
        assertEquals(scenes, parsePostCreditsScenes(encoded, legacyStartMs = null))
    }

    @Test
    fun aMarkFromAnOlderBuildIsReadFromTheLegacyColumn() {
        assertEquals(listOf(PostCreditsScene(5_000)), parsePostCreditsScenes(null, legacyStartMs = 5_000))
        // the new column wins once it has anything in it
        assertEquals(listOf(PostCreditsScene(9_000)), parsePostCreditsScenes("9000", legacyStartMs = 5_000))
        assertTrue(parsePostCreditsScenes(null, legacyStartMs = null).isEmpty())
        assertNull(encodePostCreditsScenes(emptyList()))
    }

    @Test
    fun malformedEntriesAreSkippedNotThrown() {
        assertEquals(listOf(PostCreditsScene(3_000)), parsePostCreditsScenes("abc,,3000-oops,-7", legacyStartMs = null))
    }

    private companion object {
        const val STEP = 5_000L
    }
}
