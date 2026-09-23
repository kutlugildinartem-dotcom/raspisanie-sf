package ru.uust.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.runBlocking
import ru.uust.schedule.MainActivity
import ru.uust.schedule.R
import ru.uust.schedule.data.prefs.AppTheme
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.repo.ScheduleRepository
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.DaySchedule
import ru.uust.schedule.ui.theme.Palette
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Виджет «Неделя»: недели листаются свайпом.
 *
 * Как и виджет дня, построен на StackView — это единственная коллекция
 * RemoteViews, которой лаунчер отдаёт жест пальцем. Раньше здесь были
 * невидимые зоны нажатия по краям, но листать пальцем привычнее.
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
        const val WEEKS_BACK = 4
        const val WEEKS_FORWARD = 8

        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_week_stack)

            val intent = Intent(context, WeekStackService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // См. комментарий в DayWidgetReceiver.render — простой URI вместо toUri().
                data = android.net.Uri.parse("widget://week/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.week_stack, intent)
            views.setEmptyView(R.id.week_stack, R.id.week_stack_empty)

            val open = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.week_stack, open)

            manager.updateAppWidget(appWidgetId, views)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.week_stack)
        }
    }
}

class WeekStackService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return WeekStackFactory(applicationContext, appWidgetId)
    }
}

