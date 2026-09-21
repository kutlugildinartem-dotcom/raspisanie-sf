package ru.uust.schedule.widget

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.uust.schedule.MainActivity
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.repo.ScheduleRepository

/** Тап по виджету открывает приложение. */
class OpenAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }
}

/** Листание недели в виджете «Неделя». */
class ShiftWeekAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val delta = parameters[DELTA] ?: 0
        updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
            val current = prefs[KEY_WEEK_OFFSET] ?: 0
            prefs[KEY_WEEK_OFFSET] = (current + delta).coerceIn(-8, 8)
        }
        WeekWidget().update(context, glanceId)
    }

    companion object {
        val DELTA = ActionParameters.Key<Int>("delta")
    }
}

/**
 * Обновление из виджета. Тянет свежие данные с сайта, затем перерисовывает все виджеты.
 * Ошибка сети намеренно проглатывается: виджет остаётся на кеше, а не мигает сообщением.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val groupId = SettingsStore.get(context).current().groupId
        if (groupId != 0) {
            runCatching { ScheduleRepository.get(context).syncWeeks(groupId, listOf(0, 1)) }
        }
        WidgetUpdater.updateAll(context)
    }
}

/** Перерисовка всех размещённых виджетов — после смены темы, группы или данных. */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun updateAll(context: Context) {
        scope.launch { updateAllNow(context) }
    }

    suspend fun updateAllNow(context: Context) {
        // Фоны закешированы по теме; после смены цвета кеш обязан протухнуть.
        WidgetBackground.clear()
        val manager = GlanceAppWidgetManager(context)
        runCatching {
            manager.getGlanceIds(DayWidget::class.java).forEach { DayWidget().update(context, it) }
            manager.getGlanceIds(NextLessonWidget::class.java).forEach { NextLessonWidget().update(context, it) }
            manager.getGlanceIds(WeekWidget::class.java).forEach { WeekWidget().update(context, it) }
            manager.getGlanceIds(NoteWidget::class.java).forEach { NoteWidget().update(context, it) }
        }
    }
}
