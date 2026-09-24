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
import kotlinx.coroutines.launch
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
 * Виджет «День»: плоский список карточек, каждая во весь виджет. Первая —
 * сегодня, после последней пары — завтра; следующие дни — свайпом вверх.
 * Горизонтальный свайп в виджете недоступен в принципе, его забирает
 * перелистывание страниц рабочего стола.
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
                views.setRemoteAdapter(R.id.day_list, intent)
                views.setEmptyView(R.id.day_list, R.id.day_empty)

                // Шаблон клика: сам элемент дописывает в него свои extras.
                val open = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                views.setPendingIntentTemplate(R.id.day_list, open)

                // После каждого обновления виджет снова стоит на текущем дне,
                // даже если перед этим его пролистали к следующим.
                views.setScrollPosition(R.id.day_list, 0)
                DayRolloverReceiver.scheduleReset(context)

                manager.updateAppWidget(appWidgetId, views)
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.day_list)
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
 * Готовит карточки дней для списка виджета.
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
    private var cardHeightDp = 180
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

        // Первая карточка — сегодня, пока пары не закончились; после последней
        // пары (или если сегодня пар нет вовсе) — завтра. Дальше идут следующие дни.
        val todayDay = repo.cachedDay(groupId, today)
        val lessonsToday = todayDay?.realLessons.orEmpty()
        val lastEnd = lessonsToday.map { it.endMin }.filter { it >= 0 }.maxOrNull()
        val startsTomorrow = lessonsToday.isEmpty() || (lastEnd != null && nowMinutes >= lastEnd)
        val start = if (startsTomorrow) today.plusDays(1) else today

        items = (0..DayWidgetReceiver.DAYS_FORWARD).map { i ->
            val date = start.plusDays(i.toLong())
            Item(date, if (date == today) todayDay else repo.cachedDay(groupId, date))
        }

        // Следующее переключение: сразу после последней пары, иначе — после полуночи,
        // когда «Завтра» становится «Сегодня».
        val switchAt = if (!startsTomorrow && lastEnd != null) {
            today.atStartOfDay().plusMinutes(lastEnd.toLong() + 1)
        } else {
            today.plusDays(1).atStartOfDay().plusMinutes(1)
        }
        DayRolloverReceiver.schedule(context, switchAt)
    }

    /**
     * Высота виджета в dp -> сколько строк поместится. Масштаб текста
     * увеличивает и высоту строки, иначе при крупном шрифте строки
     * наезжали бы друг на друга.
     */
    private fun measureRows() {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        // MAX_HEIGHT — высота в портретной ориентации, в которой виджет и видят.
        val heightDp = options
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            ?.takeIf { it > 0 }
            ?: options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)?.takeIf { it > 0 }
            ?: 180
        cardHeightDp = heightDp
        maxRows = (rowsAreaDp() / (36f * textScale)).toInt().coerceIn(1, 8)
    }

    /** Высота заголовка карточки: название дня (плашка с датой в той же строке) и отступы. */
    private fun headerDp(): Float = titleSp() * 1.35f + 30f

    private fun titleSp(): Float = (if (cardHeightDp < 130) 22f else 28f) * textScale

    /** Сколько места остаётся под пары — его они и делят поровну. */
    private fun rowsAreaDp(): Float = (cardHeightDp - headerDp()).coerceAtLeast(36f)

    override fun getCount(): Int = items.size.coerceAtLeast(1)

    /** Заглушка вместо системного «Загрузка…» — в стиле остальных карточек. */
    override fun getLoadingView(): RemoteViews = messageCard("Загружаем расписание")

    private fun newCard(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_item)
        // Карточка ровно во весь виджет — в списке видно только один день.
        // Задать высоту элементу списка RemoteViews умеют только с Android 12.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(
                R.id.item_root, cardHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP,
            )
        }
        // Тонирование белой фигуры вместо Bitmap: тот же вид, но в транзакцию
        // уходит идентификатор ресурса и один int, а не мегабайт пикселей.
        views.setInt(R.id.item_bg, "setColorFilter", palette.surface.toArgb())
        return views
    }

    private fun messageCard(text: String): RemoteViews {
        val views = newCard()
        views.setTextViewText(R.id.item_title, "RUUNIT")
        views.setTextColor(R.id.item_title, palette.textPrimary.toArgb())
        views.setViewVisibility(R.id.item_date, View.GONE)
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
        views.sp(R.id.item_title, titleSp())

        // Дата — залитой плашкой акцентного цвета, чтобы читалась с первого взгляда.
        views.setTextViewText(
            R.id.item_date,
            DayLogic.shortDay(item.date) + ", " + DayLogic.formatDate(item.date),
        )
        views.setTextColor(R.id.item_date, palette.onAccent.toArgb())
        views.sp(R.id.item_date, 15f * textScale)
        views.tintChip(R.id.item_date, palette.accent.toArgb())

        if (lessons.isEmpty()) {
            views.setViewVisibility(R.id.item_rows, View.GONE)
            views.setViewVisibility(R.id.item_empty, View.VISIBLE)
            views.setTextViewText(
                R.id.item_empty,
                if (item.day == null) "Нет данных" else "Пар нет",
            )
            views.setTextColor(R.id.item_empty, palette.textSecondary.toArgb())
            views.sp(R.id.item_empty, 20f * textScale)
        } else {
            views.setViewVisibility(R.id.item_empty, View.GONE)
            views.setViewVisibility(R.id.item_rows, View.VISIBLE)
            fillRows(views, item.date, lessons)
        }

        views.setOnClickFillInIntent(R.id.item_bg, Intent())
        return views
    }

    private fun RemoteViews.sp(viewId: Int, value: Float) {
        setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, value)
    }

    /**
     * Пары делят всю высоту карточки поровну, а шрифт подбирается под высоту
     * строки: мало пар — крупно, без пустого места внизу; много — мельче,
     * но всё помещается. Если все пары не влезают, сегодня сначала выпадают
     * уже прошедшие.
     *
     * Строка: слева время и тип занятия, по центру название до двух строк,
     * справа кабинет плашкой. Домашку и преподавателя виджет не показывает —
     * только то, что нужно, чтобы дойти до пары.
     */
    private fun fillRows(views: RemoteViews, date: LocalDate, lessons: List<Lesson>) {
        val shown = if (lessons.size > maxRows && date == today) {
            lessons.filterNot { it.endMin in 0..nowMinutes }.ifEmpty { lessons }.take(maxRows)
        } else {
            lessons.take(maxRows)
        }

        // Название может занять две строки — под них и считаем кегль.
        val rowDp = rowsAreaDp() / shown.size
        val subjectSp = minOf(rowDp / 2.75f, 21f * textScale).coerceAtLeast(12f)
        val timeSp = subjectSp * 0.9f
        val typeSp = (subjectSp * 0.66f).coerceAtLeast(10f)
        val showType = rowDp >= (timeSp + typeSp) * 1.3f
        val newApi = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

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
            val faded = isPast && !isNow
            val note = notes[lesson.subject]
            val subjectColor = Palette.subjectColor(lesson.subject, palette, note?.hue ?: -1)

            if (newApi) {
                views.setViewLayoutWidth(ids.left, timeSp * 3.7f, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(ids.pill, rowDp * 0.7f, TypedValue.COMPLEX_UNIT_DIP)
            }

            views.setTextViewText(ids.time, lesson.timeRange.take(5))
            views.setTextColor(
                ids.time,
                when {
                    isNow -> palette.accent
                    faded -> palette.textMuted
                    else -> palette.textPrimary
                }.toArgb(),
            )
            views.sp(ids.time, timeSp)

            val type = lessonTypeLabel(lesson.type)
            if (showType && type.isNotBlank()) {
                views.setViewVisibility(ids.type, View.VISIBLE)
                views.setTextViewText(ids.type, type)
                views.setTextColor(ids.type, palette.textMuted.toArgb())
                views.sp(ids.type, typeSp)
            } else {
                views.setViewVisibility(ids.type, View.GONE)
            }

            views.setInt(ids.pill, "setColorFilter", subjectColor.toArgb())

            views.setTextViewText(ids.subject, lesson.subject)
            views.setTextColor(
                ids.subject,
                (if (faded) palette.textMuted else palette.textPrimary).toArgb(),
            )
            views.sp(ids.subject, subjectSp)

            val room = lesson.room.trim()
            if (room.isNotBlank()) {
                views.setViewVisibility(ids.room, View.VISIBLE)
                views.setTextViewText(ids.room, room)
                views.setTextColor(
                    ids.room,
                    (if (faded) palette.textMuted else palette.accent).toArgb(),
                )
                views.tintChip(ids.room, palette.tint(if (faded) 0.08f else 0.2f).toArgb())
                views.sp(ids.room, subjectSp * 0.85f)
            } else {
                views.setViewVisibility(ids.room, View.GONE)
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
        val left: Int,
        val time: Int,
        val type: Int,
        val pill: Int,
        val subject: Int,
        val room: Int,
    )

    companion object {
        private val ROW_IDS = listOf(
            RowIds(R.id.row_1, R.id.row_left_1, R.id.row_time_1, R.id.row_type_1, R.id.row_pill_1, R.id.row_subject_1, R.id.row_room_1),
            RowIds(R.id.row_2, R.id.row_left_2, R.id.row_time_2, R.id.row_type_2, R.id.row_pill_2, R.id.row_subject_2, R.id.row_room_2),
            RowIds(R.id.row_3, R.id.row_left_3, R.id.row_time_3, R.id.row_type_3, R.id.row_pill_3, R.id.row_subject_3, R.id.row_room_3),
            RowIds(R.id.row_4, R.id.row_left_4, R.id.row_time_4, R.id.row_type_4, R.id.row_pill_4, R.id.row_subject_4, R.id.row_room_4),
            RowIds(R.id.row_5, R.id.row_left_5, R.id.row_time_5, R.id.row_type_5, R.id.row_pill_5, R.id.row_subject_5, R.id.row_room_5),
            RowIds(R.id.row_6, R.id.row_left_6, R.id.row_time_6, R.id.row_type_6, R.id.row_pill_6, R.id.row_subject_6, R.id.row_room_6),
            RowIds(R.id.row_7, R.id.row_left_7, R.id.row_time_7, R.id.row_type_7, R.id.row_pill_7, R.id.row_subject_7, R.id.row_room_7),
            RowIds(R.id.row_8, R.id.row_left_8, R.id.row_time_8, R.id.row_type_8, R.id.row_pill_8, R.id.row_subject_8, R.id.row_room_8),
        )
    }
}

/**
 * Два будильника виджетов.
 *
 * Смена дня: сразу после последней пары и после полуночи перерисовывает
 * виджеты, чтобы первая карточка сменилась с сегодня на завтра вовремя,
 * а не при следующем плановом обновлении раз в полчаса.
 *
 * Возврат к сегодня: через 15 минут после каждой перерисовки прокручивает
 * списки обоих виджетов в начало. Узнать, что пользователь пролистал виджет,
 * приложение не может, поэтому возвращаем по таймеру. Будильник не будит
 * телефон: если экран выключен, он сработает вскоре после включения.
 */
class DayRolloverReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RESET) {
            scrollToStart(context)
            scheduleReset(context)
            return
        }
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { WidgetUpdater.updateAllNow(context) }
            pending.finish()
        }
    }

    /** Частичное обновление: только прокрутка, карточки не пересобираются. */
    private fun scrollToStart(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        runCatching {
            val dayIds = manager.getAppWidgetIds(
                android.content.ComponentName(context, DayWidgetReceiver::class.java),
            )
            if (dayIds.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.widget_day_stack)
                views.setScrollPosition(R.id.day_list, 0)
                manager.partiallyUpdateAppWidget(dayIds, views)
            }
            val weekIds = manager.getAppWidgetIds(
                android.content.ComponentName(context, WeekWidgetReceiver::class.java),
            )
            if (weekIds.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.widget_week_stack)
                views.setScrollPosition(R.id.week_list, 0)
                manager.partiallyUpdateAppWidget(weekIds, views)
            }
        }
    }

    companion object {
        private const val ACTION_RESET = "ru.uust.schedule.widget.SCROLL_TO_TODAY"
        private const val RESET_AFTER_MINUTES = 15L

        /** Одно отложенное срабатывание на всё приложение: новое заменяет старое. */
        fun schedule(context: Context, at: LocalDateTime) {
            val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(context, DayRolloverReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val millis = at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            // Неточный будильник: разрешения на точные не нужно, сдвиг — минуты.
            runCatching {
                alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC, millis, pi)
            }
        }

        /** Возврат к сегодняшнему дню через 15 минут; повторный вызов переносит срок. */
        fun scheduleReset(context: Context) {
            val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, DayRolloverReceiver::class.java).setAction(ACTION_RESET),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val at = System.currentTimeMillis() + RESET_AFTER_MINUTES * 60_000L
            // RTC без WAKEUP — не будит телефон; окно в минуту не требует разрешений.
            runCatching {
                alarms.setWindow(android.app.AlarmManager.RTC, at, 60_000L, pi)
            }
        }
    }
}
