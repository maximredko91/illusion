package com.illusion.app.ui.common

import android.util.LruCache
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Доминирующий цвет постера - им подкрашивается экран описания, чтобы каждый фильм выглядел своим,
 * а не одинаковой карточкой в общей теме.
 *
 * Считается один раз на постер и кладётся в память: постер уже лежит в кеше Coil, так что
 * повторное открытие того же фильма ничего не грузит и не декодирует заново.
 *
 * Возвращает null, пока цвет не посчитан, и для постеров, из которых Palette ничего осмысленного
 * не достала - вызывающий код в этом случае просто рисует обычный фон, без подкраски.
 */
@Composable
fun rememberPosterAccent(model: Any?, imageLoader: ImageLoader? = null): Color? {
    // Выключено пользователем или экономичным профилем - даже не считаем: это чтение и
    // декодирование картинки, пусть и маленькой.
    if (!LocalVisualEffects.current.posterAccent || LocalEconomicalMode.current) return null
    val context = LocalContext.current
    // По умолчанию - тот же загрузчик, которым рисуются постеры, чтобы цвет считался по уже
    // лежащей в кеше картинке, а не по свежескачанной копии.
    val loader = imageLoader ?: coil3.SingletonImageLoader.get(context)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val key = model?.toString()
    var accent by remember(key, isDark) { mutableStateOf(key?.let { cache["$it|$isDark"] }) }

    LaunchedEffect(key, isDark) {
        if (key == null || accent != null) return@LaunchedEffect
        val computed = withContext(Dispatchers.Default) {
            val request = ImageRequest.Builder(context)
                .data(model)
                // Palette читает пиксели на CPU, а аппаратный битмап их не отдаёт.
                .allowHardware(false)
                // Цвет по уменьшенной копии: результат тот же, а декодирование дешевле.
                .size(POSTER_SAMPLE_SIZE)
                .build()
            val image = (loader.execute(request) as? SuccessResult)?.image ?: return@withContext null
            val bitmap = runCatching { image.toBitmap() }.getOrNull() ?: return@withContext null
            val palette = runCatching { Palette.from(bitmap).clearFilters().generate() }.getOrNull()
                ?: return@withContext null
            val swatch = palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.dominantSwatch
                ?: return@withContext null
            Color(swatch.rgb).forTheme(isDark)
        }
        if (computed != null && key != null) {
            cache.put("$key|$isDark", computed)
            accent = computed
        }
        android.util.Log.d("PosterAccent", "accent for $key = $computed")
    }

    return accent
}

/**
 * Цвет постера как есть годится редко: на светлой теме тёмно-синий постер даёт грязное пятно, на
 * тёмной - выцветший бледный. Насыщенность оставляем, светлоту подтягиваем под тему.
 */
private fun Color.forTheme(isDark: Boolean): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(
        android.graphics.Color.argb(
            (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
        ),
        hsl
    )
    hsl[1] = hsl[1].coerceIn(0.35f, 0.85f)
    hsl[2] = if (isDark) hsl[2].coerceIn(0.28f, 0.45f) else hsl[2].coerceIn(0.55f, 0.75f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Постеров в библиотеке тысячи, но одновременно на экране - единицы; больше и не нужно. */
private val cache = LruCache<String, Color>(64)

private const val POSTER_SAMPLE_SIZE = 96
