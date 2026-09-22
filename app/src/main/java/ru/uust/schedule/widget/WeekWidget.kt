package ru.uust.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import ru.uust.schedule.data.repo.ScheduleRepository
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.DaySchedule
import java.time.LocalDate

/**
 * Виджет «Неделя»: все учебные дни одним списком, с листанием недель.
 * Каждый день сжат до строки «Пн 22 · 4 пары» плюс сами пары мелким шрифтом.
 */
class WeekWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val snapshot = WidgetSnapshotLoader.load(context, id, prefs)
        val weekOffset = prefs[KEY_WEEK_OFFSET] ?: 0
        val monday = ScheduleRepository.mondayOf(snapshot.today).plusWeeks(weekOffset.toLong())
        val days = if (snapshot.isConfigured) {
            ScheduleRepository.get(context).cachedWeek(snapshot.groupId, monday)
        } else emptyList()

        provideContent { WeekContent(snapshot, days, monday, weekOffset) }
    }
}

@Composable
private fun WeekContent(
    s: WidgetSnapshot,
    days: List<DaySchedule>,
    monday: LocalDate,
    weekOffset: Int,
) {
    val p = s.palette
    WidgetFrame(theme = s.theme) {
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {

            Column(
                GlanceModifier.fillMaxWidth().clickable(actionRunCallback<OpenAppAction>())
            ) {
                Text(
                    text = when (weekOffset) {
                        0 -> "Эта неделя"
                        1 -> "Следующая неделя"
                        -1 -> "Прошлая неделя"
                        else -> DayLogic.formatDate(monday)
                    },
                    style = TextStyle(
                        color = p.textPrimary.glance(), fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = DayLogic.formatDate(monday) + " — " +
                        DayLogic.formatDate(monday.plusDays(5)),
                    style = TextStyle(color = p.accent.glance(), fontSize = 11.sp),
                    maxLines = 1,
                )
            }

            Spacer(GlanceModifier.height(9.dp))

            val withLessons = days.filter { it.realLessons.isNotEmpty() }
            when {
                !s.isConfigured -> Hint("Выберите группу", p.textSecondary)
                withLessons.isEmpty() -> Hint("На эту неделю пар нет", p.textSecondary)
                else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(withLessons) { day -> WeekDayBlock(day, s) }
                }
            }
        }

        // Зоны листания недель — поверх содержимого, чтобы краевые нажатия
        // не перехватывались строками. Середина клики пропускает.
        Row(GlanceModifier.fillMaxSize()) {
            WeekTapZone(-1, GlanceModifier.defaultWeight())
            Box(GlanceModifier.defaultWeight().fillMaxHeight()) {}
            Box(GlanceModifier.defaultWeight().fillMaxHeight()) {}
            WeekTapZone(1, GlanceModifier.defaultWeight())
        }
    }
}

/** Невидимая зона листания недель. */
@Composable
private fun WeekTapZone(delta: Int, modifier: GlanceModifier) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                actionRunCallback<ShiftWeekAction>(
                    actionParametersOf(ShiftWeekAction.DELTA to delta)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
private fun WeekDayBlock(day: DaySchedule, s: WidgetSnapshot) {
    val p = s.palette
    val date = runCatching { LocalDate.parse(day.isoDate) }.getOrNull()
    val isToday = date == s.today

    Column(GlanceModifier.fillMaxWidth().padding(bottom = 7.dp)) {
        // Сегодняшний день крупнее и подписан словом: в списке из шести дней
        // одного лишь цвета не хватает, чтобы сразу найти нужную строку.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (date?.let { DayLogic.shortDay(it) + " " + it.dayOfMonth } ?: day.dayName),
                style = TextStyle(
                    color = (if (isToday) p.accent else p.textSecondary).glance(),
                    fontSize = if (isToday) 14.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = if (isToday) "сегодня" else day.realLessons.size.toString() + " пар",
                style = TextStyle(
                    color = (if (isToday) p.accent else p.textMuted).glance(),
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }
        day.realLessons.forEach { lesson ->
            val note = s.notes[lesson.subject]
            val argb = p.subjectArgb(lesson.subject, note?.hue ?: -1)
            Row(
                GlanceModifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorPill(argb, widthDp = 2, heightDp = 11, cornerDp = 1)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = lesson.timeRange.take(5),
                    style = TextStyle(color = p.textMuted.glance(), fontSize = 10.sp),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = lesson.subject,
                    style = TextStyle(color = p.textPrimary.glance(), fontSize = 11.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
