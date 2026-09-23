package ru.uust.schedule.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Домашнее задание, готовое к показу в списке: уже со сроком и подписью
 * «сколько осталось».
 */
data class HomeworkItem(
    val subject: String,
    val text: String,
    val lessonDate: LocalDate,
    val lessonNumber: Int,
    val due: LocalDate,
    val done: Boolean,
    val attachments: Int,
) {
    fun daysLeft(today: LocalDate): Long = ChronoUnit.DAYS.between(today, due)

    /** В какую корзину списка попадает задание. Порядок групп — порядок срочности. */
    fun bucket(today: LocalDate): HomeworkBucket = when {
        done -> HomeworkBucket.Done
        due < today -> HomeworkBucket.Overdue
        due == today -> HomeworkBucket.Today
        due == today.plusDays(1) -> HomeworkBucket.Tomorrow
        daysLeft(today) <= 7 -> HomeworkBucket.ThisWeek
        else -> HomeworkBucket.Later
    }

    fun dueLabel(today: LocalDate): String {
        val days = daysLeft(today)
        return when {
            done -> "сделано"
            days < 0 -> "просрочено на ${plural(-days)}"
            days == 0L -> "сегодня"
            days == 1L -> "завтра"
            days <= 7 -> "через ${plural(days)}"
            else -> DayLogic.formatDate(due)
        }
    }

    private fun plural(days: Long): String {
        val n = days % 100
        val d = if (n in 11..19) 0L else n % 10
        val word = when (d) {
            1L -> "день"
            2L, 3L, 4L -> "дня"
            else -> "дней"
        }
        return "$days $word"
    }
}

enum class HomeworkBucket(val title: String) {
    Overdue("Просрочено"),
    Today("Сегодня"),
    Tomorrow("Завтра"),
    ThisWeek("На этой неделе"),
    Later("Позже"),
    Done("Сделано"),
}
