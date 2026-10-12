package dev.debugkit.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.debugkit.R

/**
 * DebugKit's palette: a dark ground, one accent and a status color per tone. Public so feature
 * modules draw with the same colors as core; host apps have no reason to use it.
 */
public object DebugKitColors {
    public val Background: Color = Color(0xFF0B0E13)
    public val Card: Color = Color(0xFF131821)
    public val CardRaised: Color = Color(0xFF1A2030)
    public val Selected: Color = Color(0xFF232B3B)
    public val Border: Color = Color(0xFF242C3B)
    public val Divider: Color = Color(0xFF1C2330)
    public val NavBar: Color = Color(0xFF0E1218)
    public val Code: Color = Color(0xFF0F131A)

    public val Text: Color = Color(0xFFE7EBF2)
    public val TextSecondary: Color = Color(0xFF8B95A5)
    public val TextFaint: Color = Color(0xFF5C6676)

    public val Accent: Color = Color(0xFF8C9BFF)
    public val OnAccent: Color = Color(0xFF0B0E13)

    public val Good: Color = Color(0xFF4FE09A)
    public val GoodContainer: Color = Color(0xFF173326)
    public val Warning: Color = Color(0xFFF5B14C)
    public val WarningContainer: Color = Color(0xFF3A2A12)
    public val Error: Color = Color(0xFFFF8A8A)
    public val ErrorContainer: Color = Color(0xFF3A1A1C)
    public val Info: Color = Color(0xFF6CC0FF)
    public val InfoContainer: Color = Color(0xFF152536)
    /** Marks anything produced by a mock rule or an override rather than the real app. */
    public val Override: Color = Color(0xFFC8B6FF)
    public val OverrideContainer: Color = Color(0xFF2B2440)
}

/** IBM Plex Sans for the interface, JetBrains Mono for URLs, JSON, logs and other code. */
public object DebugKitFonts {
    public val Sans: FontFamily = FontFamily(
        Font(R.font.debugkit_sans_regular, FontWeight.Normal),
        Font(R.font.debugkit_sans_medium, FontWeight.Medium),
        Font(R.font.debugkit_sans_semibold, FontWeight.SemiBold),
        Font(R.font.debugkit_sans_bold, FontWeight.Bold),
    )
    public val Mono: FontFamily = FontFamily(
        Font(R.font.debugkit_mono_regular, FontWeight.Normal),
        Font(R.font.debugkit_mono_medium, FontWeight.Medium),
        // No bold cut is bundled; bold monospace text uses the medium cut.
        Font(R.font.debugkit_mono_medium, FontWeight.SemiBold),
    )
}

/** How a value should read at a glance: fine, worth a look, or broken. */
public enum class DebugTone { Neutral, Good, Warning, Error, Info, Override }

/** Foreground and container colors for a [DebugTone]. */
public fun DebugTone.color(): Color = when (this) {
    DebugTone.Neutral -> DebugKitColors.TextSecondary
    DebugTone.Good -> DebugKitColors.Good
    DebugTone.Warning -> DebugKitColors.Warning
    DebugTone.Error -> DebugKitColors.Error
    DebugTone.Info -> DebugKitColors.Info
    DebugTone.Override -> DebugKitColors.Override
}

public fun DebugTone.container(): Color = when (this) {
    DebugTone.Neutral -> DebugKitColors.CardRaised
    DebugTone.Good -> DebugKitColors.GoodContainer
    DebugTone.Warning -> DebugKitColors.WarningContainer
    DebugTone.Error -> DebugKitColors.ErrorContainer
    DebugTone.Info -> DebugKitColors.InfoContainer
    DebugTone.Override -> DebugKitColors.OverrideContainer
}
