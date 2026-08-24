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
