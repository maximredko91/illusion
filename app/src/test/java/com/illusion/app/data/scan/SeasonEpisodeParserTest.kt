package com.illusion.app.data.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonEpisodeParserTest {

    private fun parsed(path: String) = parseSeasonEpisode(path)

    @Test
    fun seasonEpisodeTagInTheFileName() {
        assertEquals(SeasonEpisode(1, 3), parsed("""Shows\Сериал\Season 01\S01E03.mkv"""))
        assertEquals(SeasonEpisode(2, 11), parsed("Shows/Show/Show.s02e11.1080p.WEB-DL.x264.mkv"))
        assertEquals(SeasonEpisode(1, 5), parsed("""Shows\Show\Show - S01.E05 - Title.mp4"""))
        // the tag in the name wins over a folder that says otherwise
        assertEquals(SeasonEpisode(3, 2), parsed("""Shows\Show\Season 1\Show S03E02.mkv"""))
    }

    @Test
    fun seasonXEpisodeForm() {
        assertEquals(SeasonEpisode(1, 3), parsed("""Shows\Show\Show 1x03 Title.avi"""))
        assertEquals(SeasonEpisode(12, 105), parsed("Shows/Show/12x105.mkv"))
    }

    @Test
    fun seasonFromTheFolderEpisodeFromTheName() {
        assertEquals(SeasonEpisode(2, 7), parsed("""Shows\Сериал\Сезон 2\Серия 7.mkv"""))
        assertEquals(SeasonEpisode(2, 7), parsed("""Shows\Сериал\2 сезон\7 серия.mkv"""))
        assertEquals(SeasonEpisode(1, 4), parsed("Shows/Show/Season 1/Episode 4 - Title.mkv"))
        assertEquals(SeasonEpisode(4, 9), parsed("Shows/Show/S04/Show.E09.mkv"))
        // a bare leading number counts only inside a season folder
        assertEquals(SeasonEpisode(1, 3), parsed("Shows/Show/Season 01/03 - Pilot.mkv"))
        assertEquals(SeasonEpisode(1, 12), parsed("""Shows\Show\Season 1\12.mkv"""))
    }

    @Test
    fun resolutionCodecAndYearAreNotEpisodes() {
        assertEquals(SeasonEpisode(null, null), parsed("Shows/Show/Show.2019.1080p.x264.mkv"))
        assertEquals(SeasonEpisode(null, null), parsed("Shows/Show/1920x1080 sample.mkv"))
        assertEquals(SeasonEpisode(null, null), parsed("""Shows\Show\03 - Pilot.mkv"""))
        // in a season folder, a file with no number still gets its season
        assertEquals(SeasonEpisode(1, null), parsed("Shows/Show/Season 1/Pilot.1080p.mkv"))
    }

    @Test
    fun wordsThatMerelyContainTheMarkersAreNotMatched() {
        // "Paris01E03" has the tag glued to a word; "Seven" starts with s but isn't a season tag
        assertEquals(SeasonEpisode(null, null), parsed("Shows/Show/Paris01E03.mkv"))
        assertEquals(SeasonEpisode(null, null), parsed("Shows/Seven/Seven.mkv"))
        // "Space 1999" - "e" glued to a word, then a year
        assertEquals(SeasonEpisode(null, null), parsed("Shows/Space 1999/Space 1999.mkv"))
    }
}
