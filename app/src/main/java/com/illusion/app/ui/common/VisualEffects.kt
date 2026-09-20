package com.illusion.app.ui.common

import androidx.compose.runtime.compositionLocalOf

/**
 * Какие украшения интерфейса включены. Каждое стоит либо кадров, либо памяти, поэтому они
 * переключаются по отдельности (Настройки → Графика), а не прячутся за общим «красиво/быстро».
 *
 * Профиль производительности («Экономичный») остаётся поверх всего: там эти эффекты выключены
 * независимо от галочек - см. [LocalEconomicalMode] и то, как его проверяет каждый из них.
 */
data class VisualEffects(
    /** Экран описания подкрашивается цветом постера. */
    val posterAccent: Boolean = true,
    /** Фон уезжает медленнее содержимого при прокрутке. */
    val parallax: Boolean = true
)

val LocalVisualEffects = compositionLocalOf { VisualEffects() }
