package io.github.thedayapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.thedayapp.data.PaletteStyle

private val MidnightLight = lightColorScheme(
    primary = Color(0xFF294A72),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4F7),
    onPrimaryContainer = Color(0xFF102C4A),
    secondary = Color(0xFF596579),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7A5B83),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E4EB),
    onSurfaceVariant = Color(0xFF444A54),
    outline = Color(0xFF747A84),
)

private val MidnightDark = darkColorScheme(
    primary = Color(0xFFA9C8F4),
    onPrimary = Color(0xFF0F3458),
    primaryContainer = Color(0xFF264D76),
    onPrimaryContainer = Color(0xFFD6E4F7),
    secondary = Color(0xFFC1CADB),
    onSecondary = Color(0xFF2B3546),
    tertiary = Color(0xFFE1BDE8),
    onTertiary = Color(0xFF412744),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE2E2E8),
    surface = Color(0xFF171A1F),
    onSurface = Color(0xFFE2E2E8),
    surfaceVariant = Color(0xFF444A54),
    onSurfaceVariant = Color(0xFFC4C7CF),
    outline = Color(0xFF8E939D),
)

private val CinnabarLight = lightColorScheme(
    primary = Color(0xFF8D2F2B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF3A0807),
    secondary = Color(0xFF775653),
    onSecondary = Color.White,
    tertiary = Color(0xFF705C2E),
    onTertiary = Color.White,
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF241918),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF241918),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    outline = Color(0xFF857370),
)

private val CinnabarDark = darkColorScheme(
    primary = Color(0xFFFFB4AA),
    onPrimary = Color(0xFF561E1B),
    primaryContainer = Color(0xFF722722),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB8),
    onSecondary = Color(0xFF442927),
    tertiary = Color(0xFFDFC38C),
    onTertiary = Color(0xFF3E2E05),
    background = Color(0xFF1B1110),
    onBackground = Color(0xFFF1DEDB),
    surface = Color(0xFF211413),
    onSurface = Color(0xFFF1DEDB),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BE),
    outline = Color(0xFFA08C89),
)

private val PineLight = lightColorScheme(
    primary = Color(0xFF365E52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9ECDB),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4D635B),
    onSecondary = Color.White,
    tertiary = Color(0xFF456277),
    onTertiary = Color.White,
    background = Color(0xFFF5FAF7),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF8FBF9),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975),
)

private val PineDark = darkColorScheme(
    primary = Color(0xFF9ED0BF),
    onPrimary = Color(0xFF04372D),
    primaryContainer = Color(0xFF1D4E42),
    onPrimaryContainer = Color(0xFFB9ECDB),
    secondary = Color(0xFFB5CCC2),
    onSecondary = Color(0xFF20352E),
    tertiary = Color(0xFFACCBE5),
    onTertiary = Color(0xFF153448),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF151B18),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF89938F),
)

private val AntiqueGoldLight = lightColorScheme(
    primary = Color(0xFF6B5723),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4DF9D),
    onPrimaryContainer = Color(0xFF231B00),
    secondary = Color(0xFF655F4A),
    onSecondary = Color.White,
    tertiary = Color(0xFF426651),
    onTertiary = Color.White,
    background = Color(0xFFFFF9ED),
    onBackground = Color(0xFF1E1B13),
    surface = Color(0xFFFFF9ED),
    onSurface = Color(0xFF1E1B13),
    surfaceVariant = Color(0xFFE9E2D0),
    onSurfaceVariant = Color(0xFF4B4739),
    outline = Color(0xFF7C7767),
)

private val AntiqueGoldDark = darkColorScheme(
    primary = Color(0xFFD7C379),
    onPrimary = Color(0xFF3A3000),
    primaryContainer = Color(0xFF514617),
    onPrimaryContainer = Color(0xFFF4DF9D),
    secondary = Color(0xFFCEC6AE),
    onSecondary = Color(0xFF363123),
    tertiary = Color(0xFFA8D0B5),
    onTertiary = Color(0xFF123723),
    background = Color(0xFF15130D),
    onBackground = Color(0xFFE8E2D6),
    surface = Color(0xFF1B1912),
    onSurface = Color(0xFFE8E2D6),
    surfaceVariant = Color(0xFF4B4739),
    onSurfaceVariant = Color(0xFFCDC6B4),
    outline = Color(0xFF969080),
)

fun colorSchemeFor(style: PaletteStyle, dark: Boolean): ColorScheme = when (style) {
    PaletteStyle.MIDNIGHT -> if (dark) MidnightDark else MidnightLight
    PaletteStyle.CINNABAR -> if (dark) CinnabarDark else CinnabarLight
    PaletteStyle.PINE -> if (dark) PineDark else PineLight
    PaletteStyle.ANTIQUE_GOLD -> if (dark) AntiqueGoldDark else AntiqueGoldLight
}

fun palettePreviewColor(style: PaletteStyle): Color = when (style) {
    PaletteStyle.MIDNIGHT -> Color(0xFF294A72)
    PaletteStyle.CINNABAR -> Color(0xFF8D2F2B)
    PaletteStyle.PINE -> Color(0xFF365E52)
    PaletteStyle.ANTIQUE_GOLD -> Color(0xFF8A7130)
}
