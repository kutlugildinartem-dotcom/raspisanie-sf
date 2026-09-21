package ru.uust.schedule.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Правило, какой день показывать по умолчанию.
 *
 * С полуночи и до [switchHour] — сегодня, после — завтра.
 * Воскресенья в расписании СФ УУНиТ нет, поэтому оно всегда перескакивает на понедельник.
 */
object DayLogic {

    fun defaultDate(now: LocalDateTime, switchHour: Int): LocalDate {
        val base = if (now.hour >= switchHour) now.toLocalDate().plusDays(1) else now.toLocalDate()
        return skipSunday(base)
    }

    fun skipSunday(date: LocalDate): LocalDate =
        if (date.dayOfWeek == DayOfWeek.SUNDAY) date.plusDays(1) else date

    /** Сдвиг на [delta] учебных дней: воскресенья пропускаются в обе стороны. */
    fun shift(date: LocalDate, delta: Int): LocalDate {
        if (delta == 0) return date
        val step = if (delta > 0) 1L else -1L
        var result = date
        repeat(kotlin.math.abs(delta)) {
            result = result.plusDays(step)
            if (result.dayOfWeek == DayOfWeek.SUNDAY) result = result.plusDays(step)
        }
        return result
    }

    /** Подпись дня относительно сегодняшней даты. */
    fun relativeLabel(date: LocalDate, today: LocalDate): String? = when (date) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        today.minusDays(1) -> "Вчера"
        else -> null
    }

    /** Пара, идущая прямо сейчас. */
    fun currentLesson(day: DaySchedule?, nowMinutes: Int): Lesson? =
        day?.realLessons?.firstOrNull { it.startMin in 0..nowMinutes && nowMinutes < it.endMin }

    /** Ближайшая пара сегодня, которая ещё не началась. */
    fun nextLessonToday(day: DaySchedule?, nowMinutes: Int): Lesson? =
        day?.realLessons?.firstOrNull { it.startMin > nowMinutes }

    /** Сколько минут до начала; отрицательное — уже идёт. */
    fun minutesUntil(lesson: Lesson, nowMinutes: Int): Int = lesson.startMin - nowMinutes

    fun formatCountdown(minutes: Int): String = when {
        minutes < 0 -> "идёт"
        minutes == 0 -> "началась"
        minutes < 60 -> "через $minutes мин"
        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "через $h ч" else "через $h ч $m мин"
        }
    }

    val RU_MONTHS = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    val RU_DAYS_SHORT = mapOf(
        DayOfWeek.MONDAY to "Пн", DayOfWeek.TUESDAY to "Вт", DayOfWeek.WEDNESDAY to "Ср",
        DayOfWeek.THURSDAY to "Чт", DayOfWeek.FRIDAY to "Пт", DayOfWeek.SATURDAY to "Сб",
        DayOfWeek.SUNDAY to "Вс",
    )

    fun formatDate(date: LocalDate): String =
        "${date.dayOfMonth} ${RU_MONTHS[date.monthValue - 1]}"

    fun shortDay(date: LocalDate): String = RU_DAYS_SHORT[date.dayOfWeek].orEmpty()
}
