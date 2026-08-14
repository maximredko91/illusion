package com.seance.app.data.nfo

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parses Kodi-style .nfo files (movie / tvshow / episodedetails root tags).
 */
class NfoParser {

    fun parse(input: InputStream): NfoMetadata? {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        var title: String? = null
        var originalTitle: String? = null
        var year: Int? = null
        val genres = mutableListOf<String>()
        var rating: Double? = null
        var plot: String? = null
        val directors = mutableListOf<String>()
        val actors = mutableListOf<String>()
        var country: String? = null
        var runtime: Int? = null
        var collectionName: String? = null
        var posterUrl: String? = null
        var fanartUrl: String? = null
        var season: Int? = null
        var episode: Int? = null

        var inActor = false
        var inSet = false
        var inFanart = false
        var currentActorName: String? = null
        var currentTag: String? = null

        // tinyMediaManager (and other tools) sometimes write more than one root-level
        // <episodedetails>/<movie> block into the same .nfo - e.g. one for aired order and a
        // second, much sparser one for absolute numbering, both describing the same file. Only
        // the first block is the real one; stop as soon as it closes instead of letting a later
        // block silently overwrite good values (title, season, episode) with placeholder ones.
        var depth = 0
        var event = parser.eventType
        loop@ while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    currentTag = parser.name
                    when (currentTag) {
                        "actor" -> inActor = true
                        "set" -> inSet = true
                        "fanart" -> inFanart = true
                        // Kodi nests the backdrop as <fanart><thumb>url</thumb></fanart> - a
                        // sibling of the top-level poster <thumb> elements, not a variant of them.
                        "thumb" -> when {
                            inFanart -> if (fanartUrl == null) fanartUrl = readTextSafely(parser)
                            !inActor -> if (posterUrl == null) posterUrl = readTextSafely(parser)
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim()
                    if (!text.isNullOrEmpty()) {
                        when (currentTag) {
                            "title" -> title = text
                            "originaltitle" -> originalTitle = text
                            "year" -> year = text.toIntOrNull()
                            "genre" -> genres.add(text)
                            "rating", "value" -> rating = rating ?: text.toDoubleOrNull()
                            "plot" -> plot = text
                            "director" -> directors.add(text)
                            "name" -> when {
                                inActor -> currentActorName = text
                                inSet -> collectionName = text
                            }
                            "country" -> country = text
                            "runtime" -> runtime = text.toIntOrNull()
                            "season" -> season = text.toIntOrNull()
                            "episode" -> episode = text.toIntOrNull()
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "actor" -> {
                            currentActorName?.let { actors.add(it) }
                            currentActorName = null
                            inActor = false
                        }
                        "set" -> inSet = false
                        "fanart" -> inFanart = false
                    }
                    currentTag = null
                    depth--
                    if (depth == 0) break@loop
                }
            }
            event = parser.next()
        }

        if (title == null && originalTitle == null) return null

        return NfoMetadata(
            title = title,
            originalTitle = originalTitle,
            year = year,
            genres = genres,
            rating = rating,
            plot = plot,
            director = directors,
            actors = actors,
            country = country,
            runtimeMinutes = runtime,
            collectionName = collectionName,
            posterUrl = posterUrl,
            fanartUrl = fanartUrl,
            season = season,
            episode = episode
        )
    }

    private fun readTextSafely(parser: XmlPullParser): String? = try {
        if (parser.next() == XmlPullParser.TEXT) parser.text?.trim() else null
    } catch (e: Exception) {
        null
    }
}
