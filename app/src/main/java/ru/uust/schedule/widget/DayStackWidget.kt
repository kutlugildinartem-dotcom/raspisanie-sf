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
import ru.uust.schedule.data.local.LessonRecordEntity
import ru.uust.schedule.data.local.RecordKey
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.data.prefs.AppTheme
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.repo.ScheduleRepository
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.DaySchedule
import ru.uust.schedule.domain.Lesson
import ru.uust.schedule.ui.theme.Palette
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Виджет «День» со свайпом.
 *
 * Написан на классических RemoteViews, а не на Glance, ради StackView: это
 * единственная коллекция, которая сама обрабатывает жест пальцем — лаунчер
 * отдаёт ей вертикальный свайп. Горизонтальный свайп в виджете недоступен
 * в принципе, его забирает перелистывание страниц рабочего стола.
 *
 * Все подложки рисуются XML-фигурами с тонированием, а не Bitmap'ами.
 * Bitmap на элемент коллекции весил около мегабайта и упирался в лимит
 * binder-транзакции — из-за этого виджет не показывал вообще ничего.
 */
class DayWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> render(context, manager, id) }
    }

    /** Пользователь изменил размер виджета — число строк пересчитывается. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        render(context, manager, appWidgetId)
    }

    companion object {
        const val DAYS_BACK = 0
        const val DAYS_FORWARD = 21

        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            // Лаунчер показывает голое «Не удалось обновить виджет» без деталей,
            // если apply() уронит исключение. Ловим здесь и рисуем текст ошибки
            // прямо на виджете — это единственный способ узнать причину без
            // доступа к logcat пользователя.
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_day_stack)

                val intent = Intent(context, DayStackService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    // Intent.filterEquals() не сравнивает extras — без уникального data
                    // Android считает интенты двух разных виджетов одинаковыми и отдаёт
                    // им общее соединение с фабрикой, так что оба показывают одно и то же
                    // (или ничего, если фабрика повисает). Обычный иерархический URI —
                    // без обратной сериализации самого intent через toUri(), которая
                    // на некоторых прошивках не переживает проход через Binder лаунчера.
                    data = android.net.Uri.parse("widget://day/$appWidgetId")
                }
                views.setRemoteAdapter(R.id.day_stack, intent)
                views.setEmptyView(R.id.day_stack, R.id.day_empty)

                // Шаблон клика: сам элемент дописывает в него свои extras.
                val open = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                views.setPendingIntentTemplate(R.id.day_stack, open)

                val palette = runCatching {
                    Palette.from(runBlocking { SettingsStore.get(context).current() }.theme)
                }.getOrDefault(Palette.from(AppTheme.Default))
                views.setInt(R.id.nav_bg, "setColorFilter", palette.surface.toArgb())
                views.setTextColor(R.id.nav_prev, palette.accent.toArgb())
                views.setTextColor(R.id.nav_next, palette.accent.toArgb())
                views.setTextColor(R.id.nav_today, palette.textPrimary.toArgb())
                views.setOnClickPendingIntent(
                    R.id.nav_prev, DayNavReceiver.intent(context, appWidgetId, DayNavReceiver.PREV),
                )
                views.setOnClickPendingIntent(
                    R.id.nav_next, DayNavReceiver.intent(context, appWidgetId, DayNavReceiver.NEXT),
                )
                views.setOnClickPendingIntent(
                    R.id.nav_today, DayNavReceiver.intent(context, appWidgetId, DayNavReceiver.TODAY),
                )

                manager.updateAppWidget(appWidgetId, views)
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.day_stack)
            } catch (e: Throwable) {
                showError(context, manager, appWidgetId, e)
            }
        }

        private fun showError(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            e: Throwable,
        ) {
            val error = RemoteViews(context.packageName, R.layout.widget_error)
            error.setTextViewText(R.id.widget_error_text, "RUUNIT: ${e.javaClass.simpleName}: ${e.message}")
            runCatching { manager.updateAppWidget(appWidgetId, error) }
        }
    }
}

class DayStackService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return DayStackFactory(applicationContext, appWidgetId)
    }
}

/**
 * Готовит карточки дней для StackView.
 *
 * Число видимых строк считается от реальной высоты виджета, а не подбирается
 * на глаз: иначе на низком виджете подробности съедают место и видна всего
 * одна пара.
 */
