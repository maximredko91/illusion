package com.illusion.app.domain.model

import com.illusion.app.data.local.entity.MediaItemEntity

/**
 * Kodi/tinyMediaManager's <edition> is a fixed set of English codes, not freeform text (the
 * handful below are the ones their own scrapers actually write - see Kodi's own wiki on the
 * <edition> tag) - translated here rather than shown as-is, since the .nfo's value is never
 * meant to be user-facing on its own. An edition this app doesn't recognize (a newer code, or a
 * typo in the source .nfo) falls back to the raw value rather than hiding it - still better than
 * silently dropping information the file actually carries.
 */
val MediaItemEntity.editionLabel: String?
    get() = edition?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        when (raw.uppercase().replace(' ', '_')) {
            // "NONE" is Kodi/tinyMediaManager's own explicit "no particular edition" value, not
            // an unrecognized code - confirmed on-device it shows up in real .nfo files (a
            // scraper writes it even when there's genuinely nothing to say here). Showing the
            // literal word "NONE" on a movie card read as a real (blank) edition, not "there
            // isn't one" - null here means the whole meta line just omits it, same as any other
            // absent field.
            "NONE" -> return@let null
            "DIRECTORS_CUT", "DIRECTOR'S_CUT" -> "Режиссёрская версия"
            "EXTENDED_EDITION", "EXTENDED_CUT", "EXTENDED" -> "Расширенная версия"
            "THEATRICAL_CUT", "THEATRICAL_EDITION", "THEATRICAL" -> "Театральная версия"
            "UNRATED", "UNRATED_CUT", "UNRATED_EDITION" -> "Без цензуры"
            "ULTIMATE_EDITION", "ULTIMATE_CUT" -> "Полная версия"
            "SPECIAL_EDITION" -> "Специальное издание"
            "REMASTERED", "REMASTERED_EDITION" -> "Ремастеринг"
            "FAN_EDIT", "FAN_CUT" -> "Фанатская версия"
            "IMAX", "IMAX_EDITION" -> "IMAX-версия"
            else -> raw
        }
    }
