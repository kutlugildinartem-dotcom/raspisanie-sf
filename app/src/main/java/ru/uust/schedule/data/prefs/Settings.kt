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
    val onboarded: Boolean = false,
)
