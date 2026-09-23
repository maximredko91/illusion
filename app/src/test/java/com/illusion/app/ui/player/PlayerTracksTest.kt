package com.illusion.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerTracksTest {

    @Test
    fun languageFromSidecarSubtitleName() {
        assertEquals("ru", guessLanguage("Престиж.ru.srt"))
        assertEquals("eng", guessLanguage("Prestige.2006.ENG.srt"))
        assertNull(guessLanguage("Престиж.srt"))
        assertNull(guessLanguage("Prestige.2006.srt"))
        assertNull(guessLanguage("Prestige.forced.srt"))
    }
}
