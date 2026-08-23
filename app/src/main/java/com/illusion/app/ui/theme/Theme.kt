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
import androidx.compose.ui.platform.LocalContext
import com.illusion.app.domain.model.AccentColor
import com.illusion.app.domain.model.ThemeMode

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
                darkColorScheme(primary = accentColor.darkPrimary, secondary = accentColor.darkSecondary, tertiary = accentColor.darkTertiary)
            } else {
                lightColorScheme(primary = accentColor.lightPrimary, secondary = accentColor.lightSecondary, tertiary = accentColor.lightTertiary)
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
