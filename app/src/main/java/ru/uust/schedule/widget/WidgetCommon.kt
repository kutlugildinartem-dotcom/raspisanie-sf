package ru.uust.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.Spacer
import androidx.glance.layout.padding
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.glance.unit.ColorProvider
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.data.prefs.AppSettings
import ru.uust.schedule.data.prefs.AppTheme
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.repo.ScheduleRepository
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.DaySchedule
import ru.uust.schedule.ui.theme.Palette
import java.time.LocalDate
import java.time.LocalDateTime

/** Смещение дня, «налистанное» в виджете. Своё у каждого экземпляра. */
val KEY_DAY_OFFSET = intPreferencesKey("day_offset")

/** День, на который считалось смещение (epochDay) — чтобы сбросить его при смене суток. */
val KEY_OFFSET_ANCHOR = intPreferencesKey("offset_anchor")

/** Смещение недели для виджета «Неделя». */
val KEY_WEEK_OFFSET = intPreferencesKey("week_offset")

/**
 * Всё, что нужно виджету для отрисовки. Собирается до provideContent,
 * чтобы в composable не было обращений к диску.
 */
data class WidgetSnapshot(
    val settings: AppSettings,
    val theme: AppTheme,
    val groupId: Int,
    val groupName: String,
    val date: LocalDate,
    val day: DaySchedule?,
    val notes: Map<String, SubjectNoteEntity>,
    val nowMinutes: Int,
    val today: LocalDate,
    val showTeacher: Boolean = true,
) {
    val palette: Palette get() = Palette.from(theme)
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
        val groupId = if (config.groupIdOverride > 0) config.groupIdOverride else settings.groupId

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val base = DayLogic.defaultDate(now, settings.switchHour)

        // Налистанное смещение живёт до смены суток: иначе виджет, оставленный
        // на послезавтра, уезжал бы всё дальше от текущего дня.
        val anchor = prefs[KEY_OFFSET_ANCHOR] ?: 0
        val stored = prefs[KEY_DAY_OFFSET] ?: 0
        val offset = if (anchor.toLong() == today.toEpochDay()) stored + dayShift else dayShift

        val date = DayLogic.shift(base, offset)

        return WidgetSnapshot(
            settings = settings,
            theme = settings.theme,
            groupId = groupId,
            groupName = settings.groupName.ifBlank { repo.groupName(groupId).orEmpty() },
            date = date,
            day = if (groupId != 0) repo.cachedDay(groupId, date) else null,
            notes = if (groupId != 0) repo.notes(groupId) else emptyMap(),
            nowMinutes = now.hour * 60 + now.minute,
            today = today,
            showTeacher = config.showTeacher,
        )
    }
}

/** Непрозрачная подложка виджета — тот же цвет карточки, что и в приложении. */
@Composable
fun WidgetFrame(
    theme: AppTheme,
    cornerDp: Int = 24,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val wPx = (size.width.value * density).toInt().coerceAtLeast(8)
    val hPx = (size.height.value * density).toInt().coerceAtLeast(8)

    Box(modifier = GlanceModifier.fillMaxSize()) {
        Image(
            provider = ImageProvider(WidgetBackground.render(wPx, hPx, theme, cornerDp * density)),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
        content()
    }
}

fun androidx.compose.ui.graphics.Color.glance(): ColorProvider = ColorProvider(this)

fun Palette.subjectArgb(subject: String, hue: Int = -1): Int =
    Palette.subjectColor(subject, this, hue).toArgb()

/** Цветной маркер предмета. */
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

/**
 * Сколько пар помещается — от этого зависит, показывать ли аудиторию
 * и преподавателя. На низком виджете подробности съедают строки,
 * из-за чего видно всего одну пару.
 */
fun isCompactHeight(height: Dp): Boolean = height.value < 190f

/** Строка пары в Glance-виджетах. [compact] убирает вторую строку с подробностями. */
@Composable
fun LessonRow(lesson: ru.uust.schedule.domain.Lesson, s: WidgetSnapshot, compact: Boolean) {
    val p = s.palette
    val note = s.notes[lesson.subject]
    val argb = p.subjectArgb(lesson.subject, note?.hue ?: -1)
    val isNow = s.date == s.today && lesson.startMin >= 0 &&
        s.nowMinutes >= lesson.startMin && s.nowMinutes < lesson.endMin
    val isPast = s.date < s.today || (s.date == s.today && lesson.endMin in 0..s.nowMinutes)

    androidx.glance.layout.Row(
        GlanceModifier
            .fillMaxWidth()
            .padding(bottom = if (compact) 5.dp else 7.dp)
            .clickable(actionRunCallback<OpenAppAction>()),
        verticalAlignment = androidx.glance.layout.Alignment.CenterVertically,
    ) {
        Text(
            text = lesson.timeRange.take(5),
            style = TextStyle(
                color = (if (isNow) p.accent else p.textMuted).glance(),
                fontSize = 11.sp,
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(8.dp))
        ColorPill(argb, widthDp = 3, heightDp = if (compact) 16 else 26, cornerDp = 2)
        Spacer(GlanceModifier.width(8.dp))

        androidx.glance.layout.Column(GlanceModifier.defaultWeight()) {
            Text(
                text = lesson.subject,
                style = TextStyle(
                    color = (if (isPast && !isNow) p.textMuted else p.textPrimary).glance(),
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = if (compact) 1 else 2,
            )
            if (!compact) {
                // Короткая заметка вытесняет аудиторию: её пишут, чтобы не забыть.
                val meta = note?.note?.takeIf { it.isNotBlank() } ?: listOfNotNull(
                    lesson.type.ifBlank { null },
                    lesson.room.ifBlank { null },
                    if (s.showTeacher) {
                        note?.teacherFull?.ifBlank { null } ?: lesson.teacher.ifBlank { null }
                    } else null,
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = TextStyle(color = p.textMuted.glance(), fontSize = 10.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun Hint(text: String, color: androidx.compose.ui.graphics.Color) {
    androidx.glance.layout.Box(
        GlanceModifier.fillMaxSize().clickable(actionRunCallback<OpenAppAction>()),
        contentAlignment = androidx.glance.layout.Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = color.glance(), fontSize = 12.sp), maxLines = 2)
    }
}
