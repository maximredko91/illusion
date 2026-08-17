package com.seance.app.data.nfo

import android.util.Xml
import java.io.OutputStream

/**
 * Writes Kodi-style .nfo XML - the write-side counterpart to [NfoParser], emitting exactly the
 * tag set that parser reads back (see its own field-by-field comments). Only ever used by the
 * developer-only "add media" scraper (`ui/addmedia`) - scanning/playback never write NFOs.
 * Images (poster/fanart) are written as separate local files by the same flow, never embedded
 * here as remote `<thumb>` URLs - this app is offline-first, so a freshly-added item should never
 * need a live internet fetch again after this one-time write.
 */
class NfoWriter {

    fun writeMovie(output: OutputStream, metadata: NfoMetadata) = write(output, "movie", metadata)

    fun writeTvShow(output: OutputStream, metadata: NfoMetadata) = write(output, "tvshow", metadata)

    fun writeEpisode(output: OutputStream, metadata: NfoMetadata) = write(output, "episodedetails", metadata)

    private fun write(output: OutputStream, rootTag: String, metadata: NfoMetadata) {
        val serializer = Xml.newSerializer()
        serializer.setOutput(output, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, rootTag)

        metadata.title?.let { tag(serializer, "title", it) }
        metadata.originalTitle?.let { tag(serializer, "originaltitle", it) }
        metadata.year?.let { tag(serializer, "year", it.toString()) }
        metadata.genres.forEach { tag(serializer, "genre", it) }
        metadata.rating?.let { tag(serializer, "rating", it.toString()) }
        metadata.plot?.let { tag(serializer, "plot", it) }
        metadata.director.forEach { tag(serializer, "director", it) }
        metadata.actors.forEach { name ->
            serializer.startTag(null, "actor")
            tag(serializer, "name", name)
            serializer.endTag(null, "actor")
        }
        metadata.country?.let { tag(serializer, "country", it) }
        metadata.runtimeMinutes?.let { tag(serializer, "runtime", it.toString()) }
        metadata.collectionName?.let { name ->
            serializer.startTag(null, "set")
            tag(serializer, "name", name)
            serializer.endTag(null, "set")
        }
        metadata.season?.let { tag(serializer, "season", it.toString()) }
        metadata.episode?.let { tag(serializer, "episode", it.toString()) }
        metadata.mpaa?.let { tag(serializer, "mpaa", it) }
        metadata.tagline?.let { tag(serializer, "tagline", it) }
        metadata.studio?.let { tag(serializer, "studio", it) }
        metadata.premiered?.let { tag(serializer, "premiered", it) }
        metadata.imdbId?.let { uniqueId(serializer, "imdb", it) }
        metadata.tmdbId?.let {
            uniqueId(serializer, "tmdb", it)
            tag(serializer, "tmdbid", it)
        }

        serializer.endTag(null, rootTag)
        serializer.endDocument()
        serializer.flush()
    }

    private fun tag(serializer: org.xmlpull.v1.XmlSerializer, name: String, value: String) {
        serializer.startTag(null, name)
        serializer.text(value)
        serializer.endTag(null, name)
    }

    private fun uniqueId(serializer: org.xmlpull.v1.XmlSerializer, type: String, value: String) {
        serializer.startTag(null, "uniqueid")
        serializer.attribute(null, "type", type)
        serializer.text(value)
        serializer.endTag(null, "uniqueid")
    }
}
