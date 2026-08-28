package com.illusion.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.illusion.app.domain.model.AccentColor
import com.illusion.app.domain.model.ThemeMode

/**
 * `darkColorScheme(primary = X, ...)` only overrides the three roles actually passed in - every
 * other role (onPrimary, primaryContainer, ...) silently stays at Material3's baked-in default
 * purple-ish tone regardless of the chosen accent. That was invisible while this app only ever
 * had one hardcoded scheme, but once accent color became user-choosable it meant anything reading
 * `onPrimary` - most visibly a `Switch`'s checked thumb (Material3's own SwitchTokens map
 * `checkedThumbColor` straight to `ColorSchemeKeyTokens.OnPrimary`, confirmed by reading the real
 * Switch.kt/SwitchTokens.kt sources) - never actually changed color no matter which accent was
 * picked, only the track around it (`primary`) did.
 *
 * A first pass here just picked pure black-or-white by contrast (luminance() > 0.5f), which
 * technically fixed legibility but not the actual complaint: every one of this app's *dark*-theme
 * accent primaries is a light M3 tone-80-ish pastel (by Material's own dark-theme convention, for
 * contrast against a dark background), so every single one has luminance > 0.5 and the computed
 * on-color collapsed back to plain black for all nine accents - visually indistinguishable from
 * the original bug. What's actually needed is a same-hue variant at a contrasting *lightness*, not
 * a hue-blind black/white snap - so the thumb still visibly shifts color with the chosen accent
 * while staying legible against its own track. HSL (not raw RGB math) is what makes "same hue,
 * different lightness" a one-line change - androidx.core's ColorUtils wraps the same platform HSL
 * conversion `android.graphics.Color` itself uses, no extra dependency needed.
 */
private fun onColorFor(background: Color): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(background.toArgb(), hsl)
    val backgroundIsLight = hsl[2] > 0.5f
    // Slightly past the opposite extreme (0.12/0.92, not 0/1) so the result reads as "a deep/pale
    // shade of this hue" rather than snapping all the way to true black/white regardless of hue -
    // pure black or white would wash out the saturation entirely on some hues.
    hsl[2] = if (backgroundIsLight) 0.12f else 0.92f
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun IllusionTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accentColor: AccentColor = AccentColor.ILLUSION,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val colorScheme = when {
        // A user-picked accent overrides Material You/the default purple scheme outright - picking
        // one from Settings is a deliberate override, so it should always win rather than only
        // applying below API 31 or being silently ignored while dynamic color is active.
        accentColor != AccentColor.DEFAULT -> {
            if (darkTheme) {
                darkColorScheme(
                    primary = accentColor.darkPrimary,
                    onPrimary = onColorFor(accentColor.darkPrimary),
                    secondary = accentColor.darkSecondary,
                    onSecondary = onColorFor(accentColor.darkSecondary),
                    tertiary = accentColor.darkTertiary,
                    onTertiary = onColorFor(accentColor.darkTertiary)
                )
            } else {
                lightColorScheme(
                    primary = accentColor.lightPrimary,
                    onPrimary = onColorFor(accentColor.lightPrimary),
                    secondary = accentColor.lightSecondary,
                    onSecondary = onColorFor(accentColor.lightSecondary),
                    tertiary = accentColor.lightTertiary,
                    onTertiary = onColorFor(accentColor.lightTertiary)
                )
            }
        }

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.let {
        // True AMOLED black rather than Material's usual dark-grey surfaces - only overrides the
        // background/surface tones, everything else (accent, dynamic color, container colors
        // derived from them) stays as already computed above.
        if (themeMode == ThemeMode.BLACK) {
            it.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0A0A0A),
                surfaceContainer = Color(0xFF0F0F0F),
                surfaceContainerHigh = Color(0xFF141414),
                surfaceContainerHighest = Color(0xFF1A1A1A)
            )
        } else {
            it
        }
    }

    // tv-material's own MaterialTheme/ColorScheme is a SEPARATE CompositionLocal from Material3's
    // - androidx.tv.material3.Card/Surface/etc. (used by TV-mode composables, e.g. PosterCard) read
    // colors from IT, not from the androidx.compose.material3.MaterialTheme above. Without this,
    // every TV component would render with tv-material's own hardcoded default palette instead of
    // the user's chosen accent color/theme. Always provided (not gated on UiMode) since it's inert
    // for phone-mode composables, which never read it - simpler than threading UiMode through here
    // just to skip an otherwise-harmless CompositionLocal provide.
    val tvColorScheme = if (darkTheme) {
        androidx.tv.material3.darkColorScheme(
            primary = colorScheme.primary,
            onPrimary = colorScheme.onPrimary,
            secondary = colorScheme.secondary,
            onSecondary = colorScheme.onSecondary,
            tertiary = colorScheme.tertiary,
            onTertiary = colorScheme.onTertiary,
            background = colorScheme.background,
            onBackground = colorScheme.onBackground,
            surface = colorScheme.surface,
            onSurface = colorScheme.onSurface
        )
    } else {
        androidx.tv.material3.lightColorScheme(
            primary = colorScheme.primary,
            onPrimary = colorScheme.onPrimary,
            secondary = colorScheme.secondary,
            onSecondary = colorScheme.onSecondary,
            tertiary = colorScheme.tertiary,
            onTertiary = colorScheme.onTertiary,
            background = colorScheme.background,
            onBackground = colorScheme.onBackground,
            surface = colorScheme.surface,
            onSurface = colorScheme.onSurface
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            androidx.tv.material3.MaterialTheme(
                colorScheme = tvColorScheme,
                content = content
            )
        }
    )
}
