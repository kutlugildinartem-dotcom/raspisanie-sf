package ru.uust.schedule.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import ru.uust.schedule.data.prefs.AppTheme
import ru.uust.schedule.ui.theme.Palette

/**
 * Подложка виджета.
 *
 * Рисуется в Bitmap, потому что GlanceModifier.cornerRadius требует API 31,
 * а minSdk здесь 26. Заливка намеренно непрозрачная: поверх обоев телефона
 * полупрозрачный виджет превращается в нечитаемое пятно.
 */
object WidgetBackground {

    /** Выше этого разрешения RemoteViews упирается в лимит памяти на bitmap. */
    private const val MAX_DIMEN = 720

    private val cache = LruCache<String, Bitmap>(12)

    fun render(widthPx: Int, heightPx: Int, theme: AppTheme, cornerRadiusPx: Float): Bitmap {
        val scale = minOf(1f, MAX_DIMEN.toFloat() / maxOf(widthPx, heightPx).coerceAtLeast(1))
        val w = (widthPx * scale).toInt().coerceAtLeast(8)
        val h = (heightPx * scale).toInt().coerceAtLeast(8)
        val r = cornerRadiusPx * scale

        val key = "bg:$w:$h:${theme.hashCode()}:${r.toInt()}"
        cache[key]?.takeIf { !it.isRecycled }?.let { return it }

        val palette = Palette.from(theme)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        canvas.drawRoundRect(
            RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surface.toArgb() },
        )

        cache.put(key, bmp)
        return bmp
    }

    /** Скруглённая плашка одного цвета — Glance не умеет рисовать фигуры сам. */
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
