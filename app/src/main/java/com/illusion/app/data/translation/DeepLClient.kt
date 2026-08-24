package com.illusion.app.data.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * DeepL's free-tier REST API - explicitly opt-in from Settings (see TagTranslationRepository),
 * never called automatically during a scan. A scan can legitimately run with no internet beyond
 * the local SMB connection (this app is otherwise offline-first apart from TMDB), so nothing
 * scan-triggered should depend on an external API being reachable.
 */
class DeepLClient(private val apiKeyProvider: () -> String?) {
    private val client = OkHttpClient()

    /**
     * Batched - DeepL's API accepts multiple repeated `text` form fields in a single request and
     * returns translations in the same order, so a whole chunk (see [BATCH_SIZE]) costs one round
     * trip instead of one per tag. The very first implementation of this called translate() once
     * per tag - for a library with thousands of distinct tags that meant thousands of sequential
     * HTTP requests, confirmed on-device to take upwards of 20+ minutes for one pass. Returns null
     * (for the whole batch) if no key is set or the request fails outright; a per-item null in the
     * result list means DeepL's response didn't include that many translations back.
     */
    suspend fun translateBatch(texts: List<String>): List<String?>? = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return@withContext null
        try {
            val bodyBuilder = FormBody.Builder()
                .add("source_lang", "EN")
                .add("target_lang", "RU")
            texts.forEach { bodyBuilder.add("text", it) }
            val request = Request.Builder()
                .url("https://api-free.deepl.com/v2/translate")
                .addHeader("Authorization", "DeepL-Auth-Key $apiKey")
                .post(bodyBuilder.build())
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val translations = json.getJSONArray("translations")
                texts.indices.map { i ->
                    if (i < translations.length()) translations.getJSONObject(i).getString("text") else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** True if [apiKeyProvider] currently returns a non-blank key - lets callers gate UI (e.g. Settings' "key activated" label) without duplicating the blank-check. */
    fun hasApiKey(): Boolean = !apiKeyProvider().isNullOrBlank()

    companion object {
        /** DeepL's own documented per-request limit on repeated `text` fields. */
        const val BATCH_SIZE = 50
    }
}
