package ru.uust.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.runBlocking
import ru.uust.schedule.MainActivity
import ru.uust.schedule.R
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.repo.ScheduleRepository
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.DaySchedule
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
 * Дни лежат готовым списком [DAYS_BACK]..[DAYS_FORWARD] вокруг сегодняшнего,
 * а StackView сам держит позицию — поэтому «налистанное» состояние не нужно
 * хранить отдельно.
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
        const val DAYS_BACK = 7
        const val DAYS_FORWARD = 21

        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_day_stack)

            val intent = Intent(context, DayStackService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Без уникального data Android переиспользует фабрику другого виджета.
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
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

            manager.updateAppWidget(appWidgetId, views)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.day_stack)
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
    private var palette: Palette = Palette.from(ru.uust.schedule.data.prefs.AppTheme.Default)
    private var theme = ru.uust.schedule.data.prefs.AppTheme.Default
    private var notes: Map<String, ru.uust.schedule.data.local.SubjectNoteEntity> = emptyMap()
    private var records: Map<ru.uust.schedule.data.local.RecordKey, ru.uust.schedule.data.local.LessonRecordEntity> = emptyMap()
    private var showTeacher = true
    private var maxRows = 4
    private var compact = false
    private var today: LocalDate = LocalDate.now()
    private var nowMinutes = 0

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

        theme = settings.theme
        palette = Palette.from(theme)
        showTeacher = config.showTeacher

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

        // Порядок списка начинается с того дня, который виджет показывает
        // по умолчанию: StackView всегда открывается на первом элементе.
        val start = DayLogic.defaultDate(now, settings.switchHour)
        items = buildList {
            for (i in 0..DayWidgetReceiver.DAYS_FORWARD) {
                val date = DayLogic.shift(start, i)
                add(Item(date, repo.cachedDay(groupId, date)))
            }
            for (i in 1..DayWidgetReceiver.DAYS_BACK) {
                val date = DayLogic.shift(start, -i)
                add(Item(date, repo.cachedDay(groupId, date)))
            }
        }
    }

    /**
     * Высота виджета в dp -> сколько строк поместится.
     * Заголовок и отступы занимают примерно 62dp, полная строка — 40dp,
     * сжатая — 26dp.
     */
    private fun measureRows() {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val heightDp = options
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            ?.takeIf { it > 0 }
            ?: 160

        compact = heightDp < 200
        val rowHeight = if (compact) 26 else 40
        val available = (heightDp - 62).coerceAtLeast(rowHeight)
        maxRows = (available / rowHeight).coerceIn(1, 8)
    }

    private var failure: String? = null

    // Никогда не возвращаем 0: пустой адаптер оставляет StackView в «Загрузке».
    override fun getCount(): Int = items.size.coerceAtLeast(1)

    /** Заглушка вместо системного «Загрузка…» — в стиле остальных карточек. */
    override fun getLoadingView(): RemoteViews = messageCard("Загружаем расписание")

    private fun messageCard(text: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_item)
        views.setImageViewBitmap(
            R.id.item_bg,
            WidgetBackground.render(BG_WIDTH, BG_HEIGHT, theme, BG_CORNER),
        )
        views.setTextViewText(R.id.item_title, "RUUNIT")
        views.setTextColor(R.id.item_title, palette.textPrimary.toArgb())
        views.setTextViewText(R.id.item_date, "")
        views.setTextViewText(R.id.item_count, "")
        views.setViewVisibility(R.id.item_rows, android.view.View.GONE)
        views.setViewVisibility(R.id.item_empty, android.view.View.VISIBLE)
        views.setTextViewText(R.id.item_empty, text)
        views.setTextColor(R.id.item_empty, palette.textSecondary.toArgb())
        views.setOnClickFillInIntent(R.id.item_bg, Intent())
        return views
    }

    override fun getViewAt(position: Int): RemoteViews {
        failure?.let { return messageCard(it) }
        val item = items.getOrNull(position)
            ?: return messageCard("Откройте приложение и выберите группу")

        val views = RemoteViews(context.packageName, R.layout.widget_day_item)
        val lessons = item.day?.realLessons.orEmpty()

        views.setImageViewBitmap(
            R.id.item_bg,
            WidgetBackground.render(BG_WIDTH, BG_HEIGHT, theme, BG_CORNER),
        )

        views.setTextViewText(R.id.item_title, DayLogic.title(item.date, today))
        views.setTextColor(R.id.item_title, palette.textPrimary.toArgb())
        views.setTextViewText(
            R.id.item_date,
            DayLogic.shortDay(item.date) + ", " + DayLogic.formatDate(item.date),
        )
        views.setTextColor(R.id.item_date, palette.accent.toArgb())

        views.setTextViewText(R.id.item_count, if (lessons.isEmpty()) "" else "${lessons.size}")
        views.setTextColor(R.id.item_count, palette.textMuted.toArgb())

        if (lessons.isEmpty()) {
            views.setViewVisibility(R.id.item_rows, android.view.View.GONE)
            views.setViewVisibility(R.id.item_empty, android.view.View.VISIBLE)
            views.setTextViewText(
                R.id.item_empty,
                if (item.day == null) "Нет данных" else "Пар нет",
            )
            views.setTextColor(R.id.item_empty, palette.textSecondary.toArgb())
        } else {
            views.setViewVisibility(R.id.item_empty, android.view.View.GONE)
            views.setViewVisibility(R.id.item_rows, android.view.View.VISIBLE)
            fillRows(views, item.date, lessons)
        }

        views.setOnClickFillInIntent(R.id.item_bg, Intent())
        return views
    }

    private fun fillRows(
        views: RemoteViews,
        date: LocalDate,
        lessons: List<ru.uust.schedule.domain.Lesson>,
    ) {
        val shown = lessons.take(maxRows)

        ROW_IDS.forEachIndexed { index, ids ->
            val lesson = shown.getOrNull(index)
            if (lesson == null) {
                views.setViewVisibility(ids.row, android.view.View.GONE)
                return@forEachIndexed
            }

            views.setViewVisibility(ids.row, android.view.View.VISIBLE)

            val isNow = date == today && lesson.startMin >= 0 &&
                nowMinutes >= lesson.startMin && nowMinutes < lesson.endMin
            val isPast = date < today || (date == today && lesson.endMin in 0..nowMinutes)
            val note = notes[lesson.subject]
            val subjectColor = Palette.subjectColor(lesson.subject, palette, note?.hue ?: -1)

            views.setTextViewText(ids.time, lesson.timeRange.take(5))
            views.setTextColor(
                ids.time,
                (if (isNow) palette.accent else palette.textMuted).toArgb(),
            )

            views.setImageViewBitmap(
                ids.pill,
                WidgetBackground.pill(PILL_W, PILL_H, subjectColor.toArgb(), PILL_CORNER),
            )

            views.setTextViewText(ids.subject, lesson.subject)
            views.setTextColor(
                ids.subject,
                (if (isPast && !isNow) palette.textMuted else palette.textPrimary).toArgb(),
            )

            // Невыполненная домашка вытесняет всё остальное: ради неё в виджет и смотрят.
            // Точка перед текстом — тот же ненавязчивый маркер, что и в приложении.
            val record = records[
                ru.uust.schedule.data.local.RecordKey(
                    date.toString(), lesson.subject, lesson.number,
                )
            ] ?: records[
                ru.uust.schedule.data.local.RecordKey(date.toString(), lesson.subject, 0)
            ]
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
                views.setViewVisibility(ids.meta, android.view.View.GONE)
            } else {
                views.setViewVisibility(ids.meta, android.view.View.VISIBLE)
                views.setTextViewText(ids.meta, meta)
                views.setTextColor(ids.meta, palette.textMuted.toArgb())
            }
        }
    }

    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() {
        items = emptyList()
    }

    private data class RowIds(val row: Int, val time: Int, val pill: Int, val subject: Int, val meta: Int)

    companion object {
        /** Подложка рисуется один раз в фиксированном разрешении и растягивается по месту. */
        private const val BG_WIDTH = 600
        private const val BG_HEIGHT = 420
        private const val BG_CORNER = 46f
        private const val PILL_W = 8
        private const val PILL_H = 64
        private const val PILL_CORNER = 4f

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
