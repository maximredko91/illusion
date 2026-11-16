package com.illusion.app.data.scan

/** Season and episode numbers read off a path; either may be missing. */
data class SeasonEpisode(val season: Int?, val episode: Int?)

/**
 * Season and episode numbers from an episode's file name and folders - the fallback for a series
 * laid out without per-episode `.nfo` files, where [LibraryScanner] otherwise has no season at
 * all (and everything keyed on one - season grouping, intro and credits search - skips the show).
 * An `.nfo` always wins; this is only asked when it has nothing.
 *
 * Understands, in this order: `S01E03` / `s1.e3` and `1x03` in the file name; otherwise the
 * season from the nearest "Season 1" / "Сезон 1" / "S01" / "1 сезон" folder, and the episode from
 * "E03" / "Ep 3" / "Серия 3" / "3 серия" in the file name or, inside such a season folder only,
 * from a leading number ("03 - Pilot"). A bare leading number without a season folder is not
 * trusted - it's as likely a year fragment or part of the title.
 *
 * [path] may use either separator; the last segment is the file name, with or without extension.
 */
fun parseSeasonEpisode(path: String): SeasonEpisode {
    val segments = path.split('/', '\\').filter { it.isNotEmpty() }
    val fileName = segments.lastOrNull()?.substringBeforeLast('.', segments.last()) ?: return SeasonEpisode(null, null)

    SEASON_EPISODE_TAG.find(fileName)?.let { match ->
        return SeasonEpisode(match.groupValues[1].toIntOrNull(), match.groupValues[2].toIntOrNull())
    }
    SEASON_X_EPISODE.find(fileName)?.let { match ->
        return SeasonEpisode(match.groupValues[1].toIntOrNull(), match.groupValues[2].toIntOrNull())
    }

    val season = segments.dropLast(1).asReversed().firstNotNullOfOrNull { folder ->
        (SEASON_FOLDER.find(folder) ?: SEASON_FOLDER_NUMBER_FIRST.find(folder))?.groupValues?.get(1)?.toIntOrNull()
    }
    val episode = (EPISODE_WORD.find(fileName) ?: EPISODE_WORD_NUMBER_FIRST.find(fileName))?.groupValues?.get(1)?.toIntOrNull()
        ?: if (season != null) LEADING_NUMBER.find(fileName)?.groupValues?.get(1)?.toIntOrNull() else null
    return SeasonEpisode(season, episode)
}

// "S01E03", "s1e3", "S01.E03", "S01 E03" - not preceded by a letter or digit, so "Paris01E03"
// style accidents and resolution/codec tags don't qualify.
private val SEASON_EPISODE_TAG = Regex("""(?<![\p{L}\d])s(\d{1,2})[ ._-]?e(\d{1,3})(?!\d)""", RegexOption.IGNORE_CASE)

// "1x03", "12x105". Two or three digits after the x keeps "x264"/"x265" out: those are preceded
// by a separator, not by the one or two digits this needs right before the x.
private val SEASON_X_EPISODE = Regex("""(?<!\d)(\d{1,2})x(\d{2,3})(?!\d)""", RegexOption.IGNORE_CASE)

// A folder that IS a season: "Season 1", "Season.01", "Сезон 1", "S01".
private val SEASON_FOLDER = Regex("""^(?:season|сезон|s)[ ._-]?(\d{1,2})$""", RegexOption.IGNORE_CASE)

// "1 сезон", "2 season".
private val SEASON_FOLDER_NUMBER_FIRST = Regex("""^(\d{1,2})[ ._-]?(?:season|сезон)$""", RegexOption.IGNORE_CASE)

// "E03", "Ep 3", "Episode 3", "Серия 3", "Эпизод 3" - the marker not glued to a preceding letter
// or digit. (Case-insensitivity comes from RegexOption.IGNORE_CASE, which Kotlin always pairs with
// Unicode case folding so "Сезон" matches "сезон". NOT from an inline (?iu): the first
// version spelled it that way, passed every JVM unit test, and the library scan failed on the
// device; it worked once the inline flags were gone. Android's regex engine is ICU, not the
// JDK's, so the likely cause is the "u" flag - the exception itself wasn't captured.)
private val EPISODE_WORD = Regex("""(?<![\p{L}\d])(?:episode|ep|e|серия|эпизод)[ ._-]?(\d{1,3})(?!\d)""", RegexOption.IGNORE_CASE)

// "3 серия", "12 эпизод".
private val EPISODE_WORD_NUMBER_FIRST = Regex("""(?<!\d)(\d{1,3})[ ._-]?(?:серия|эпизод)(?!\p{L})""", RegexOption.IGNORE_CASE)

// "03 - Pilot", "3. Pilot", "12" - one to three digits, so "1080p" and years don't qualify.
private val LEADING_NUMBER = Regex("""^(\d{1,3})(?=[ ._-]|$)""")
