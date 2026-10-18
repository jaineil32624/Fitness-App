package com.repwise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repwise.domain.AccentTheme
import com.repwise.domain.ThemeMode

/** Brand colors that Material's scheme doesn't model: gradients and celebration accents. */
@Immutable
data class RepwiseColors(
    val heroStart: Color,
    val heroEnd: Color,
    val glow: Color,
    val success: Color,
    val warning: Color,
    val isDark: Boolean,
) {
    val heroBrush: Brush get() = Brush.linearGradient(listOf(heroStart, heroEnd))
}

val LocalRepwiseColors = staticCompositionLocalOf {
    RepwiseColors(Color(0xFF1C2822), Color(0xFF327A5B), Color(0xFF8EE0A9), Color(0xFF3FA36B), Color(0xFFE0A030), false)
}

private data class Palette(val primary: Color, val primaryDark: Color, val secondary: Color, val tertiary: Color, val heroStart: Color, val heroEnd: Color, val glow: Color)

private fun AccentTheme.palette(): Palette = when (this) {
    AccentTheme.FOREST -> Palette(Color(0xFF2E7656), Color(0xFF7FD3A5), Color(0xFF52635A), Color(0xFF3B6470), Color(0xFF14261D), Color(0xFF2E7656), Color(0xFF8EE0A9))
    AccentTheme.OCEAN -> Palette(Color(0xFF1F6A9A), Color(0xFF93CCFF), Color(0xFF4F606E), Color(0xFF2E7C7A), Color(0xFF0D2234), Color(0xFF1F6A9A), Color(0xFF7FD0F5))
    AccentTheme.SUNSET -> Palette(Color(0xFFB2462B), Color(0xFFFFB59F), Color(0xFF77574E), Color(0xFF8A5A00), Color(0xFF3A1410), Color(0xFFD0603A), Color(0xFFFFC07A))
    AccentTheme.GRAPE -> Palette(Color(0xFF6E4BB0), Color(0xFFD2BBFF), Color(0xFF635B70), Color(0xFF9A4374), Color(0xFF1F1236), Color(0xFF6E4BB0), Color(0xFFE2A6FF))
    AccentTheme.GOLD -> Palette(Color(0xFF8A6100), Color(0xFFF5BF48), Color(0xFF6B5D3F), Color(0xFF4A6547), Color(0xFF231A05), Color(0xFF9C7000), Color(0xFFFFD66B))
}

private fun lightScheme(p: Palette): ColorScheme = lightColorScheme(
    primary = p.primary,
    onPrimary = Color.White,
    primaryContainer = p.primary.copy(alpha = 0.14f).compositeOver(Color(0xFFFAFBF8)),
    onPrimaryContainer = p.heroStart,
    secondary = p.secondary,
    secondaryContainer = p.secondary.copy(alpha = 0.12f).compositeOver(Color(0xFFFAFBF8)),
    onSecondaryContainer = Color(0xFF1C2822),
    tertiary = p.tertiary,
    background = Color(0xFFF5F6F2),
    onBackground = Color(0xFF1A1F1C),
    surface = Color(0xFFFAFBF8),
    onSurface = Color(0xFF1A1F1C),
    surfaceVariant = Color(0xFFE4E8E2),
    onSurfaceVariant = Color(0xFF5E6762),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2EE),
    surfaceContainerHigh = Color(0xFFEAEDE8),
    surfaceContainerHighest = Color(0xFFE4E8E2),
    outline = Color(0xFF8A938D),
    outlineVariant = Color(0xFFDDE2DC),
)

private fun darkScheme(p: Palette): ColorScheme = darkColorScheme(
    primary = p.primaryDark,
    onPrimary = p.heroStart,
    primaryContainer = p.primary.copy(alpha = 0.35f).compositeOver(Color(0xFF151A17)),
    onPrimaryContainer = p.primaryDark,
    secondary = p.secondary.copy(alpha = 0.6f).compositeOver(Color(0xFFD0D8D2)),
    secondaryContainer = Color(0xFF2A322D),
    onSecondaryContainer = Color(0xFFDCE5DE),
    tertiary = p.glow,
    background = Color(0xFF0E1210),
    onBackground = Color(0xFFE2E6E2),
    surface = Color(0xFF131815),
    onSurface = Color(0xFFE2E6E2),
    surfaceVariant = Color(0xFF2A302C),
    onSurfaceVariant = Color(0xFFA9B3AC),
    surfaceContainerLowest = Color(0xFF0B0E0C),
    surfaceContainerLow = Color(0xFF181D1A),
    surfaceContainer = Color(0xFF1C221F),
    surfaceContainerHigh = Color(0xFF232A26),
    surfaceContainerHighest = Color(0xFF2B332E),
    outline = Color(0xFF79837C),
    outlineVariant = Color(0xFF353D38),
)

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(red * a + background.red * (1 - a), green * a + background.green * (1 - a), blue * a + background.blue * (1 - a), 1f)
}

private val RepwiseTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    )
}

/** Small all-caps label used above stats and section titles. */
val Typography.overline: TextStyle get() = labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.2.sp)

private val RepwiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun RepwiseTheme(mode: ThemeMode, accent: AccentTheme, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = accent.palette()
    val colors = RepwiseColors(
        heroStart = palette.heroStart,
        heroEnd = palette.heroEnd,
        glow = palette.glow,
        success = if (dark) Color(0xFF7FD3A5) else Color(0xFF2E8A57),
        warning = if (dark) Color(0xFFF5C26B) else Color(0xFFB5761A),
        isDark = dark,
    )
    CompositionLocalProvider(LocalRepwiseColors provides colors) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(palette) else lightScheme(palette),
            typography = RepwiseTypography,
            shapes = RepwiseShapes,
            content = content,
        )
    }
}

/** Preview swatch for the theme picker. */
fun AccentTheme.swatch(): Brush = palette().let { Brush.linearGradient(listOf(it.heroStart, it.heroEnd, it.glow)) }
