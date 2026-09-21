package ru.uust.schedule.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import ru.uust.schedule.data.prefs.NeonTheme
import ru.uust.schedule.ui.theme.NeonPalette

/**
 * Фон виджета рисуется в Bitmap, а не задаётся цветом или XML-фигурой.
 *
 * Причина: нужны одновременно скруглённые углы, градиент, свечение и произвольная
 * прозрачность на API 26+. GlanceModifier.cornerRadius требует API 31, а shape-drawable
 * не даёт градиента — так виджет выглядел бы иначе, чем приложение.
 */
object WidgetBackground {

    /** Выше этого разрешения RemoteViews начинает упираться в лимит памяти на bitmap. */
    private const val MAX_DIMEN = 720

    private val cache = LruCache<String, Bitmap>(8)

    fun render(
        widthPx: Int,
        heightPx: Int,
        theme: NeonTheme,
        cornerRadiusPx: Float,
    ): Bitmap {
        val scale = minOf(
            1f,
            MAX_DIMEN.toFloat() / maxOf(widthPx, heightPx).coerceAtLeast(1),
        )
        val w = (widthPx * scale).toInt().coerceAtLeast(8)
        val h = (heightPx * scale).toInt().coerceAtLeast(8)
        val r = cornerRadiusPx * scale

        val key = "$w:$h:${theme.hashCode()}:${r.toInt()}"
        cache[key]?.takeIf { !it.isRecycled }?.let { return it }

        val palette = NeonPalette.from(theme)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(1f, 1f, w - 1f, h - 1f)

        // Стеклянная заливка: вертикальный градиент от более плотного верха к прозрачному низу.
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                palette.glassStrong.toArgb(), palette.glass.toArgb(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, r, r, fill)

        // Свечение изнутри, из верхнего левого угла — повторяет GlassCard в приложении.
        if (palette.glowAlpha > 0.02f) {
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.18f, h * 0.15f, maxOf(w, h) * 0.9f,
                    intArrayOf(
                        palette.accent.copy(alpha = 0.30f * palette.glowAlpha).toArgb(),
                        palette.accent.copy(alpha = 0f).toArgb(),
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(rect, r, r, glow)
        }

        // Неоновый контур.
        val strokeWidth = maxOf(1.5f, w * 0.004f)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                palette.accent.copy(alpha = 0.10f + 0.55f * palette.glowAlpha).toArgb(),
                palette.accentAlt.copy(alpha = 0.06f + 0.20f * palette.glowAlpha).toArgb(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(
            RectF(rect.left + strokeWidth / 2, rect.top + strokeWidth / 2,
                rect.right - strokeWidth / 2, rect.bottom - strokeWidth / 2),
            r, r, border,
        )

        cache.put(key, bmp)
        return bmp
    }

    /** Небольшая скруглённая плашка одного цвета — для маркеров и чипов внутри виджета. */
    fun pill(widthPx: Int, heightPx: Int, colorArgb: Int, cornerPx: Float): Bitmap {
        val key = "pill:$widthPx:$heightPx:$colorArgb:${cornerPx.toInt()}"
        cache[key]?.takeIf { !it.isRecycled }?.let { return it }
        val w = widthPx.coerceIn(2, MAX_DIMEN)
        val h = heightPx.coerceIn(2, MAX_DIMEN)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawRoundRect(
            RectF(0f, 0f, w.toFloat(), h.toFloat()), cornerPx, cornerPx,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb },
        )
        cache.put(key, bmp)
        return bmp
    }

    fun clear() = cache.evictAll()
}
