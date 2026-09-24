package ru.uust.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.runBlocking
import ru.uust.schedule.MainActivity
import ru.uust.schedule.R
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.data.prefs.AppSettings
import ru.uust.schedule.data.prefs.AppTheme
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.repo.ScheduleRepository
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.Lesson
import ru.uust.schedule.ui.theme.Palette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Виджет «Неделя»: только текущая неделя, крупным текстом.
 *
 * Список (ListView листается свайпом вверх-вниз), который всегда начинается
 * с сегодняшнего дня — см. [WeekData].
 */
class WeekWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> render(context, manager, id) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        render(context, manager, appWidgetId)
    }

    companion object {

        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_week_stack)

                val intent = Intent(context, WeekStackService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    // См. комментарий в DayWidgetReceiver.render — простой URI вместо toUri().
                    data = android.net.Uri.parse("widget://week/$appWidgetId")
                }
                views.setRemoteAdapter(R.id.week_list, intent)
                views.setEmptyView(R.id.week_list, R.id.week_list_empty)

                val open = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                views.setPendingIntentTemplate(R.id.week_list, open)

                val snapshot = runCatching { runBlocking { WeekData.load(context, appWidgetId) } }
                    .getOrNull()
                val palette = snapshot?.palette ?: Palette.from(AppTheme.Default)
                val scale = snapshot?.textScale ?: 1f
                val monday = snapshot?.monday ?: ScheduleRepository.mondayOf(LocalDate.now())

                views.setInt(R.id.week_bg, "setColorFilter", palette.surface.toArgb())
                views.setTextViewText(R.id.week_title, snapshot?.title ?: "Эта неделя")
                views.setTextColor(R.id.week_title, palette.textPrimary.toArgb())
                views.setTextViewTextSize(R.id.week_title, TypedValue.COMPLEX_UNIT_SP, 22f * scale)
                views.setTextViewText(R.id.week_range, weekRange(monday))
                views.setTextColor(R.id.week_range, palette.accent.toArgb())
                views.setTextViewTextSize(R.id.week_range, TypedValue.COMPLEX_UNIT_SP, 14f * scale)
                views.setTextColor(R.id.week_list_empty, palette.textSecondary.toArgb())

                // Список начинается с сегодняшнего дня; после каждого обновления
                // возвращаемся в его начало, даже если перед этим прокрутили.
                views.setScrollPosition(R.id.week_list, 0)
                DayRolloverReceiver.scheduleReset(context)

                manager.updateAppWidget(appWidgetId, views)
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.week_list)
            } catch (e: Throwable) {
                val error = RemoteViews(context.packageName, R.layout.widget_error)
                error.setTextViewText(
                    R.id.widget_error_text, "RUUNIT: ${e.javaClass.simpleName}: ${e.message}",
                )
                runCatching { manager.updateAppWidget(appWidgetId, error) }
            }
        }

        private fun weekRange(monday: LocalDate): String {
            val sunday = monday.plusDays(6)
            return if (monday.month == sunday.month) {
                "${monday.dayOfMonth} — ${DayLogic.formatDate(sunday)}"
            } else {
                "${DayLogic.formatDate(monday)} — ${DayLogic.formatDate(sunday)}"
            }
        }
    }
}

/** Строка списка недели: заголовок дня, пара или пояснение вместо пар. */
internal sealed interface WeekRow {
    data class Day(val date: LocalDate) : WeekRow
    data class Item(val date: LocalDate, val lesson: Lesson) : WeekRow
    data class Note(val text: String) : WeekRow
}

/**
 * Всё, что нужно для отрисовки недели. Общая загрузка для render() и фабрики списка.
 *
 * Список начинается с сегодняшнего дня: прокрутить виджет так, чтобы нужная
 * строка оказалась вверху, RemoteViews не позволяют (setScrollPosition лишь
 * дотягивает её до нижнего края), поэтому прошедшие дни недели не показываем.
 * Если от текущей недели ничего не осталось (воскресенье без пар) — следующая.
 */
