package com.seance.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.seance.app.domain.model.AccentColor

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
fun SeanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColor: AccentColor = AccentColor.DEFAULT,
    content: @Composable () -> Unit
) {
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
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
