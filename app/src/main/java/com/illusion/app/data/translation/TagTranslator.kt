package com.illusion.app.data.translation

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device English->Russian translation for freeform .nfo <tag> values (unlike <genre>, tags
 * aren't a small fixed vocabulary - see GenreTranslation.kt's static map - so that approach
 * can't cover them). The model downloads once (needs network for that one time, same as
 * TmdbClient's own one-time-online moment) and translates fully offline afterward - not a
 * standing exception to this app's offline-first architecture, just a one-time setup cost.
 */
class TagTranslator {
    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build()
    )

    // Tags are stable (an .nfo's <tag> list doesn't change on its own), and re-translating the
    // same word on every screen visit would be wasted work even though it's local - a simple
    // in-memory cache for this process's lifetime is enough, no need to persist it.
    private val cache = mutableMapOf<String, String>()

    /** Null if the model isn't downloaded yet and the download fails (e.g. no network at all, or Play Services unavailable) - callers fall back to the original English tag in that case. */
    suspend fun translate(tag: String): String? {
        cache[tag]?.let { return it }
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val result = translator.translate(tag).await()
            cache[tag] = result
            result
        } catch (e: Exception) {
            null
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