internal class WeekData(
    val rows: List<WeekRow>,
    val palette: Palette,
    val textScale: Float,
    val notes: Map<String, SubjectNoteEntity>,
    val title: String,
    val monday: LocalDate,
    val timeRange: Boolean = false,
) {
    companion object {
        suspend fun load(context: Context, appWidgetId: Int): WeekData {
            val store = SettingsStore.get(context)
            val repo = ScheduleRepository.get(context)
            val settings: AppSettings = store.current()
            val config = store.widgetConfig(appWidgetId)
            val groupId = if (config.groupIdOverride > 0) config.groupIdOverride else settings.groupId
            val palette = Palette.from(settings.theme)
            val today = LocalDate.now()
            val thisMonday = ScheduleRepository.mondayOf(today)

            if (groupId == 0) {
                return WeekData(
                    emptyList(), palette, settings.widgetTextScale, emptyMap(), "Эта неделя", thisMonday,
                )
            }

            var monday = thisMonday
            var title = "Эта неделя"
            var rows = buildRows(repo, groupId, thisMonday, from = today)
            if (rows.none { it is WeekRow.Day }) {
                val next = thisMonday.plusWeeks(1)
                val nextRows = buildRows(repo, groupId, next, from = next)
                if (nextRows.any { it is WeekRow.Day }) {
                    monday = next
                    title = "Следующая неделя"
                    rows = nextRows
                }
            }
            return WeekData(
                rows, palette, settings.widgetTextScale, repo.notes(groupId), title, monday,
                settings.showTimeRange,
            )
        }

        private suspend fun buildRows(
            repo: ScheduleRepository,
            groupId: Int,
            monday: LocalDate,
            from: LocalDate,
        ): List<WeekRow> {
            val byDate = repo.cachedWeek(groupId, monday)
                .mapNotNull { day ->
                    runCatching { LocalDate.parse(day.isoDate) }.getOrNull()?.let { it to day }
                }
                .toMap()
            if (byDate.isEmpty()) {
                return listOf(WeekRow.Note("Нет данных на эту неделю — откройте приложение, чтобы загрузить"))
            }
            return buildList {
                for (offset in 0..6) {
                    val date = monday.plusDays(offset.toLong())
                    if (date < from) continue
                    val lessons = byDate[date]?.realLessons.orEmpty()
                    // Воскресенье показываем, только если в нём есть пары.
                    if (date.dayOfWeek == DayOfWeek.SUNDAY && lessons.isEmpty()) continue
                    add(WeekRow.Day(date))
                    if (lessons.isEmpty()) add(WeekRow.Note("Пар нет"))
                    else lessons.forEach { add(WeekRow.Item(date, it)) }
                }
            }
        }
    }
}

class WeekStackService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return WeekListFactory(applicationContext, appWidgetId)
    }
}