private class WeekStackFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private data class Week(val monday: LocalDate, val days: List<DaySchedule>)

    private var weeks: List<Week> = emptyList()
    private var palette: Palette = Palette.from(AppTheme.Default)
    private var textScale = 1f
    private var today: LocalDate = LocalDate.now()
    private var failure: String? = null

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        failure = null
        runCatching { loadData() }.onFailure { error ->
            failure = error.message ?: "Не удалось прочитать расписание"
            weeks = emptyList()
        }
    }

    private fun loadData() = runBlocking {
        val store = SettingsStore.get(context)
        val repo = ScheduleRepository.get(context)

        val settings = store.current()
        val config = store.widgetConfig(appWidgetId)
        val groupId = if (config.groupIdOverride > 0) config.groupIdOverride else settings.groupId

        palette = Palette.from(settings.theme)
        textScale = settings.widgetTextScale
        today = LocalDateTime.now().toLocalDate()

        if (groupId == 0) {
            weeks = emptyList()
            return@runBlocking
        }

        // Первая карточка — текущая неделя: StackView всегда открывается на ней.
        val thisMonday = ScheduleRepository.mondayOf(today)
        weeks = buildList {
            for (i in 0..WeekWidgetReceiver.WEEKS_FORWARD) {
                val monday = thisMonday.plusWeeks(i.toLong())
                add(Week(monday, repo.cachedWeek(groupId, monday)))
            }
            for (i in 1..WeekWidgetReceiver.WEEKS_BACK) {
                val monday = thisMonday.minusWeeks(i.toLong())
                add(Week(monday, repo.cachedWeek(groupId, monday)))
            }
        }
    }

    override fun getCount(): Int = weeks.size.coerceAtLeast(1)

    override fun getLoadingView(): RemoteViews = messageCard("Загружаем расписание")

    private fun newCard(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_week_item)
        views.setInt(R.id.week_bg, "setColorFilter", palette.surface.toArgb())
        return views
    }

    private fun messageCard(text: String): RemoteViews {
        val views = newCard()
        views.setTextViewText(R.id.week_title, "RUUNIT")
        views.setTextColor(R.id.week_title, palette.textPrimary.toArgb())
        views.setTextViewText(R.id.week_range, "")
        views.setViewVisibility(R.id.week_days, View.GONE)
        views.setViewVisibility(R.id.week_empty, View.VISIBLE)
        views.setTextViewText(R.id.week_empty, text)
        views.setTextColor(R.id.week_empty, palette.textSecondary.toArgb())
        views.setOnClickFillInIntent(R.id.week_bg, Intent())
        return views
    }

    override fun getViewAt(position: Int): RemoteViews {
        failure?.let { return messageCard(it) }
        val week = weeks.getOrNull(position)
            ?: return messageCard("Откройте приложение и выберите группу")

        val views = newCard()
        val isCurrent = today >= week.monday && today <= week.monday.plusDays(6)

        views.setTextViewText(
            R.id.week_title,
            when {
                isCurrent -> "Эта неделя"
                week.monday > today -> "Следующая неделя"
                else -> "Прошедшая неделя"
            },
        )
        views.setTextColor(R.id.week_title, palette.textPrimary.toArgb())
        views.setTextSize(R.id.week_title, 20f)

        views.setTextViewText(R.id.week_range, weekRange(week.monday))
        views.setTextColor(R.id.week_range, palette.accent.toArgb())
        views.setTextSize(R.id.week_range, 13f)

        val withLessons = week.days.filter { it.realLessons.isNotEmpty() }
        if (withLessons.isEmpty()) {
            views.setViewVisibility(R.id.week_days, View.GONE)
            views.setViewVisibility(R.id.week_empty, View.VISIBLE)
            views.setTextViewText(R.id.week_empty, "На эту неделю пар нет")
            views.setTextColor(R.id.week_empty, palette.textSecondary.toArgb())
            views.setTextSize(R.id.week_empty, 15f)
        } else {
            views.setViewVisibility(R.id.week_empty, View.GONE)
            views.setViewVisibility(R.id.week_days, View.VISIBLE)
            fillDays(views, withLessons)
        }

        views.setOnClickFillInIntent(R.id.week_bg, Intent())
        return views
    }

    private fun fillDays(views: RemoteViews, days: List<DaySchedule>) {
        DAY_IDS.forEachIndexed { index, ids ->
            val day = days.getOrNull(index)
            if (day == null) {
                views.setViewVisibility(ids.container, View.GONE)
                return@forEachIndexed
            }

            views.setViewVisibility(ids.container, View.VISIBLE)

            val date = runCatching { LocalDate.parse(day.isoDate) }.getOrNull()
            val isToday = date == today

            views.setTextViewText(
                ids.name,
                date?.let { DayLogic.shortDay(it) + ", " + DayLogic.formatDate(it) }
                    ?: day.dayName,
            )
            views.setTextColor(
                ids.name,
                (if (isToday) palette.accent else palette.textPrimary).toArgb(),
            )
            views.setTextSize(ids.name, if (isToday) 16f else 14f)

            // «Сегодня» — залитая плашка акцентом, а не серая подпись:
            // среди шести одинаковых строк её иначе не найти взглядом.
            if (isToday) {
                views.setViewVisibility(ids.badge, View.VISIBLE)
                views.setTextViewText(ids.badge, "СЕГОДНЯ")
                views.setInt(ids.badge, "setBackgroundResource", R.drawable.widget_chip)
                views.setInt(ids.badge, "setBackgroundColor", palette.accent.toArgb())
                views.setTextColor(ids.badge, palette.onAccent.toArgb())
                views.setTextSize(ids.badge, 11f)
            } else {
                views.setViewVisibility(ids.badge, View.GONE)
            }

            views.setTextViewText(
                ids.lessons,
                day.realLessons.joinToString("\n") { lesson ->
                    listOfNotNull(
                        lesson.timeRange.take(5).ifBlank { null },
                        lesson.subject,
                        lesson.room.ifBlank { null },
                    ).joinToString(" · ")
                },
            )
            views.setTextColor(
                ids.lessons,
                (if (isToday) palette.textSecondary else palette.textMuted).toArgb(),
            )
            views.setTextSize(ids.lessons, 12f)
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

    private fun RemoteViews.setTextSize(viewId: Int, baseSp: Float) {
        setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, baseSp * textScale)
    }

    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() {
        weeks = emptyList()
    }

    private data class DayIds(val container: Int, val name: Int, val badge: Int, val lessons: Int)

    companion object {
        private val DAY_IDS = listOf(
            DayIds(R.id.day_1, R.id.day_name_1, R.id.day_badge_1, R.id.day_lessons_1),
            DayIds(R.id.day_2, R.id.day_name_2, R.id.day_badge_2, R.id.day_lessons_2),
            DayIds(R.id.day_3, R.id.day_name_3, R.id.day_badge_3, R.id.day_lessons_3),
            DayIds(R.id.day_4, R.id.day_name_4, R.id.day_badge_4, R.id.day_lessons_4),
            DayIds(R.id.day_5, R.id.day_name_5, R.id.day_badge_5, R.id.day_lessons_5),
            DayIds(R.id.day_6, R.id.day_name_6, R.id.day_badge_6, R.id.day_lessons_6),
        )
    }
}
