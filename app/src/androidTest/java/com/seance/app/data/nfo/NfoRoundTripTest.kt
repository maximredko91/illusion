package com.seance.app.data.nfo

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * NfoWriter uses android.util.Xml, which is a real device/emulator API (a plain JVM unit test
 * gets the "not mocked" stub) - this verifies the write side actually round-trips through the
 * read side, i.e. NfoWriter emits exactly the tag set NfoParser reads back, per NfoWriter's KDoc.
 */
@RunWith(AndroidJUnit4::class)
class NfoRoundTripTest {

    private val writer = NfoWriter()
    private val parser = NfoParser()

    private fun roundTrip(metadata: NfoMetadata): NfoMetadata? {
        val output = ByteArrayOutputStream()
        writer.writeMovie(output, metadata)
        return parser.parse(ByteArrayInputStream(output.toByteArray()))
    }

    @Test
    fun fullMetadataSurvivesRoundTrip() {
        val original = NfoMetadata(
            title = "Начало",
            originalTitle = "Inception",
            year = 2010,
            genres = listOf("Фантастика", "Боевик"),
            rating = 8.8,
            plot = "Вор, крадущий чужие секреты через сны.",
            director = listOf("Кристофер Нолан"),
            actors = listOf("Леонардо ДиКаприо", "Джозеф Гордон-Левитт"),
            country = "США",
            runtimeMinutes = 148,
            collectionName = "Наборы Нолана",
            season = null,
            episode = null,
            mpaa = "PG-13",
            tagline = "Твой разум - место преступления.",
            studio = "Warner Bros.",
            premiered = "2010-07-16",
            imdbId = "tt1375666",
            tmdbId = "27205"
        )

        val result = roundTrip(original)

        requireNotNull(result)
        assertEquals(original.title, result.title)
        assertEquals(original.originalTitle, result.originalTitle)
        assertEquals(original.year, result.year)
        assertEquals(original.genres, result.genres)
        assertEquals(original.rating, result.rating)
        assertEquals(original.plot, result.plot)
        assertEquals(original.director, result.director)
        assertEquals(original.actors, result.actors)
        assertEquals(original.country, result.country)
        assertEquals(original.runtimeMinutes, result.runtimeMinutes)
        assertEquals(original.collectionName, result.collectionName)
        assertEquals(original.mpaa, result.mpaa)
        assertEquals(original.tagline, result.tagline)
        assertEquals(original.studio, result.studio)
        assertEquals(original.premiered, result.premiered)
        assertEquals(original.imdbId, result.imdbId)
        assertEquals(original.tmdbId, result.tmdbId)
    }

    @Test
    fun episodeMetadataSurvivesRoundTrip() {
        val output = ByteArrayOutputStream()
        val original = NfoMetadata(title = "Серия 1", season = 1, episode = 3)
        writer.writeEpisode(output, original)

        val result = parser.parse(ByteArrayInputStream(output.toByteArray()))

        requireNotNull(result)
        assertEquals(1, result.season)
        assertEquals(3, result.episode)
    }

    @Test
    fun minimalMetadataWithOnlyTitleSurvivesRoundTrip() {
        val result = roundTrip(NfoMetadata(title = "T"))
        requireNotNull(result)
        assertEquals("T", result.title)
        assertEquals(emptyList<String>(), result.genres)
    }
}
