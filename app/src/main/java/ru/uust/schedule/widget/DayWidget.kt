package ru.uust.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
 * По умолчанию показывает сегодня, а после часа переключения (по умолчанию 17:00) —
 * завтра. Стрелки листают дни прямо на главном экране; налистанное смещение
 * сбрасывается при смене суток, иначе виджет, оставленный на послезавтра,
 * уезжал бы всё дальше от текущего дня.
 */
class DayWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(
            context, PreferencesGlanceStateDefinition, id,
        )
        val snapshot = WidgetSnapshotLoader.load(context, id, prefs)
        provideContent { DayWidgetContent(snapshot) }
    }
}

@Composable
private fun DayWidgetContent(s: WidgetSnapshot) {
    val p = s.palette
    WidgetFrame(theme = s.theme) {
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            DayHeader(s)
            Spacer(GlanceModifier.height(8.dp))

            when {
                !s.isConfigured -> CenterHint("Откройте приложение и выберите группу", p.textSecondary)
                s.day == null -> CenterHint("Нет данных — обновите в приложении", p.textMuted)
                s.day.realLessons.isEmpty() -> CenterHint("Пар нет", p.textSecondary)
                else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(s.day.realLessons) { lesson -> WidgetLessonRow(lesson, s) }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(s: WidgetSnapshot) {
    val p = s.palette
    val relative = DayLogic.relativeLabel(s.date, s.today)
    val dateLine = DayLogic.shortDay(s.date) + " · " + DayLogic.formatDate(s.date)

    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ArrowButton(ARROW_LEFT, -1, p.accent)
        Spacer(GlanceModifier.width(4.dp))

        Column(GlanceModifier.defaultWeight().clickable(actionRunCallback<OpenAppAction>())) {
            Text(
                text = relative ?: dateLine,
                style = TextStyle(color = p.accent.glance(), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = if (relative != null) dateLine else s.groupName.ifBlank { "—" },
                style = TextStyle(color = p.textMuted.glance(), fontSize = 11.sp),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.width(4.dp))
        ArrowButton(ARROW_RIGHT, 1, p.accent)
    }
}

@Composable
private fun ArrowButton(glyph: String, delta: Int, color: Color) {
    Box(
        modifier = GlanceModifier
            .width(34.dp)
            .height(34.dp)
            .clickable(
                actionRunCallback<ShiftDayAction>(actionParametersOf(ShiftDayAction.DELTA to delta))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = TextStyle(color = color.glance(), fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** Строка пары. [compact] убирает аудиторию и преподавателя — для узких виджетов. */
@Composable
fun WidgetLessonRow(lesson: Lesson, s: WidgetSnapshot, compact: Boolean = false) {
    val p = s.palette
    val note = s.notes[lesson.subject]
    val argb = p.subjectArgb(lesson.subject, note?.hue ?: -1)
    val isNow = s.date == s.today && lesson.startMin >= 0 &&
        s.nowMinutes >= lesson.startMin && s.nowMinutes < lesson.endMin
    val isPast = s.date < s.today ||
        (s.date == s.today && lesson.endMin in 0..s.nowMinutes)

    Row(
        GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionRunCallback<OpenAppAction>()),
        verticalAlignment = Alignment.Top,
    ) {
        ColorPill(argb, widthDp = 3, heightDp = if (compact) 26 else 34, cornerDp = 2)
        Spacer(GlanceModifier.width(8.dp))

        Column(GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lesson.timeRange.ifBlank { lesson.number.toString() + " пара" },
                    style = TextStyle(
                        color = (if (isNow) p.accent else p.textMuted).glance(),
                        fontSize = 10.sp,
                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
                if (lesson.type.isNotBlank()) {
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        lesson.type,
                        style = TextStyle(color = Color(argb).glance(), fontSize = 10.sp),
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = lesson.subject,
                style = TextStyle(
                    color = (if (isPast && !isNow) p.textMuted else p.textPrimary).glance(),
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 2,
            )
            if (!compact) {
                val meta = listOfNotNull(
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
fun CenterHint(text: String, color: Color) {
    Box(
        GlanceModifier.fillMaxSize().clickable(actionRunCallback<OpenAppAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = color.glance(), fontSize = 13.sp), maxLines = 3)
    }
}

/** Листание дня стрелками. */
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
            // Смещение, накопленное в другой день, уже не имеет смысла — начинаем с нуля.
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
