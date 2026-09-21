package ru.uust.schedule.data.prefs

import kotlinx.serialization.Serializable

/**
 * Параметры неоновой темы. Цвет задаётся в HSV, чтобы один ползунок оттенка
 * перекрашивал всё приложение связно, не ломая контраст.
 */
@Serializable
data class NeonTheme(
    /** Оттенок акцента, 0..360. */
    val hue: Float = 186f,
    /** Насыщенность акцента, 0..1. */
    val saturation: Float = 0.85f,
    /** Яркость акцента, 0..1. */
    val brightness: Float = 1.0f,
    /** Прозрачность стеклянных карточек, 0..1. */
    val glassOpacity: Float = 0.30f,
    /** Сила свечения, 0..1. */
    val glow: Float = 0.6f,
    /** Светлота фона, 0..1: 0 — чистый чёрный (AMOLED), 1 — заметно поднятый. */
    val backgroundLift: Float = 0.22f,
    /** Второй оттенок для градиента; смещение от [hue] в градусах. */
    val hueSpread: Float = 60f,
) {
    companion object {
        val Default = NeonTheme()

        val PRESETS: List<Pair<String, NeonTheme>> = listOf(
            "Cyan" to NeonTheme(hue = 186f),
            "Magenta" to NeonTheme(hue = 312f, hueSpread = -50f),
            "Violet" to NeonTheme(hue = 268f, hueSpread = 70f),
            "Lime" to NeonTheme(hue = 96f, saturation = 0.9f, hueSpread = 50f),
            "Amber" to NeonTheme(hue = 38f, hueSpread = -30f),
            "Rose" to NeonTheme(hue = 346f, hueSpread = 40f),
            "Ice" to NeonTheme(hue = 205f, saturation = 0.55f, backgroundLift = 0.3f),
            "Toxic" to NeonTheme(hue = 140f, saturation = 1f, glow = 0.85f),
            "AMOLED" to NeonTheme(hue = 186f, backgroundLift = 0f, glassOpacity = 0.14f, glow = 0.35f),
            "Sunset" to NeonTheme(hue = 18f, hueSpread = 90f, glow = 0.75f),
        )
    }
}

/** Настройки одного экземпляра виджета. */
@Serializable
data class WidgetConfig(
    /** null — наследовать тему виджетов. */
    val themeOverride: NeonTheme? = null,
    /** Группа этого виджета; 0 — использовать основную группу приложения. */
    val groupIdOverride: Int = 0,
    /** Показывать преподавателя в компактных виджетах. */
    val showTeacher: Boolean = true,
)

@Serializable
data class AppSettings(
    val groupId: Int = 0,
    val groupName: String = "",
    val appTheme: NeonTheme = NeonTheme.Default,
    /** Тема виджетов; null — та же, что у приложения. */
    val widgetTheme: NeonTheme? = null,
    /** Час, после которого виджет «День» показывает завтра. */
    val switchHour: Int = 17,
    val notificationsEnabled: Boolean = false,
    val notifyMinutesBefore: Int = 15,
    val onboarded: Boolean = false,
) {
    val effectiveWidgetTheme: NeonTheme get() = widgetTheme ?: appTheme
}
