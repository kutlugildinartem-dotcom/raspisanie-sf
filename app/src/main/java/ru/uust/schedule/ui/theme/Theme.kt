package ru.uust.schedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.uust.schedule.data.prefs.NeonTheme

val LocalNeon = staticCompositionLocalOf { NeonPalette.from(NeonTheme.Default) }

@Composable
fun UustTheme(theme: NeonTheme, content: @Composable () -> Unit) {
    val palette = NeonPalette.from(theme)
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.background,
        secondary = palette.accentAlt,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.background,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.glass,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.stroke,
        error = palette.danger,
    )
    CompositionLocalProvider(LocalNeon provides palette) {
        MaterialTheme(colorScheme = scheme, typography = NeonTypography, content = content)
    }
}

/** Интерфейс плотный: важны время и название, остальное — второй план. */
private val NeonTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.6.sp,
    ),
)

@Composable
internal fun keepDark(): Boolean = isSystemInDarkTheme() || true
