package ru.uust.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.Lesson

/**
 * Компактный виджет «Следующая пара».
 *
 * Показывает идущую сейчас пару, а если её нет — ближайшую. Когда на сегодня
 * пары кончились, ищет первую пару следующего учебного дня, чтобы виджет
 * не превращался вечером в пустую плашку.
 */
class NextLessonWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val snapshot = WidgetSnapshotLoader.load(context, id, prefs)
        val target = resolveTarget(context, snapshot)
        provideContent { NextLessonContent(snapshot, target) }
    }

    private suspend fun resolveTarget(context: Context, s: WidgetSnapshot): Target? {
        if (!s.isConfigured) return null

        val todayDay = if (s.date == s.today) s.day
        else ru.uust.schedule.data.repo.ScheduleRepository.get(context).cachedDay(s.groupId, s.today)

        DayLogic.currentLesson(todayDay, s.nowMinutes)?.let { return Target(it, true, null) }
        DayLogic.nextLessonToday(todayDay, s.nowMinutes)?.let { return Target(it, false, null) }

        // Пары на сегодня закончились — заглядываем вперёд, но не дальше недели.
        val repo = ru.uust.schedule.data.repo.ScheduleRepository.get(context)
        var date = DayLogic.shift(s.today, 1)
        repeat(7) {
            val day = repo.cachedDay(s.groupId, date)
            val first = day?.realLessons?.firstOrNull()
            if (first != null) {
                return Target(first, false, DayLogic.relativeLabel(date, s.today)
                    ?: (DayLogic.shortDay(date) + " " + DayLogic.formatDate(date)))
            }
            date = DayLogic.shift(date, 1)
        }
        return null
    }

    data class Target(val lesson: Lesson, val isNow: Boolean, val dayLabel: String?)
}

@Composable
private fun NextLessonContent(s: WidgetSnapshot, target: NextLessonWidget.Target?) {
    val p = s.palette
    WidgetFrame(theme = s.theme, cornerDp = 22) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(actionRunCallback<OpenAppAction>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            if (!s.isConfigured) {
                Text(
                    "Выберите группу",
                    style = TextStyle(color = p.textSecondary.glance(), fontSize = 13.sp),
                )
                return@Column
            }
            if (target == null) {
                Text(
                    "Пар впереди нет",
                    style = TextStyle(color = p.textSecondary.glance(), fontSize = 14.sp,
                        fontWeight = FontWeight.Medium),
                )
                Text(
                    "обновите расписание",
                    style = TextStyle(color = p.textMuted.glance(), fontSize = 11.sp),
                )
                return@Column
            }

            val lesson = target.lesson
            val note = s.notes[lesson.subject]
            val argb = p.subjectArgb(lesson.subject, note?.hue ?: -1)
            val countdown = if (target.isNow) "идёт сейчас"
            else DayLogic.formatCountdown(DayLogic.minutesUntil(lesson, s.nowMinutes))

            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ColorPill(argb, widthDp = 3, heightDp = 14, cornerDp = 2)
                Spacer(GlanceModifier.width(7.dp))
                Text(
                    text = target.dayLabel ?: countdown,
                    style = TextStyle(
                        color = p.accent.glance(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }

            Spacer(GlanceModifier.height(5.dp))
            Text(
                text = lesson.subject,
                style = TextStyle(
                    color = p.textPrimary.glance(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = lesson.timeRange,
                style = TextStyle(color = p.textSecondary.glance(), fontSize = 12.sp),
                maxLines = 1,
            )

            val meta = listOfNotNull(
                lesson.room.ifBlank { null },
                if (s.showTeacher) lesson.teacher.ifBlank { null } else null,
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = TextStyle(color = p.textMuted.glance(), fontSize = 11.sp),
                    maxLines = 1,
                )
            }
            if (target.dayLabel != null) {
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = countdown,
                    style = TextStyle(color = p.textMuted.glance(), fontSize = 10.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

class NextLessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLessonWidget()
}
