package com.illusion.app.domain.model

/**
 * Some .nfo sources (mainly TMDB-scraped ones, including this app's own developer-only add-media
 * flow) write `<genre>` in English ("Action", "Science Fiction"), while others (manually-authored
 * or scraped from Russian databases) write it in Russian ("Боевик", "Фантастика") - both live side
 * by side in the same library with no normalization. A Russian-speaking user searching "боевик"
 * found nothing for English-tagged items, since the search query only ever matched literally.
 *
 * A static map is enough here (not a translation API call) - genre is a small, fixed vocabulary
 * (the ~19 TMDB genre names), not free-form text worth a real MT service for.
 */
private val RU_TO_EN_GENRES: Map<String, String> = mapOf(
    "боевик" to "Action",
    "экшн" to "Action",
    "приключения" to "Adventure",
    "приключение" to "Adventure",
    "мультфильм" to "Animation",
    "анимация" to "Animation",
    "комедия" to "Comedy",
    "криминал" to "Crime",
    "документальный" to "Documentary",
    "документалистика" to "Documentary",
    "драма" to "Drama",
    "семейный" to "Family",
    "фэнтези" to "Fantasy",
    "история" to "History",
    "исторический" to "History",
    "ужасы" to "Horror",
    "хоррор" to "Horror",
    "музыка" to "Music",
    "музыкальный" to "Music",
    "детектив" to "Mystery",
    "мелодрама" to "Romance",
    "романтика" to "Romance",
    "фантастика" to "Science Fiction",
    "телефильм" to "TV Movie",
    "триллер" to "Thriller",
    "военный" to "War",
    "вестерн" to "Western"
)

/** Genre only, not a general-purpose translator - returns null for anything not a known genre synonym. */
fun russianGenreToEnglish(query: String): String? = RU_TO_EN_GENRES[query.trim().lowercase()]

/** Reverse of [RU_TO_EN_GENRES], one-to-many (several Russian spellings can map to the same English name, e.g. "боевик"/"экшн" -> "Action"). */
private val EN_TO_RU_GENRES: Map<String, List<String>> = RU_TO_EN_GENRES.entries.groupBy({ it.value }, { it.key })

/**
 * Normalizes a genre string to its canonical (TMDB English) name if it's a known synonym in
 * either language, so items scraped in different languages (e.g. this app's own TMDB add-media
 * flow, English, vs. a Russian-scraped .nfo) still count as "the same genre" when scoring
 * similarity - see [LibraryRepository.getSimilar]. Falls back to the trimmed original for
 * anything outside the fixed ~19-genre vocabulary.
 */
fun canonicalGenre(name: String): String {
    val trimmed = name.trim()
    return russianGenreToEnglish(trimmed) ?: trimmed
}

/**
 * Every known spelling of [name]'s genre across both languages - itself plus its English
 * translation if [name] is a Russian synonym, or itself plus every Russian synonym if [name] is
 * already the canonical English name. Used to widen a genre-based SQL prefilter so it isn't blind
 * to a differently-scraped candidate meaning the same genre - the precise match still happens
 * afterward via [canonicalGenre].
 */
fun genreSynonyms(name: String): List<String> {
    val trimmed = name.trim()
    val english = russianGenreToEnglish(trimmed)
    return if (english != null) listOf(trimmed, english) else listOf(trimmed) + EN_TO_RU_GENRES[trimmed].orEmpty()
}
