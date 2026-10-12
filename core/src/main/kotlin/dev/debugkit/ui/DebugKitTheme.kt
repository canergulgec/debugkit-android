package dev.debugkit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

/**
 * DebugKit's own dark theme, the same whatever the host app or the system setting, so the tool
 * always looks like itself and never like part of the app under test.
 */
@Composable
internal fun DebugKitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}

private val Colors = darkColorScheme(
    primary = DebugKitColors.Accent,
    onPrimary = DebugKitColors.OnAccent,
    primaryContainer = DebugKitColors.Selected,
    onPrimaryContainer = DebugKitColors.Text,
    secondary = DebugKitColors.Accent,
    onSecondary = DebugKitColors.OnAccent,
    secondaryContainer = DebugKitColors.Selected,
    onSecondaryContainer = DebugKitColors.Text,
    tertiary = DebugKitColors.Override,
    onTertiary = DebugKitColors.OnAccent,
    tertiaryContainer = DebugKitColors.OverrideContainer,
    onTertiaryContainer = DebugKitColors.Override,
    background = DebugKitColors.Background,
    onBackground = DebugKitColors.Text,
    surface = DebugKitColors.Background,
    onSurface = DebugKitColors.Text,
    surfaceVariant = DebugKitColors.Card,
    onSurfaceVariant = DebugKitColors.TextSecondary,
    surfaceContainerLowest = DebugKitColors.Background,
    surfaceContainerLow = DebugKitColors.Card,
    surfaceContainer = DebugKitColors.Card,
    surfaceContainerHigh = DebugKitColors.CardRaised,
    surfaceContainerHighest = DebugKitColors.Selected,
    // Unchecked switch thumbs and text field borders: brighter than card borders so they read.
    outline = DebugKitColors.TextFaint,
    outlineVariant = DebugKitColors.Divider,
    error = DebugKitColors.Error,
    onError = DebugKitColors.OnAccent,
    errorContainer = DebugKitColors.ErrorContainer,
    onErrorContainer = DebugKitColors.Error,
)

private val Type = Typography().run {
    fun TextStyle.sans() = copy(fontFamily = DebugKitFonts.Sans)
    Typography(
        displayLarge = displayLarge.sans(),
        displayMedium = displayMedium.sans(),
        displaySmall = displaySmall.sans(),
        headlineLarge = headlineLarge.sans(),
        headlineMedium = headlineMedium.sans(),
        headlineSmall = headlineSmall.sans(),
        titleLarge = titleLarge.sans(),
        titleMedium = titleMedium.sans(),
        titleSmall = titleSmall.sans(),
        bodyLarge = bodyLarge.sans(),
        bodyMedium = bodyMedium.sans(),
        bodySmall = bodySmall.sans(),
        labelLarge = labelLarge.sans(),
        labelMedium = labelMedium.sans(),
        labelSmall = labelSmall.sans(),
    )
}
