package ru.uust.schedule.widget

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.Composable
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.data.prefs.AppSettings
import ru.uust.schedule.data.prefs.NeonTheme
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.repo.ScheduleRepository
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.DaySchedule
import ru.uust.schedule.ui.theme.NeonPalette
import java.time.LocalDate
import java.time.LocalDateTime

/** Смещение дня, которое пользователь «налистал» стрелками. Своё у каждого виджета. */
val KEY_DAY_OFFSET = intPreferencesKey("day_offset")

/** Дата, на которую был посчитан [KEY_DAY_OFFSET] (epochDay). Нужна, чтобы сбросить листание на новый день. */
val KEY_OFFSET_ANCHOR = intPreferencesKey("offset_anchor")

/** Смещение недели для виджета «Неделя». */
val KEY_WEEK_OFFSET = intPreferencesKey("week_offset")

/**
 * Снимок всего, что нужно виджету для отрисовки. Собирается один раз перед
 * provideContent, чтобы в composable не было обращений к диску.
 */
data class WidgetSnapshot(
    val settings: AppSettings,
    val theme: NeonTheme,
    val groupId: Int,
    val groupName: String,
    val date: LocalDate,
    val day: DaySchedule?,
    val notes: Map<String, SubjectNoteEntity>,
    val nowMinutes: Int,
    val today: LocalDate,
    val showTeacher: Boolean = true,
) {
    val palette: NeonPalette get() = NeonPalette.from(theme)
    val isConfigured: Boolean get() = groupId != 0
}

object WidgetSnapshotLoader {

    suspend fun load(
        context: Context,
        glanceId: GlanceId,
        prefs: Preferences,
        dayShift: Int = 0,
    ): WidgetSnapshot {
        val store = SettingsStore.get(context)
        val repo = ScheduleRepository.get(context)
        val appWidgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        }.getOrDefault(0)

        val settings = store.current()
        val config = store.widgetConfig(appWidgetId)
        val theme = config.themeOverride ?: settings.effectiveWidgetTheme
        val groupId = if (config.groupIdOverride > 0) config.groupIdOverride else settings.groupId

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val base = DayLogic.defaultDate(now, settings.switchHour)

        // Листание живёт до смены суток: иначе виджет, оставленный на «послезавтра»,
        // навсегда уезжал бы всё дальше от текущего дня.
        val anchor = prefs[KEY_OFFSET_ANCHOR] ?: 0
        val storedOffset = prefs[KEY_DAY_OFFSET] ?: 0
        val offset = if (anchor.toLong() == today.toEpochDay()) storedOffset + dayShift else dayShift

        val date = DayLogic.shift(base, offset)
        val day = if (groupId != 0) repo.cachedDay(groupId, date) else null
        val notes = if (groupId != 0) repo.notes(groupId) else emptyMap()

        return WidgetSnapshot(
            settings = settings,
            theme = theme,
            groupId = groupId,
            groupName = settings.groupName.ifBlank { repo.groupName(groupId).orEmpty() },
            date = date,
            day = day,
            notes = notes,
            nowMinutes = now.hour * 60 + now.minute,
            today = today,
            showTeacher = config.showTeacher,
        )
    }
}

/** Стеклянная подложка виджета — та же визуальная логика, что у GlassCard в приложении. */
@Composable
fun WidgetFrame(
    theme: NeonTheme,
    cornerDp: Int = 24,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val size: DpSize = LocalSize.current
    val density = context.resources.displayMetrics.density
    val wPx = (size.width.value * density).toInt().coerceAtLeast(8)
    val hPx = (size.height.value * density).toInt().coerceAtLeast(8)

    Box(modifier = GlanceModifier.fillMaxSize()) {
        Image(
            provider = ImageProvider(
                WidgetBackground.render(wPx, hPx, theme, cornerDp * density)
            ),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
        content()
    }
}

fun androidx.compose.ui.graphics.Color.glance(): ColorProvider = ColorProvider(this)

fun NeonPalette.subjectArgb(subject: String, hue: Int = -1): Int =
    NeonPalette.subjectColor(subject, this, hue).toArgb()

/** Скруглённый маркер заданного цвета — Glance не умеет рисовать фигуры напрямую. */
@Composable
fun ColorPill(
    argb: Int,
    widthDp: Int,
    heightDp: Int,
    cornerDp: Int = 2,
    modifier: GlanceModifier = GlanceModifier,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    Image(
        provider = ImageProvider(
            WidgetBackground.pill(
                (widthDp * density).toInt(),
                (heightDp * density).toInt(),
                argb,
                cornerDp * density,
            )
        ),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier.width(widthDp.dp).height(heightDp.dp),
    )
}

