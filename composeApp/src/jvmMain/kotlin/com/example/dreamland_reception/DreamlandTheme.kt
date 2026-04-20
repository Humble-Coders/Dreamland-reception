package com.example.dreamland_reception

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Primary background — deep forest (hero / marketing spec ~`#081C15`). */
internal val DreamlandForest = Color(0xFF081C15)

/** Cards & layered panels (~`#12241E`). */
internal val DreamlandForestSurface = Color(0xFF12241E)

/** Elevated / inset surfaces, subtle lift from [DreamlandForestSurface]. */
internal val DreamlandForestElevated = Color(0xFF162822)

/** Primary accent — muted gold (~`#C5A059`). */
internal val DreamlandGold = Color(0xFFC5A059)

/** Emphasis / hero highlights (~`#D4AF37`). */
internal val DreamlandGoldBright = Color(0xFFD4AF37)

/** Darker gold for gradients, borders, decorative depth. */
internal val DreamlandGoldDeep = Color(0xFF8E6F3C)

/** Primary text on dark (~`#F5F5F5`). */
internal val DreamlandOnDark = Color(0xFFF5F5F5)

/** Secondary / caption text on dark. */
internal val DreamlandMuted = Color(0xFF8FA69E)

/**
 * Italic serif for gold accent lines (hero “Dreamers”-style); use where you need gold + italic.
 */
internal val DreamlandStyleAccentItalicSerif = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Italic,
    fontSize = 22.sp,
    lineHeight = 30.sp,
    letterSpacing = 0.2.sp,
    color = DreamlandGoldBright,
)

private val DreamlandScheme = darkColorScheme(
    primary = DreamlandGold,
    onPrimary = DreamlandForest,
    secondary = DreamlandGoldBright,
    onSecondary = DreamlandForest,
    tertiary = DreamlandGold.copy(alpha = 0.85f),
    background = DreamlandForest,
    onBackground = DreamlandOnDark,
    surface = DreamlandForestSurface,
    onSurface = DreamlandOnDark,
    surfaceVariant = DreamlandForestElevated,
    onSurfaceVariant = DreamlandMuted,
    outline = DreamlandGold.copy(alpha = 0.35f),
    outlineVariant = Color.White.copy(alpha = 0.08f),
)

/**
 * Typography aligned with Dreamland marketing: **serif** for display / section titles,
 * **sans-serif** for UI labels (small caps feel via letter spacing) and **body** with relaxed line height.
 */
private val DreamlandTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 2.sp,
        color = DreamlandGold,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.5.sp,
        color = DreamlandOnDark,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.35.sp,
        color = DreamlandGoldBright,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.25.sp,
        color = DreamlandOnDark,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.2.sp,
        color = DreamlandOnDark,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = DreamlandOnDark,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = DreamlandOnDark,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.4.sp,
        color = DreamlandOnDark,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
        color = DreamlandMuted,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp,
        color = DreamlandOnDark,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
        color = DreamlandOnDark,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        color = DreamlandMuted,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.8.sp,
        color = DreamlandMuted,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp,
        color = DreamlandMuted,
    ),
)

@Composable
fun DreamlandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DreamlandScheme,
        typography = DreamlandTypography,
        content = content,
    )
}
