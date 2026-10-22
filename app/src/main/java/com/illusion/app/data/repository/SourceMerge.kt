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
