package com.illusion.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted so a tag is only ever translated once (per source), never re-run on the next app
 * launch/screen visit - see TagTranslationRepository. [source] distinguishes an ML Kit result
 * from a DeepL one, since the "improve via DeepL" Settings action needs to know which tags still
 * only have the offline/cheaper translation and skip the ones already upgraded, instead of
 * silently re-spending API quota on tags that already have the better result.
 */
@Entity(tableName = "tag_translations")
data class TagTranslationEntity(
    @PrimaryKey val tag: String,
    val translation: String,
    val source: TranslationSource
)

enum class TranslationSource { MLKIT, DEEPL }
