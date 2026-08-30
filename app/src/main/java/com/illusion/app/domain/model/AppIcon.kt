package com.illusion.app.domain.model

/**
 * Alternate launcher icon variants (Настройки > Значок приложения), one per [AccentColor] entry -
 * each swaps the mark's stroke color for that accent's own lightPrimary (same relationship
 * ILLUSION's crimson already has to AccentColor.ILLUSION.lightPrimary). Switched at runtime via
 * [com.illusion.app.data.appicon.IconVariantManager], which enables/disables the matching
 * `<activity-alias>` in AndroidManifest.xml - [aliasName] is that alias's manifest `android:name`
 * (relative, as declared there). ILLUSION is what ships by default (matches the existing
 * @mipmap/ic_launcher unchanged) and is the only alias enabled out of the box.
 */
enum class AppIcon(val aliasName: String) {
    ILLUSION(".IconIllusion"),
    DEFAULT(".IconDefault"),
    BLUE(".IconBlue"),
    GREEN(".IconGreen"),
    ORANGE(".IconOrange"),
    YELLOW(".IconYellow"),
    RED(".IconRed"),
    TEAL(".IconTeal"),
    PINK(".IconPink")
}
