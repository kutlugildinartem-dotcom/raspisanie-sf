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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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

/**
 * Виджет «Заметка дня»: заметки только по тем предметам, которые стоят
 * в расписании на показываемый день. Предметы без заметки не показываются —
 * иначе виджет превращается в дубль списка пар.
 */
class NoteWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val snapshot = WidgetSnapshotLoader.load(context, id, prefs)
        provideContent { NoteContent(snapshot) }
    }
}

private data class NoteRow(val subject: String, val note: String, val argb: Int, val time: String)

@Composable
private fun NoteContent(s: WidgetSnapshot) {
    val p = s.palette
    val rows = s.day?.realLessons.orEmpty()
        .mapNotNull { lesson ->
            val note = s.notes[lesson.subject]?.note?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            NoteRow(
                subject = lesson.subject,
                note = note,
                argb = p.subjectArgb(lesson.subject, s.notes[lesson.subject]?.hue ?: -1),
                time = lesson.timeRange.take(5),
            )
        }
        .distinctBy { it.subject }

    WidgetFrame(theme = s.theme, cornerDp = 22) {
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(
                GlanceModifier.fillMaxWidth().clickable(actionRunCallback<OpenAppAction>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Заметки · " + (DayLogic.relativeLabel(s.date, s.today)
                        ?: DayLogic.formatDate(s.date)),
                    style = TextStyle(color = p.accent.glance(), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
            Spacer(GlanceModifier.height(7.dp))

            when {
                !s.isConfigured -> CenterHint("Выберите группу", p.textSecondary)
                rows.isEmpty() -> CenterHint("Заметок на этот день нет", p.textMuted)
                else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(rows) { row ->
                        Row(
                            GlanceModifier
                                .fillMaxWidth()
                                .padding(bottom = 7.dp)
                                .clickable(actionRunCallback<OpenAppAction>()),
                            verticalAlignment = Alignment.Top,
                        ) {
                            ColorPill(row.argb, widthDp = 3, heightDp = 28, cornerDp = 2)
                            Spacer(GlanceModifier.width(8.dp))
                            Column(GlanceModifier.defaultWeight()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        row.time,
                                        style = TextStyle(color = p.textMuted.glance(), fontSize = 10.sp),
                                        maxLines = 1,
                                    )
                                    Spacer(GlanceModifier.width(6.dp))
                                    Text(
                                        row.subject,
                                        style = TextStyle(
                                            color = p.textSecondary.glance(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    row.note,
                                    style = TextStyle(color = p.textPrimary.glance(), fontSize = 12.sp),
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteWidget()
}
