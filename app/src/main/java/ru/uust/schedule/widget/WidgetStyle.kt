package ru.uust.schedule.widget

import android.content.res.ColorStateList
import android.os.Build
import android.widget.RemoteViews

/**
 * Тонирует фон-плашку (widget_chip) с сохранением скругления. Тонировать фон
 * RemoteViews умеют только с Android 12; раньше — просто заливка цветом,
 * плашка выйдет прямоугольной.
 */
internal fun RemoteViews.tintChip(viewId: Int, color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color))
    } else {
        setInt(viewId, "setBackgroundColor", color)
    }
}

/** Сайт сокращает тип до «Лек»/«Пр»/«Лаб»; в узкой колонке под временем — так. */
internal fun lessonTypeLabel(raw: String): String = when (raw.trim().lowercase()) {
    "лек" -> "Лекция"
    "пр" -> "Практика"
    "лаб" -> "Лаб."
    else -> raw.trim()
}
