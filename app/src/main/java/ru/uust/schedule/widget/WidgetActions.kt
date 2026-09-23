package ru.uust.schedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.uust.schedule.R

/**
 * Перерисовка всех размещённых виджетов — после смены темы, группы или данных.
 *
 * Оба виджета живут на классических RemoteViews со StackView: только эта
 * коллекция листается пальцем, а Glance такого не умеет.
 */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun updateAll(context: Context) {
        scope.launch { updateAllNow(context) }
    }

    suspend fun updateAllNow(context: Context) {
        runCatching {
            val widgets = AppWidgetManager.getInstance(context)

            widgets.getAppWidgetIds(ComponentName(context, DayWidgetReceiver::class.java))
                .forEach { id -> DayWidgetReceiver.render(context, widgets, id) }

            widgets.getAppWidgetIds(ComponentName(context, WeekWidgetReceiver::class.java))
                .forEach { id -> WeekWidgetReceiver.render(context, widgets, id) }
        }
    }

    /** Данные изменились — просим коллекции перечитать себя, не пересобирая разметку. */
    fun notifyDataChanged(context: Context) {
        runCatching {
            val widgets = AppWidgetManager.getInstance(context)
            widgets.notifyAppWidgetViewDataChanged(
                widgets.getAppWidgetIds(ComponentName(context, DayWidgetReceiver::class.java)),
                R.id.day_stack,
            )
            widgets.notifyAppWidgetViewDataChanged(
                widgets.getAppWidgetIds(ComponentName(context, WeekWidgetReceiver::class.java)),
                R.id.week_stack,
            )
        }
    }
}
