package ru.uust.schedule.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ru.uust.schedule.data.prefs.AppTheme

/**
 * Палитра приложения.
 *
 * Фон и карточки почти нейтральные — в них лишь след оттенка акцента, чтобы
 * интерфейс не выглядел собранным из разных наборов. Цветом работает
 * содержимое: метки предметов, активные элементы, подписи.
 *
 * Насыщенность акцента ограничена сверху: даже на максимуме [AppTheme.intensity]
 * цвет остаётся приглушённым, потому что неон на больших поверхностях утомляет.
 */
@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val accentMuted: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val danger: Color,
    val isDark: Boolean,
) {
    /** Цвет акцента для подложек: тот же тон, но едва заметный. */
    fun tint(alpha: Float = 0.12f): Color = accent.copy(alpha = alpha)

    companion object {

        fun from(theme: AppTheme): Palette {
            val h = theme.hue.mod(360f)
            val intensity = theme.intensity.coerceIn(0f, 1f)
            val contrast = theme.contrast.coerceIn(0f, 1f)

            // Верхняя граница насыщенности — 0.72: выше начинается неон.
            val accentSat = 0.34f + intensity * 0.38f
            val accent = if (theme.dark) {
                Color.hsv(h, accentSat, 0.72f + intensity * 0.16f)
            } else {
                Color.hsv(h, accentSat + 0.08f, 0.58f + intensity * 0.12f)
            }

            return if (theme.dark) darkPalette(h, accent, contrast) else lightPalette(h, accent, contrast)
        }

        private fun darkPalette(hue: Float, accent: Color, contrast: Float) = Palette(
            background = Color.hsv(hue, 0.07f, 0.042f),
            surface = Color.hsv(hue, 0.055f, 0.095f + contrast * 0.035f),
            surfaceHigh = Color.hsv(hue, 0.05f, 0.145f + contrast * 0.04f),
            accent = accent,
            accentMuted = accent.copy(alpha = 0.16f),
            onAccent = Color(0xFF0A0A0B),
            textPrimary = Color(0xFFF7F7F8),
            textSecondary = Color(0xFF9E9EA6),
            textMuted = Color(0xFF6B6B73),
            divider = Color(0xFF2A2A2E),
            danger = Color(0xFFE5796F),
            isDark = true,
        )

        private fun lightPalette(hue: Float, accent: Color, contrast: Float) = Palette(
            background = Color.hsv(hue, 0.03f, 0.97f),
            surface = Color.hsv(hue, 0.012f, 1f - contrast * 0.012f),
            surfaceHigh = Color.hsv(hue, 0.045f, 0.935f - contrast * 0.02f),
            accent = accent,
            accentMuted = accent.copy(alpha = 0.14f),
            onAccent = Color.White,
            textPrimary = Color(0xFF14141A),
            textSecondary = Color(0xFF61616B),
            textMuted = Color(0xFF8E8E98),
            divider = Color(0xFFE2E2E8),
            danger = Color(0xFFC8453A),
            isDark = false,
        )

        /**
         * Цвет метки предмета. Одно и то же название всегда даёт один цвет.
         *
         * Оттенки держатся в пределах ±70° от акцента: так список выглядит
         * одним набором, а не радугой.
         */
        fun subjectColor(subject: String, palette: Palette, hue: Int = -1): Color {
            val accentHue = hueOf(palette.accent)
            val h = if (hue in 0..360) {
                hue.toFloat()
            } else {
                val spread = (subject.hashCode().mod(1000) / 1000f - 0.5f) * 140f
                (accentHue + spread).mod(360f)
            }
            return if (palette.isDark) Color.hsv(h, 0.46f, 0.82f)
            else Color.hsv(h, 0.55f, 0.66f)
        }

        fun hueOf(color: Color): Float {
            val r = color.red
            val g = color.green
            val b = color.blue
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val d = max - min
            if (d == 0f) return 0f
            val h = when (max) {
                r -> 60f * (((g - b) / d).mod(6f))
                g -> 60f * (((b - r) / d) + 2f)
                else -> 60f * (((r - g) / d) + 4f)
            }
            return h.mod(360f)
        }
    }
}
