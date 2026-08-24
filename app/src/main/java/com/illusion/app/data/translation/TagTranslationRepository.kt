package com.illusion.app.data.translation

import com.illusion.app.data.local.dao.TagTranslationDao
import com.illusion.app.data.local.entity.TagTranslationEntity
import com.illusion.app.data.local.entity.TranslationSource
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/** Result of a manual "upgrade to DeepL" pass from Settings - lets the UI tell the difference between "translated N tags" and "nothing left to do", instead of silently no-oping on a repeat tap. */
sealed interface DeepLUpgradeResult {
    data class Upgraded(val count: Int) : DeepLUpgradeResult
    object AlreadyUpToDate : DeepLUpgradeResult
    object NoApiKey : DeepLUpgradeResult
}

/** (translated so far, total to translate) - reported as [upgradeAllToDeepL] works through the batches, so Settings can show real progress instead of a bare spinner for what can be a multi-minute operation on a large library. */
data class DeepLUpgradeProgress(val done: Int, val total: Int)

/**
 * Single source of truth for tag labels shown in TagsScreen. Every tag is translated at most
 * once per engine, ever - results persist in Room ([TagTranslationDao]), so neither a fresh app
 * launch nor revisiting the screen re-runs anything. Two tiers, both opt-in from the caller's
 * side (never auto-triggered by a scan - see this class's own call sites):
 * - [translateLazily]: the default. Runs ML Kit on-device, called by TagsViewModel as each tag
 *   actually gets displayed. Fully offline once its one-time model download has happened.
 * - [upgradeAllToDeepL]: only invoked from a Settings button. Re-translates every tag NOT already
 *   DeepL-sourced through the (more accurate, but quota-limited and network-dependent) DeepL API,
 *   batched (see DeepLClient.BATCH_SIZE) rather than one request per tag - an earlier one-call-
 *   per-tag version was confirmed on-device to take 20+ minutes on a library with thousands of
 *   distinct tags. Reports [DeepLUpgradeResult.AlreadyUpToDate] instead of making any network
 *   calls at all when every known tag already has a DeepL translation.
 */
class TagTranslationRepository(
    private val dao: TagTranslationDao,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val mlKitTranslator: TagTranslator,
    private val deepLClient: DeepLClient
) {
    suspend fun getCached(): Map<String, String> =
        dao.getAll().associate { it.tag to it.translation }

    /** Translates one tag via ML Kit and persists it, unless already cached (from either engine - a prior DeepL upgrade is never overwritten by a plain ML Kit lookup). Returns the translation, or the original English tag if translation isn't possible right now (e.g. no network for the model's one-time download). */
    suspend fun translateLazily(tag: String): String {
        dao.getOne(tag)?.let { return it.translation }
        val translated = mlKitTranslator.translate(tag) ?: return tag
        dao.upsert(TagTranslationEntity(tag, translated, TranslationSource.MLKIT))
        return translated
    }

    /** Manual, Settings-triggered - see this class's own KDoc. [onProgress] fires after every batch. */
    suspend fun upgradeAllToDeepL(onProgress: suspend (DeepLUpgradeProgress) -> Unit = {}): DeepLUpgradeResult {
        val apiKey = settingsRepository.deeplApiKey.first()
        if (apiKey.isNullOrBlank()) return DeepLUpgradeResult.NoApiKey

        val allTags = libraryRepository.getAll().flatMap { it.tags }.distinct()
        val cached = dao.getAll().associateBy { it.tag }
        val needsUpgrade = allTags.filter { cached[it]?.source != TranslationSource.DEEPL }
        if (needsUpgrade.isEmpty()) return DeepLUpgradeResult.AlreadyUpToDate

        var upgraded = 0
        needsUpgrade.chunked(DeepLClient.BATCH_SIZE).forEach { batch ->
            val translations = deepLClient.translateBatch(batch)
            if (translations != null) {
                val entities = batch.zip(translations).mapNotNull { (tag, translation) ->
                    translation?.let { TagTranslationEntity(tag, it, TranslationSource.DEEPL) }
                }
                dao.upsertAll(entities)
                upgraded += entities.size
            }
            onProgress(DeepLUpgradeProgress(upgraded, needsUpgrade.size))
        }
        return DeepLUpgradeResult.Upgraded(upgraded)
    }
}
