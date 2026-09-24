package ru.uust.schedule.data.prefs

import kotlinx.serialization.Serializable

/**
 * Оформление приложения.
 *
 * Основа — плотный тёмный или светлый фон, цветом играет только содержимое.
 * Поэтому параметров всего три: светлая основа или тёмная, оттенок акцента
 * и его мягкость. Прозрачности и свечения здесь намеренно нет: на обоях
 * телефона полупрозрачный виджет становится нечитаемым.
 */
@Serializable
data class AppTheme(
    /** Оттенок акцента, 0..360. По умолчанию приглушённая бирюза. */
    val hue: Float = 174f,
    /** Мягкость акцента: 0 — почти серый, 1 — сочный. Неона нет ни на одном конце. */
    val intensity: Float = 0.5f,
    /** Тёмная основа или светлая. */
    val dark: Boolean = true,
    /** Насколько карточки выделяются из фона. */
    val contrast: Float = 0.5f,
) {
    companion object {
        val Default = AppTheme()

        /** Приглушённые тона: ни один не уходит в неон. */
        val PRESETS: List<Pair<String, Float>> = listOf(
            "Бирюза" to 174f,
            "Барвинок" to 232f,
            "Лаванда" to 262f,
            "Шалфей" to 145f,
            "Небо" to 200f,
            "Песок" to 38f,
            "Роза" to 348f,
            "Глина" to 18f,
            "Графит" to 220f,
        )
    }
}

/** Настройки одного экземпляра виджета. */
@Serializable
data class WidgetConfig(
    /** Группа этого виджета; 0 — основная группа приложения. */
    val groupIdOverride: Int = 0,
    val showTeacher: Boolean = true,
)

@Serializable
data class AppSettings(
    val groupId: Int = 0,
    val groupName: String = "",
    val theme: AppTheme = AppTheme.Default,
    /** Час, после которого виджет «День» показывает завтра. */
    val switchHour: Int = 17,
    val notificationsEnabled: Boolean = false,
    val notifyMinutesBefore: Int = 15,
    /** Пуш, когда пара на уже показанный день меняется на сайте (время, аудитория, отмена). */
    val notifyScheduleChanges: Boolean = false,
    /** Пуш, когда для следующей недели впервые появляются пары — сайт публикует её не сразу. */
    val notifyNextWeekAdded: Boolean = false,
    val onboarded: Boolean = false,
    val layout: ScheduleLayout = ScheduleLayout.Day,
    /** Когда последний раз проверяли обновление приложения — гасит проверки чаще раза в день. */
    val lastUpdateCheckAt: Long = 0L,
    /** Версия релиза, которую пользователь явно закрыл — не показывать баннер снова для неё же. */
    val dismissedUpdateVersion: String = "",
    /** Масштаб текста в виджетах: 1.0 обычный, 1.2 крупный, 1.45 очень крупный. */
    val widgetTextScale: Float = 1f,
    /** Напоминание накануне срока домашки: «Завтра домашка по предмету». */
    val homeworkReminder: Boolean = true,
    /** Час, в который приходит напоминание о домашке. */
    val homeworkReminderHour: Int = 10,
    /** За сколько дней до срока напоминать: 1, 2, 3 — то есть за 24, 48, 72 часа. */
    val homeworkReminderDays: List<Int> = listOf(1, 2, 3),
    /** Время пары диапазоном «10:10–11:40», а не только начало — и в приложении, и в виджетах. */
    val showTimeRange: Boolean = false,
)

/** Как выглядит главный экран. */
@kotlinx.serialization.Serializable
enum class ScheduleLayout(val title: String, val description: String) {
    /** Одна страница на день: крупно и ничего лишнего. */
    Day("День", "Один день на экран, листается свайпом"),

    /** Непрерывная лента с заголовками дней — видно, что будет дальше, без листания. */
    Feed("Лента", "Все дни подряд с разделителями"),

    /** Две колонки: пары видны целиком одним взглядом. */
    Grid("Две колонки", "Пары в два ряда, дни листаются"),
}
