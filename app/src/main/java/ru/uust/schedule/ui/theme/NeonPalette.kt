package ru.uust.schedule.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ru.uust.schedule.data.prefs.NeonTheme
import kotlin.math.abs

/**
 * Палитра, выведенная из [NeonTheme].
 *
 * Всё считается от одного оттенка, поэтому ползунок перекрашивает интерфейс,
 * не разъезжаясь по контрасту: фон держится тёмным, текст — светлым,
 * меняется только окраска.
 */
@Immutable
data class NeonPalette(
    val accent: Color,
    val accentAlt: Color,
    val accentSoft: Color,
    val background: Color,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val glass: Color,
    val glassStrong: Color,
    val stroke: Color,
    val glowColor: Color,
    val glowAlpha: Float,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val danger: Color,
) {
    companion object {
        fun from(theme: NeonTheme): NeonPalette {
            val h = theme.hue.mod(360f)
            val hAlt = (theme.hue + theme.hueSpread).mod(360f)
            val s = theme.saturation.coerceIn(0f, 1f)
            val v = theme.brightness.coerceIn(0.35f, 1f)

            val accent = hsv(h, s, v)
            val accentAlt = hsv(hAlt, s, v)
            val lift = theme.backgroundLift.coerceIn(0f, 1f)

            // Фон: почти чёрный, чуть подкрашенный акцентом. lift поднимает светлоту,
            // но потолок низкий — иначе неон перестаёт читаться как неон.
            val bgV = 0.04f + lift * 0.10f
            val bgS = (0.55f - lift * 0.25f).coerceIn(0f, 1f)

            return NeonPalette(
                accent = accent,
                accentAlt = accentAlt,
                accentSoft = hsv(h, s * 0.7f, v).copy(alpha = 0.35f),
                background = hsv(h, bgS * 0.6f, bgV),
                backgroundTop = hsv(h, bgS, bgV + 0.05f),
                backgroundBottom = hsv(hAlt, bgS * 0.8f, bgV * 0.6f),
                glass = hsv(h, 0.35f, 0.30f + lift * 0.15f)
                    .copy(alpha = theme.glassOpacity.coerceIn(0.05f, 0.85f)),
                glassStrong = hsv(h, 0.4f, 0.38f + lift * 0.15f)
                    .copy(alpha = (theme.glassOpacity + 0.18f).coerceIn(0.1f, 0.95f)),
                stroke = accent.copy(alpha = 0.28f + theme.glow * 0.25f),
                glowColor = accent,
                glowAlpha = theme.glow.coerceIn(0f, 1f),
                textPrimary = Color(0xFFF2F6FA),
                textSecondary = Color(0xFFB9C3CE),
                textMuted = Color(0xFF7C8794),
                danger = hsv(colorDistance(h, 4f).let { if (it < 40f) 34f else 4f }, 0.85f, 1f),
            )
        }

        /** Стабильный оттенок для предмета: одинаковое имя — всегда один цвет. */
        fun subjectColor(subject: String, palette: NeonPalette, hue: Int = -1): Color {
            if (hue in 0..360) return hsv(hue.toFloat(), 0.8f, 1f)
            val base = (subject.hashCode().mod(360)).toFloat()
            // Держим оттенок предмета рядом с акцентом, чтобы список не пестрил.
            val accentHue = hueOf(palette.accent)
            val offset = ((base / 360f) - 0.5f) * 90f
            return hsv((accentHue + offset).mod(360f), 0.72f, 1f)
        }

        fun hsv(h: Float, s: Float, v: Float): Color =
            Color.hsv(h.mod(360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

        private fun colorDistance(a: Float, b: Float): Float {
            val d = abs(a - b).mod(360f)
            return if (d > 180f) 360f - d else d
        }

        fun hueOf(color: Color): Float {
            val r = color.red; val g = color.green; val b = color.blue
            val max = maxOf(r, g, b); val min = minOf(r, g, b)
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
