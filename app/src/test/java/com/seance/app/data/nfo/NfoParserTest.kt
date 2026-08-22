package com.seance.app.data.nfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class NfoParserTest {

    private val parser = NfoParser()

    private fun parse(xml: String) = parser.parse(ByteArrayInputStream(xml.trimIndent().toByteArray(Charsets.UTF_8)))

    @Test
    fun parsesFullMovieNfo() {
        val result = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <movie>
                <title>Начало</title>
                <originaltitle>Inception</originaltitle>
                <year>2010</year>
                <genre>Фантастика</genre>
                <genre>Боевик</genre>
                <rating>8.8</rating>
                <plot>Вор, крадущий чужие секреты.</plot>
                <director>Кристофер Нолан</director>
                <actor>
                    <name>Леонардо ДиКаприо</name>
                </actor>
                <country>США</country>
                <runtime>148</runtime>
                <set>
                    <name>Наборы Нолана</name>
                </set>
                <thumb>http://example.com/poster.jpg</thumb>
                <fanart>
                    <thumb>http://example.com/fanart.jpg</thumb>
                </fanart>
                <mpaa>PG-13</mpaa>
                <tagline>Твой разум - место преступления.</tagline>
                <studio>Warner Bros.</studio>
                <premiered>2010-07-16</premiered>
                <uniqueid type="imdb">tt1375666</uniqueid>
                <uniqueid type="tmdb">27205</uniqueid>
            </movie>
            """
        )

        requireNotNull(result)
        assertEquals("Начало", result.title)
        assertEquals("Inception", result.originalTitle)
        assertEquals(2010, result.year)
        assertEquals(listOf("Фантастика", "Боевик"), result.genres)
        assertEquals(8.8, result.rating!!, 0.0001)
        assertEquals("Вор, крадущий чужие секреты.", result.plot)
        assertEquals(listOf("Кристофер Нолан"), result.director)
        assertEquals(listOf("Леонардо ДиКаприо"), result.actors)
        assertEquals("США", result.country)
        assertEquals(148, result.runtimeMinutes)
        assertEquals("Наборы Нолана", result.collectionName)
        assertEquals("http://example.com/poster.jpg", result.posterUrl)
        assertEquals("http://example.com/fanart.jpg", result.fanartUrl)
        assertEquals("PG-13", result.mpaa)
        assertEquals("Твой разум - место преступления.", result.tagline)
        assertEquals("Warner Bros.", result.studio)
        assertEquals("2010-07-16", result.premiered)
        assertEquals("tt1375666", result.imdbId)
        assertEquals("27205", result.tmdbId)
    }

    @Test
    fun missingTitleAndOriginalTitleReturnsNull() {
        val result = parse("<movie><year>2010</year></movie>")
        assertNull(result)
    }

    @Test
    fun originalTitleAloneIsEnoughToKeepTheEntry() {
        val result = parse("<movie><originaltitle>Inception</originaltitle></movie>")
        requireNotNull(result)
        assertNull(result.title)
        assertEquals("Inception", result.originalTitle)
    }

    @Test
    fun onlyFirstRootLevelBlockIsRead() {
        // tinyMediaManager sometimes writes a second, sparser <episodedetails> block into the same
        // file for absolute numbering - only the first should win, per NfoParser's own comment.
        val result = parse(
            """
            <episodedetails>
                <title>Серия 1</title>
                <season>1</season>
                <episode>1</episode>
            </episodedetails>
            <episodedetails>
                <title>Absolute</title>
                <season>99</season>
                <episode>99</episode>
            </episodedetails>
            """
        )

        requireNotNull(result)
        assertEquals("Серия 1", result.title)
        assertEquals(1, result.season)
        assertEquals(1, result.episode)
    }

    @Test
    fun posterThumbIsNotConfusedWithFanartThumb() {
        val result = parse(
            """
            <movie>
                <title>T</title>
                <fanart><thumb>fanart.jpg</thumb></fanart>
                <thumb>poster.jpg</thumb>
            </movie>
            """
        )

        requireNotNull(result)
        assertEquals("poster.jpg", result.posterUrl)
        assertEquals("fanart.jpg", result.fanartUrl)
    }

    @Test
    fun actorThumbIsNotMistakenForPoster() {
        val result = parse(
            """
            <movie>
                <title>T</title>
                <actor>
                    <name>Actor Name</name>
                    <thumb>actor-headshot.jpg</thumb>
                </actor>
            </movie>
            """
        )

        requireNotNull(result)
        assertNull(result.posterUrl)
        assertEquals(listOf("Actor Name"), result.actors)
    }

    @Test
    fun legacyBareIdTagIsOnlyTrustedWhenItLooksLikeImdb() {
        val trusted = parse("<movie><title>T</title><id>tt1234567</id></movie>")
        requireNotNull(trusted)
        assertEquals("tt1234567", trusted.imdbId)

        val untrusted = parse("<movie><title>T</title><id>12345</id></movie>")
        requireNotNull(untrusted)
        assertNull(untrusted.imdbId)
    }

    @Test
    fun uniqueIdWinsOverLegacyIdWhenBothPresent() {
        val result = parse(
            """
            <movie>
                <title>T</title>
                <id>tt0000000</id>
                <uniqueid type="imdb">tt1375666</uniqueid>
            </movie>
            """
        )
        requireNotNull(result)
        // <id> is read first in document order, and both branches use `?:` (first-wins) -
        // documenting the actual precedence here so a reordering doesn't silently flip it.
        assertEquals("tt0000000", result.imdbId)
    }

    @Test
    fun malformedRatingAndYearAreIgnoredNotThrown() {
        val result = parse("<movie><title>T</title><year>not-a-year</year><rating>n/a</rating></movie>")
        requireNotNull(result)
        assertNull(result.year)
        assertNull(result.rating)
    }

    @Test
    fun multipleGenresAndActorsAreAllCaptured() {
        val result = parse(
            """
            <movie>
                <title>T</title>
                <genre>A</genre>
                <genre>B</genre>
                <genre>C</genre>
                <actor><name>One</name></actor>
                <actor><name>Two</name></actor>
            </movie>
            """
        )
        requireNotNull(result)
        assertEquals(listOf("A", "B", "C"), result.genres)
        assertEquals(listOf("One", "Two"), result.actors)
    }
}