private class WeekListFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<WeekRow> = emptyList()
    private var palette: Palette = Palette.from(AppTheme.Default)
    private var notes: Map<String, SubjectNoteEntity> = emptyMap()
    private var textScale = 1f
    private var timeRange = false
    private var today: LocalDate = LocalDate.now()
    private var nowMinutes = 0

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val now = LocalDateTime.now()
        today = now.toLocalDate()
        nowMinutes = now.hour * 60 + now.minute
        runCatching { runBlocking { WeekData.load(context, appWidgetId) } }
            .onSuccess { data ->
                rows = data.rows
                palette = data.palette
                notes = data.notes
                textScale = data.textScale
                timeRange = data.timeRange
            }
            .onFailure { error ->
                rows = listOf(WeekRow.Note(error.message ?: "Не удалось прочитать расписание"))
            }
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews =
        runCatching {
            when (val row = rows[position]) {
                is WeekRow.Day -> dayRow(row.date)
                is WeekRow.Item -> lessonRow(row.date, row.lesson)
                is WeekRow.Note -> noteRow(row.text)
            }
        }.getOrElse { e -> noteRow("${e.javaClass.simpleName}: ${e.message}") }

    private fun dayRow(date: LocalDate): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_week_day_row)
        val isToday = date == today
        val name = date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
            .replaceFirstChar { it.uppercase() }

        views.setTextViewText(R.id.day_name, "$name, ${DayLogic.formatDate(date)}")
        views.setTextColor(
            R.id.day_name,
            when {
                isToday -> palette.accent
                date < today -> palette.textMuted
                else -> palette.textPrimary
            }.toArgb(),
        )
        views.setSize(R.id.day_name, if (isToday) 19f else 17f)

        if (isToday) {
            views.setViewVisibility(R.id.day_badge, View.VISIBLE)
            views.setTextColor(R.id.day_badge, palette.onAccent.toArgb())
            views.setSize(R.id.day_badge, 12f)
            views.setInt(R.id.day_badge, "setBackgroundResource", R.drawable.widget_chip)
            views.tintChip(R.id.day_badge, palette.accent.toArgb())
        } else {
            views.setViewVisibility(R.id.day_badge, View.GONE)
        }

        views.setOnClickFillInIntent(R.id.row_root, Intent())
        return views
    }

    /** Та же строка, что и в виджете «День»: время и тип слева, кабинет плашкой справа. */
    private fun lessonRow(date: LocalDate, lesson: Lesson): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_week_lesson_row)
        val isNow = date == today && lesson.startMin >= 0 &&
            nowMinutes >= lesson.startMin && nowMinutes < lesson.endMin
        val faded = (date < today || (date == today && lesson.endMin in 0..nowMinutes)) && !isNow
        val note = notes[lesson.subject]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutWidth(
                R.id.lesson_left, (if (timeRange) 100f else 60f) * textScale, TypedValue.COMPLEX_UNIT_DIP,
            )
        }

        views.setTextViewText(R.id.lesson_time, lesson.timeLabel(timeRange))
        views.setTextColor(
            R.id.lesson_time,
            when {
                isNow -> palette.accent
                faded -> palette.textMuted
                else -> palette.textPrimary
            }.toArgb(),
        )
        views.setSize(R.id.lesson_time, 15f)

        val type = lessonTypeLabel(lesson.type)
        views.setViewVisibility(R.id.lesson_type, if (type.isBlank()) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.lesson_type, type)
        views.setTextColor(R.id.lesson_type, palette.textMuted.toArgb())
        views.setSize(R.id.lesson_type, 11f)

        views.setInt(
            R.id.lesson_pill, "setColorFilter",
            Palette.subjectColor(lesson.subject, palette, note?.hue ?: -1).toArgb(),
        )

        views.setTextViewText(R.id.lesson_subject, lesson.subject)
        views.setTextColor(
            R.id.lesson_subject,
            (if (faded) palette.textMuted else palette.textPrimary).toArgb(),
        )
        views.setSize(R.id.lesson_subject, 16f)

        val room = lesson.room.trim()
        if (room.isNotBlank()) {
            views.setViewVisibility(R.id.lesson_room, View.VISIBLE)
            views.setTextViewText(R.id.lesson_room, room)
            views.setTextColor(
                R.id.lesson_room,
                (if (faded) palette.textMuted else palette.accent).toArgb(),
            )
            views.tintChip(R.id.lesson_room, palette.tint(if (faded) 0.08f else 0.2f).toArgb())
            views.setSize(R.id.lesson_room, 14f)
        } else {
            views.setViewVisibility(R.id.lesson_room, View.GONE)
        }

        views.setOnClickFillInIntent(R.id.row_root, Intent())
        return views
    }

    /** «Пар нет» и сообщения об ошибках — та же строка пары, только с текстом. */
    private fun noteRow(text: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_week_lesson_row)
        views.setViewVisibility(R.id.lesson_left, View.GONE)
        views.setViewVisibility(R.id.lesson_pill, View.GONE)
        views.setViewVisibility(R.id.lesson_room, View.GONE)
        views.setTextViewText(R.id.lesson_subject, text)
        views.setTextColor(R.id.lesson_subject, palette.textMuted.toArgb())
        views.setSize(R.id.lesson_subject, 15f)
        views.setOnClickFillInIntent(R.id.row_root, Intent())
        return views
    }

    private fun RemoteViews.setSize(viewId: Int, baseSp: Float) {
        setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, baseSp * textScale)
    }

    override fun getLoadingView(): RemoteViews = noteRow("")
    override fun getViewTypeCount(): Int = 2
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
    override fun onDestroy() {
        rows = emptyList()
    }
}
