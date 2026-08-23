package com.illusion.app.domain.model

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
    // Hue nudged from the old value's red-orange (~21°) to a true orange (~30-35°) - the old
    // swatch read as brick-red/brown to the eye even though it was technically "orange" by hue
    // math, since M3's light-theme contrast requirement (tone ~40, dark enough for white text)
    // muddies every warm hue toward brown at that lightness - this is the most vivid true-orange
    // achievable at that same tone, not a full fix (that ceiling applies to YELLOW below too).
    ORANGE(
        lightPrimary = Color(0xFFA85400), lightSecondary = Color(0xFF7C5732), lightTertiary = Color(0xFF5C6B2C),
        darkPrimary = Color(0xFFFFB74D), darkSecondary = Color(0xFFE8C0A0), darkTertiary = Color(0xFFC3D19A)
    ),
    // True yellow can't stay legible as "yellow" once darkened enough for readable text on a
    // light background (same tone-40 contrast ceiling noted on ORANGE above) - it reads as a warm
    // gold/mustard in light theme, which is the closest a light-theme yellow can get without
    // failing contrast. Dark theme's swatch (used as light text/icons on a dark background, no
    // such constraint) is a proper bright gold.
    YELLOW(
        lightPrimary = Color(0xFF8C6D00), lightSecondary = Color(0xFF6F5F3E), lightTertiary = Color(0xFF3F6C4A),
        darkPrimary = Color(0xFFFFD54F), darkSecondary = Color(0xFFD6C6A0), darkTertiary = Color(0xFFA6D3AE)
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
