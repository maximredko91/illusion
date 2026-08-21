package com.seance.app.domain.model

import androidx.compose.ui.graphics.Color

/**
 * User-selectable accent color (Settings), overriding the theme's primary/secondary/tertiary
 * colors. [DEFAULT] keeps the app's existing behavior (Material You wallpaper-based dynamic
 * color on API 31+, falling back to the original hardcoded purple scheme below that) - every
 * other entry replaces both with a fixed scheme built from these colors, same lightweight
 * "override primary/secondary/tertiary, leave the rest at Material3's baseline defaults"
 * approach the original hardcoded purple scheme already used (see ui/theme/Theme.kt).
 */
enum class AccentColor(
    val lightPrimary: Color,
    val lightSecondary: Color,
    val lightTertiary: Color,
    val darkPrimary: Color,
    val darkSecondary: Color,
    val darkTertiary: Color
) {
    DEFAULT(
        lightPrimary = Color(0xFF6650a4), lightSecondary = Color(0xFF625b71), lightTertiary = Color(0xFF7D5260),
        darkPrimary = Color(0xFFD0BCFF), darkSecondary = Color(0xFFCCC2DC), darkTertiary = Color(0xFFEFB8C8)
    ),
    BLUE(
        lightPrimary = Color(0xFF3F5CA9), lightSecondary = Color(0xFF585F71), lightTertiary = Color(0xFF6B5778),
        darkPrimary = Color(0xFFB6C4EB), darkSecondary = Color(0xFFC0C6DC), darkTertiary = Color(0xFFD8BFE3)
    ),
    GREEN(
        lightPrimary = Color(0xFF3D6B3E), lightSecondary = Color(0xFF52634F), lightTertiary = Color(0xFF39656A),
        darkPrimary = Color(0xFFA1D4A0), darkSecondary = Color(0xFFB9C9B4), darkTertiary = Color(0xFFA0CFD4)
    ),
    ORANGE(
        lightPrimary = Color(0xFF9C4515), lightSecondary = Color(0xFF77574B), lightTertiary = Color(0xFF67602C),
        darkPrimary = Color(0xFFFFB68C), darkSecondary = Color(0xFFE7BDAE), darkTertiary = Color(0xFFD1C88E)
    ),
    RED(
        lightPrimary = Color(0xFFA13F3F), lightSecondary = Color(0xFF775651), lightTertiary = Color(0xFF6D5C2E),
        darkPrimary = Color(0xFFFFB3AE), darkSecondary = Color(0xFFE7BDB7), darkTertiary = Color(0xFFDAC38F)
    ),
    TEAL(
        lightPrimary = Color(0xFF1C6C68), lightSecondary = Color(0xFF4A6360), lightTertiary = Color(0xFF4B607C),
        darkPrimary = Color(0xFF83D5CE), darkSecondary = Color(0xFFB1CCC8), darkTertiary = Color(0xFFB4C8E8)
    ),
    PINK(
        lightPrimary = Color(0xFFA13D77), lightSecondary = Color(0xFF74576A), lightTertiary = Color(0xFF7C5635),
        darkPrimary = Color(0xFFFFACDA), darkSecondary = Color(0xFFE3BAD5), darkTertiary = Color(0xFFEEBE93)
    )
}