private class DayStackFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private data class Item(val date: LocalDate, val day: DaySchedule?)

    private var items: List<Item> = emptyList()
    private var palette: Palette = Palette.from(AppTheme.Default)
    private var notes: Map<String, SubjectNoteEntity> = emptyMap()
    private var records: Map<RecordKey, LessonRecordEntity> = emptyMap()
    private var showTeacher = true
    private var maxRows = 4
    private var compact = false
    private var textScale = 1f
    private var today: LocalDate = LocalDate.now()
    private var nowMinutes = 0
    private var failure: String? = null

    override fun onCreate() = Unit

    /**
     * Любая ошибка здесь раньше оставляла виджет навсегда в состоянии
     * «Загрузка…»: StackView показывает загрузочный вид, пока фабрика не
     * отдаст элементы, и молча ждёт вечно. Теперь сбой превращается в
     * карточку с текстом, а не в вечное ожидание.
     */
    override fun onDataSetChanged() {
        failure = null
        runCatching { loadData() }.onFailure { error ->
            failure = error.message ?: "Не удалось прочитать расписание"
            items = emptyList()
        }
    }

    private fun loadData() = runBlocking {
        val store = SettingsStore.get(context)
        val repo = ScheduleRepository.get(context)

        val settings = store.current()
        val config = store.widgetConfig(appWidgetId)
        val groupId = if (config.groupIdOverride > 0) config.groupIdOverride else settings.groupId

        palette = Palette.from(settings.theme)
        showTeacher = config.showTeacher
        textScale = settings.widgetTextScale

        val now = LocalDateTime.now()
        today = now.toLocalDate()
        nowMinutes = now.hour * 60 + now.minute

        measureRows()

        if (groupId == 0) {
            items = emptyList()
            notes = emptyMap()
            return@runBlocking
        }

        notes = repo.notes(groupId)
        records = repo.recordsBetween(
            groupId,
            DayLogic.shift(today, -DayWidgetReceiver.DAYS_BACK),
            DayLogic.shift(today, DayWidgetReceiver.DAYS_FORWARD),
        )

        // Первая карточка — сегодня, дальше только будущие дни: StackView
        // открывается на первом элементе, а прошедшие дни в виджете не нужны.
        items = (0..DayWidgetReceiver.DAYS_FORWARD).map { i ->
            val date = today.plusDays(i.toLong())
            Item(date, repo.cachedDay(groupId, date))
        }
    }

    /**
     * Высота виджета в dp -> сколько строк поместится. Масштаб текста
     * увеличивает и высоту строки, иначе при крупном шрифте строки
     * наезжали бы друг на друга.
     */
    private fun measureRows() {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val heightDp = options
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            ?.takeIf { it > 0 }
            ?: 180

        compact = heightDp < 200
        val rowHeight = ((if (compact) 32 else 46) * textScale).toInt().coerceAtLeast(20)
        val header = (74 * textScale).toInt()
        // 50dp забирает полоска кнопок ‹ Сегодня › под карточкой.
        val available = (heightDp - header - 50).coerceAtLeast(rowHeight)
        maxRows = (available / rowHeight).coerceIn(1, 8)
    }

    // Никогда не возвращаем 0: пустой адаптер оставляет StackView в «Загрузке».
    override fun getCount(): Int = items.size.coerceAtLeast(1)

    /** Заглушка вместо системного «Загрузка…» — в стиле остальных карточек. */
    override fun getLoadingView(): RemoteViews = messageCard("Загружаем расписание")

    private fun newCard(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_item)
        // Тонирование белой фигуры вместо Bitmap: тот же вид, но в транзакцию
        // уходит идентификатор ресурса и один int, а не мегабайт пикселей.
        views.setInt(R.id.item_bg, "setColorFilter", palette.surface.toArgb())
        return views
    }

    private fun messageCard(text: String): RemoteViews {
        val views = newCard()
        views.setTextViewText(R.id.item_title, "RUUNIT")
        views.setTextColor(R.id.item_title, palette.textPrimary.toArgb())
        views.setTextViewText(R.id.item_date, "")
        views.setTextViewText(R.id.item_count, "")
        views.setViewVisibility(R.id.item_rows, View.GONE)
        views.setViewVisibility(R.id.item_empty, View.VISIBLE)
        views.setTextViewText(R.id.item_empty, text)
        views.setTextColor(R.id.item_empty, palette.textSecondary.toArgb())
        views.setOnClickFillInIntent(R.id.item_bg, Intent())
        return views
    }

    override fun getViewAt(position: Int): RemoteViews =
        runCatching { buildView(position) }
            .getOrElse { e -> messageCard("${e.javaClass.simpleName}: ${e.message}") }

    private fun buildView(position: Int): RemoteViews {
        failure?.let { return messageCard(it) }
        val item = items.getOrNull(position)
            ?: return messageCard("Откройте приложение и выберите группу")

        val views = newCard()
        val lessons = item.day?.realLessons.orEmpty()

        views.setTextViewText(R.id.item_title, DayLogic.title(item.date, today))
        views.setTextColor(R.id.item_title, palette.textPrimary.toArgb())
        views.setTextSize(R.id.item_title, 22f)

        views.setTextViewText(
            R.id.item_date,
            DayLogic.shortDay(item.date) + ", " + DayLogic.formatDate(item.date),
        )
        views.setTextColor(R.id.item_date, palette.accent.toArgb())
        views.setTextSize(R.id.item_date, 13f)

        views.setTextViewText(
            R.id.item_count,
            if (lessons.isEmpty()) "" else "${lessons.size} пар",
        )
        views.setTextColor(R.id.item_count, palette.textMuted.toArgb())
        views.setTextSize(R.id.item_count, 13f)

        if (lessons.isEmpty()) {
            views.setViewVisibility(R.id.item_rows, View.GONE)
            views.setViewVisibility(R.id.item_empty, View.VISIBLE)
            views.setTextViewText(
                R.id.item_empty,
                if (item.day == null) "Нет данных" else "Пар нет",
            )
            views.setTextColor(R.id.item_empty, palette.textSecondary.toArgb())
            views.setTextSize(R.id.item_empty, 15f)
        } else {
            views.setViewVisibility(R.id.item_empty, View.GONE)
            views.setViewVisibility(R.id.item_rows, View.VISIBLE)
            fillRows(views, item.date, lessons)
        }

        views.setOnClickFillInIntent(R.id.item_bg, Intent())
        return views
    }

    /** Размер шрифта с учётом выбранного пользователем масштаба виджета. */
    private fun RemoteViews.setTextSize(viewId: Int, baseSp: Float) {
        setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, baseSp * textScale)
    }

    private fun fillRows(views: RemoteViews, date: LocalDate, lessons: List<Lesson>) {
        val shown = lessons.take(maxRows)

        ROW_IDS.forEachIndexed { index, ids ->
            val lesson = shown.getOrNull(index)
            if (lesson == null) {
                views.setViewVisibility(ids.row, View.GONE)
                return@forEachIndexed
            }

            views.setViewVisibility(ids.row, View.VISIBLE)

            val isNow = date == today && lesson.startMin >= 0 &&
                nowMinutes >= lesson.startMin && nowMinutes < lesson.endMin
            val isPast = date < today || (date == today && lesson.endMin in 0..nowMinutes)
            val note = notes[lesson.subject]
            val subjectColor = Palette.subjectColor(lesson.subject, palette, note?.hue ?: -1)

            views.setTextViewText(ids.time, lesson.timeRange.take(5))
            views.setTextColor(ids.time, (if (isNow) palette.accent else palette.textMuted).toArgb())
            views.setTextSize(ids.time, 13f)

            views.setInt(ids.pill, "setColorFilter", subjectColor.toArgb())

            views.setTextViewText(ids.subject, lesson.subject)
            views.setTextColor(
                ids.subject,
                (if (isPast && !isNow) palette.textMuted else palette.textPrimary).toArgb(),
            )
            views.setTextSize(ids.subject, 15f)

            // Невыполненная домашка вытесняет всё остальное: ради неё в виджет и смотрят.
            // Точка перед текстом — тот же ненавязчивый маркер, что и в приложении.
            val record = records[RecordKey(date.toString(), lesson.subject, lesson.number)]
                ?: records[RecordKey(date.toString(), lesson.subject, 0)]
            val meta = when {
                record != null && record.hasHomework && !record.homeworkDone ->
                    "• " + record.homework
                !note?.note.isNullOrBlank() -> note!!.note
                else -> listOfNotNull(
                    lesson.type.ifBlank { null },
                    lesson.room.ifBlank { null },
                    if (showTeacher) {
                        note?.teacherFull?.ifBlank { null } ?: lesson.teacher.ifBlank { null }
                    } else null,
                ).joinToString(" · ")
            }

            if (compact || meta.isBlank()) {
                views.setViewVisibility(ids.meta, View.GONE)
            } else {
                views.setViewVisibility(ids.meta, View.VISIBLE)
                views.setTextViewText(ids.meta, meta)
                views.setTextColor(ids.meta, palette.textMuted.toArgb())
                views.setTextSize(ids.meta, 12f)
            }
        }
    }

    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() {
        items = emptyList()
    }

    private data class RowIds(
        val row: Int,
        val time: Int,
        val pill: Int,
        val subject: Int,
        val meta: Int,
    )

    companion object {
        private val ROW_IDS = listOf(
            RowIds(R.id.row_1, R.id.row_time_1, R.id.row_pill_1, R.id.row_subject_1, R.id.row_meta_1),
            RowIds(R.id.row_2, R.id.row_time_2, R.id.row_pill_2, R.id.row_subject_2, R.id.row_meta_2),
            RowIds(R.id.row_3, R.id.row_time_3, R.id.row_pill_3, R.id.row_subject_3, R.id.row_meta_3),
            RowIds(R.id.row_4, R.id.row_time_4, R.id.row_pill_4, R.id.row_subject_4, R.id.row_meta_4),
            RowIds(R.id.row_5, R.id.row_time_5, R.id.row_pill_5, R.id.row_subject_5, R.id.row_meta_5),
            RowIds(R.id.row_6, R.id.row_time_6, R.id.row_pill_6, R.id.row_subject_6, R.id.row_meta_6),
            RowIds(R.id.row_7, R.id.row_time_7, R.id.row_pill_7, R.id.row_subject_7, R.id.row_meta_7),
            RowIds(R.id.row_8, R.id.row_time_8, R.id.row_pill_8, R.id.row_subject_8, R.id.row_meta_8),
        )
    }
}

/**
 * Кнопки ‹ Сегодня › под карточками. Двигают StackView частичным
 * обновлением — остальная разметка виджета при этом не пересобирается.
 */
class DayNavReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val views = RemoteViews(context.packageName, R.layout.widget_day_stack)
        when (intent.action) {
            NEXT -> views.showNext(R.id.day_stack)
            PREV -> views.showPrevious(R.id.day_stack)
            TODAY -> views.setDisplayedChild(R.id.day_stack, 0)
            else -> return
        }
        runCatching {
            AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        const val NEXT = "ru.uust.schedule.widget.DAY_NEXT"
        const val PREV = "ru.uust.schedule.widget.DAY_PREV"
        const val TODAY = "ru.uust.schedule.widget.DAY_TODAY"

        fun intent(context: Context, appWidgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, DayNavReceiver::class.java)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Разный requestCode на каждую пару «виджет + кнопка», иначе
            // PendingIntent'ы разных кнопок и виджетов подменяют друг друга.
            val code = appWidgetId * 4 + when (action) { NEXT -> 1; PREV -> 2; else -> 3 }
            return PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
