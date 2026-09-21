package ru.uust.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.Lesson
import java.time.LocalDate

/**
 * Виджет «День».
 *
 * Сверху — какой это день относительно сегодня: «Сегодня», «Завтра»,
 * «Послезавтра», дальше — день недели. После часа переключения виджет сам
 * показывает завтрашний день.
 *
 * Листается без единой видимой кнопки: левая четверть виджета — день назад,
 * правая — вперёд, середина открывает приложение. Свайп здесь недоступен
 * в принципе — лаунчер забирает горизонтальный жест на перелистывание
 * страниц рабочего стола, а RemoteViews не получает событий касания.
 */
class DayWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val snapshot = WidgetSnapshotLoader.load(context, id, prefs)
        provideContent { DayWidgetContent(snapshot) }
    }
}

@Composable
private fun DayWidgetContent(s: WidgetSnapshot) {
    val p = s.palette
    val compact = isCompactHeight(LocalSize.current.height)

    WidgetFrame(theme = s.theme) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 13.dp)
        ) {
            DayHeader(s, compact)
            Spacer(GlanceModifier.height(if (compact) 7.dp else 10.dp))

            when {
                !s.isConfigured -> Hint("Откройте приложение и выберите группу", p.textSecondary)
                s.day == null -> Hint("Нет данных", p.textMuted)
                s.day.realLessons.isEmpty() -> Hint("Пар нет", p.textSecondary)
                else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(s.day.realLessons) { lesson -> LessonRow(lesson, s, compact) }
                }
            }
        }

        // Зоны листания лежат ПОВЕРХ содержимого, иначе строки пар, кликабельные
        // во всю ширину, перехватывали бы нажатия у краёв. Середина зон не
        // кликабельна, поэтому тап по центру проваливается к содержимому и
        // открывает приложение.
        Row(GlanceModifier.fillMaxSize()) {
            TapZone(-1, GlanceModifier.defaultWeight())
            Box(GlanceModifier.defaultWeight().fillMaxHeight()) {}
            Box(GlanceModifier.defaultWeight().fillMaxHeight()) {}
            TapZone(1, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun DayHeader(s: WidgetSnapshot, compact: Boolean) {
    val p = s.palette

    Row(
        GlanceModifier
            .fillMaxWidth()
            .clickable(actionRunCallback<OpenAppAction>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight()) {
            Text(
                text = DayLogic.title(s.date, s.today),
                style = TextStyle(
                    color = p.textPrimary.glance(),
                    fontSize = if (compact) 16.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = DayLogic.shortDay(s.date) + ", " + DayLogic.formatDate(s.date),
                style = TextStyle(color = p.accent.glance(), fontSize = 11.sp),
                maxLines = 1,
            )
        }

        val count = s.day?.realLessons?.size ?: 0
        if (count > 0) {
            Text(
                text = "$count",
                style = TextStyle(
                    color = p.textMuted.glance(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

/** Невидимая зона листания — ни фона, ни текста, только область нажатия. */
@Composable
private fun TapZone(delta: Int, modifier: GlanceModifier) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                actionRunCallback<ShiftDayAction>(actionParametersOf(ShiftDayAction.DELTA to delta))
            ),
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
fun LessonRow(lesson: Lesson, s: WidgetSnapshot, compact: Boolean) {
    val p = s.palette
    val note = s.notes[lesson.subject]
    val argb = p.subjectArgb(lesson.subject, note?.hue ?: -1)
    val isNow = s.date == s.today && lesson.startMin >= 0 &&
        s.nowMinutes >= lesson.startMin && s.nowMinutes < lesson.endMin
    val isPast = s.date < s.today || (s.date == s.today && lesson.endMin in 0..s.nowMinutes)
    val textColor = if (isPast && !isNow) p.textMuted else p.textPrimary

    Row(
        GlanceModifier
            .fillMaxWidth()
            .padding(bottom = if (compact) 5.dp else 7.dp)
            .clickable(actionRunCallback<OpenAppAction>()),
        verticalAlignment = Alignment.CenterVertically,
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

        Column(GlanceModifier.defaultWeight()) {
            Text(
                text = lesson.subject,
                style = TextStyle(
                    color = textColor.glance(),
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = if (compact) 1 else 2,
            )
            if (!compact) {
                val meta = listOfNotNull(
                    lesson.type.ifBlank { null },
                    lesson.room.ifBlank { null },
                    if (s.showTeacher) lesson.teacher.ifBlank { null } else null,
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
    Box(
        GlanceModifier.fillMaxSize().clickable(actionRunCallback<OpenAppAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = color.glance(), fontSize = 12.sp), maxLines = 2)
    }
}

/** Листание дня нажатием на краевую зону. */
class ShiftDayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val delta = parameters[DELTA] ?: 0
        val today = LocalDate.now().toEpochDay().toInt()

        updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
            val anchor = prefs[KEY_OFFSET_ANCHOR] ?: 0
            // Смещение, накопленное в другой день, уже не имеет смысла.
            val current = if (anchor == today) prefs[KEY_DAY_OFFSET] ?: 0 else 0
            prefs[KEY_DAY_OFFSET] = (current + delta).coerceIn(-14, 14)
            prefs[KEY_OFFSET_ANCHOR] = today
        }
        DayWidget().update(context, glanceId)
    }

    companion object {
        val DELTA = ActionParameters.Key<Int>("delta")
    }
}

const val ARROW_LEFT = "‹"
const val ARROW_RIGHT = "›"

class DayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayWidget()
}
