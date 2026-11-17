package com.illusion.app.data.repository

import com.illusion.app.data.local.entity.FavoriteEntity
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.local.entity.WatchProgressEntity

/**
 * Что даст объединение двух источников. Считается заранее и показывается пользователю числами:
 * объединение необратимо удаляет источник, поэтому вслепую его предлагать нельзя.
 *
 * [unmatched] - элементы удаляемого источника, которым не нашлось двойника. Их отметки перенести
 * некуда, и вместе с элементами они пропадут. Это честная цена, и пользователь должен увидеть её
 * ДО того, как согласится: на одном и том же диске таких быть не должно, а если их много - значит,
 * источники указывают на разные шары и объединять их вообще не следует.
 */
data class SourceMergePlan(
    val matched: Int,
    val unmatched: Int,
    val progressToTransfer: Int,
    val favoritesToTransfer: Int
)

/**
 * Сопоставляет элементы удаляемого источника с их двойниками в оставляемом: ключ - путь к файлу
 * плюс размер. Для одного и того же диска, подключённого в разных местах, путь совпадает буква в
 * букву, а размер отсекает случай, когда по одному пути лежат разные файлы (перезалили в другом
 * качестве). Регистр пути не учитывается: SMB его не различает, а две записи о одном файле могут
 * отличаться регистром, если шару добавляли вручную.
 *
 * Возвращает stableId удаляемого элемента -> stableId того, который останется.
 */
fun pairItemsByFile(from: List<MediaItemEntity>, into: List<MediaItemEntity>): Map<String, String> {
    val byKey = into.associateBy { fileKey(it) }
    return from.mapNotNull { item -> byKey[fileKey(item)]?.let { item.stableId to it.stableId } }.toMap()
}

private fun fileKey(item: MediaItemEntity) = item.filePath.lowercase() to item.sizeBytes

/**
 * Отметки просмотра, которые надо записать оставляемым элементам. Берём отметку удаляемого, только
 * если у двойника её нет или она старее: смотрели в двух местах - выигрывает последний просмотр,
 * иначе объединение откатило бы прогресс назад.
 */
fun progressTransfers(
    pairs: Map<String, String>,
    progressByItem: Map<String, WatchProgressEntity>
): List<WatchProgressEntity> = pairs.mapNotNull { (fromId, intoId) ->
    val source = progressByItem[fromId] ?: return@mapNotNull null
    val target = progressByItem[intoId]
    if (target != null && target.updatedAt >= source.updatedAt) null
    else source.copy(mediaItemStableId = intoId)
}

/** Избранное переносится, если двойник ещё не в избранном. Дата добавления берётся самая ранняя - именно тогда фильм и был отмечен. */
fun favoriteTransfers(
    pairs: Map<String, String>,
    favoritesByItem: Map<String, FavoriteEntity>
): List<FavoriteEntity> = pairs.mapNotNull { (fromId, intoId) ->
    val source = favoritesByItem[fromId] ?: return@mapNotNull null
    val target = favoritesByItem[intoId]
    if (target != null && target.addedAt <= source.addedAt) null
    else FavoriteEntity(intoId, source.addedAt)
}

/**
 * Метки заставки, титров и сцен после титров, которые надо записать оставляемым элементам:
 * возвращает двойников уже с метками, готовых к upsert. Своя метка двойника всегда главнее -
 * переносится только то, чего у него нет. Заставка идёт парой (начало и конец), сцены - обеими
 * колонками сразу: половина чужой метки рядом с половиной своей дала бы диапазон, которого никто
 * не ставил.
 */
fun markerTransfers(from: List<MediaItemEntity>, into: List<MediaItemEntity>): List<MediaItemEntity> {
    val fromById = from.associateBy { it.stableId }
    val intoById = into.associateBy { it.stableId }
    return pairItemsByFile(from, into).mapNotNull { (fromId, intoId) ->
        val source = fromById[fromId] ?: return@mapNotNull null
        val target = intoById[intoId] ?: return@mapNotNull null
        val takeIntro = target.introEndMs == null && source.introEndMs != null
        val takeScenes = target.postCreditsStartMs == null && target.postCreditsScenesMs == null &&
            (source.postCreditsStartMs != null || source.postCreditsScenesMs != null)
        val merged = target.copy(
            introStartMs = if (takeIntro) source.introStartMs else target.introStartMs,
            introEndMs = if (takeIntro) source.introEndMs else target.introEndMs,
            outroStartMs = target.outroStartMs ?: source.outroStartMs,
            postCreditsStartMs = if (takeScenes) source.postCreditsStartMs else target.postCreditsStartMs,
            postCreditsScenesMs = if (takeScenes) source.postCreditsScenesMs else target.postCreditsScenesMs
        )
        merged.takeIf { it != target }
    }
}

/** Сводка для диалога подтверждения - то же сопоставление, но без единой записи в базу. */
fun buildMergePlan(
    from: List<MediaItemEntity>,
    into: List<MediaItemEntity>,
    progressByItem: Map<String, WatchProgressEntity>,
    favoritesByItem: Map<String, FavoriteEntity>
): SourceMergePlan {
    val pairs = pairItemsByFile(from, into)
    return SourceMergePlan(
        matched = pairs.size,
        unmatched = from.size - pairs.size,
        progressToTransfer = progressTransfers(pairs, progressByItem).size,
        favoritesToTransfer = favoriteTransfers(pairs, favoritesByItem).size
    )
}
