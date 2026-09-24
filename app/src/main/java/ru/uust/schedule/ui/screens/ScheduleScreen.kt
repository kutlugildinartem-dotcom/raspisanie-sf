package ru.uust.schedule.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.uust.schedule.data.prefs.ScheduleLayout
import ru.uust.schedule.data.update.UpdateManager
import ru.uust.schedule.data.update.UpdateState
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.ui.ScheduleViewModel
import ru.uust.schedule.ui.components.CalendarSheet
import ru.uust.schedule.ui.components.DragHandle
import ru.uust.schedule.ui.components.EmptyDayCard
import ru.uust.schedule.ui.components.LessonSheet
import ru.uust.schedule.ui.components.UpdateBanner
import ru.uust.schedule.ui.components.quietClickable
import ru.uust.schedule.ui.theme.LocalPalette
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/**
 * Главный экран: один день за раз.
 *
 * Кнопок навигации и обновления здесь нет намеренно. Дни листаются свайпом
 * и полосой недели, а расписание подтягивается само — при открытии экрана
 * и фоновой задачей. Кнопка «обновить» означала бы, что приложению нельзя
 * доверять без ручного вмешательства.
 */
@Composable
fun ScheduleScreen(vm: ScheduleViewModel) {
    val palette = LocalPalette.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetTarget by remember { mutableStateOf<Pair<LocalDate, ru.uust.schedule.domain.Lesson>?>(null) }
    // Не сданные задания со сроком — чтобы показать текст под парой, на которую
    // выпадает срок, даже если запись создавалась на другом занятии.
    var dueHomework by remember {
        mutableStateOf<List<ru.uust.schedule.domain.HomeworkItem>>(emptyList())
    }
    LaunchedEffect(settings.groupId, ui.rangeRecords) {
        vm.loadHomework { items -> dueHomework = items.filterNot { it.done } }
    }

    val today = LocalDate.now()
    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }

    // Жест календаря: тянешь вниз — выезжает, тянешь вверх — прячется.
    //
    // Два правила, без которых жест ощущается случайным:
    //
    // 1. Пока список сам прокручивался в этом жесте, календарь не открывается.
    //    Иначе, докрутив список до верха одним движением, ты получал бы календарь
    //    в лицо — хотя просто листал расписание. Нужно отпустить палец и потянуть
    //    ещё раз, уже от самого верха.
    // 2. Сработавший жест доедает сам себя до конца. Иначе после закрытия
    //    календаря остаток того же движения уезжал бы в список, и экран
    //    продолжал бы листаться сам по себе.
    var pullAccum by remember { mutableStateOf(0f) }
    var listScrolled by remember { mutableStateOf(false) }
    var gestureHandled by remember { mutableStateOf(false) }

    val calendarConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {

            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (gestureHandled) return available.copy(x = 0f)

                // Закрытие ловим ДО списка: иначе он успевает уехать под пальцем,
                // и календарь закрывается уже на прокрученном экране.
                if (ui.calendarOpen && available.y < 0f) {
                    pullAccum += available.y
                    if (pullAccum < -60f) {
                        vm.toggleCalendar()
                        gestureHandled = true
                        pullAccum = 0f
                    }
                    return available.copy(x = 0f)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (gestureHandled) return available.copy(x = 0f)

                if (consumed.y != 0f) {
                    // Список прокрутился сам — значит жест начался не от края.
                    listScrolled = true
                    pullAccum = 0f
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                if (!ui.calendarOpen && !listScrolled && available.y > 0f) {
                    pullAccum += available.y
                    if (pullAccum > 80f) {
                        vm.toggleCalendar()
                        gestureHandled = true
                        pullAccum = 0f
                    }
                    return available.copy(x = 0f)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity =
                // Гасим инерцию сработавшего жеста, чтобы экран замер на месте.
                if (gestureHandled) available else androidx.compose.ui.unit.Velocity.Zero

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                // Палец отпущен — начинаем следующий жест с чистого листа.
                pullAccum = 0f
                listScrolled = false
                gestureHandled = false
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(calendarConnection)
            .pointerInput(settings.layout) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag) > 70f) {
                            val forward = drag < 0
                            // В недельных режимах горизонтальный свайп листает неделю,
                            // а не отдельный день — дня там на экране всё равно не видно.
                            if (settings.layout == ScheduleLayout.Feed ||
                                settings.layout == ScheduleLayout.Grid
                            ) {
                                vm.shiftWeek(if (forward) 1 else -1)
                            } else {
                                vm.shiftDay(if (forward) 1 else -1)
                            }
                        }
                        drag = 0f
                    },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            }
    ) {
        Header(
            date = date,
            today = today,
            groupName = settings.groupName,
            syncing = ui.loading,
            calendarOpen = ui.calendarOpen,
            onToggleCalendar = vm::toggleCalendar,
        )

        // Календарь выезжает сверху, пружиной — как системные панели на iPhone.
        // Выбор даты его не закрывает: по календарю обычно смотрят несколько дней подряд.
        AnimatedVisibility(
            visible = ui.calendarOpen,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f)) +
                fadeIn(animationSpec = tween(160)),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)) +
                fadeOut(animationSpec = tween(120)),
        ) {
            Column(
                Modifier.pointerInput(Unit) {
                    var drag = 0f
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (drag < -50f) vm.toggleCalendar()
                            drag = 0f
                        },
                        onVerticalDrag = { _, amount -> drag += amount },
                    )
                }
            ) {
                // Ручка над карточкой, а не под ней — так и выглядят системные
                // выезжающие панели: сначала «за что потянуть», потом содержимое.
                DragHandle()
                CalendarSheet(
                    month = ui.calendarMonth,
                    selected = date,
                    today = today,
                    counts = ui.lessonCounts,
                    onPick = vm::selectDate,
                    onMonthChange = vm::showMonth,
                )
            }
        }

        if (!ui.calendarOpen) {
            when (settings.layout) {
                // Недельные режимы листаются неделями, а не днями: полоса дат
                // в них показывала бы выбор, которого нет.
                ScheduleLayout.Feed, ScheduleLayout.Grid -> WeekSwitcher(
                    monday = ui.weekMonday,
                    today = today,
                    onShift = vm::shiftWeek,
                )

                else -> DateStrip(
                    date = date,
                    today = today,
                    onPick = vm::selectDate,
                    onOpenCalendar = vm::toggleCalendar,
                )
            }
        }

        UpdateBanner(
            state = updateState,
            onInstall = {
                val release = (updateState as? UpdateState.Available)?.release ?: return@UpdateBanner
                if (UpdateManager.canInstallPackages(context)) {
                    vm.installUpdate(release)
                } else {
                    context.startActivity(UpdateManager.unknownSourcesSettingsIntent(context))
                }
            },
            onDismiss = vm::dismissUpdate,
            modifier = Modifier.padding(top = 6.dp),
        )

        ui.error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.danger.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .quietClickable { vm.refresh(force = true) }
            ) {
                Text(
                    "$message · нажмите, чтобы повторить",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.danger,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val layoutData = LayoutData(
            notes = ui.notes,
            records = ui.rangeRecords,
            dueHomework = dueHomework,
            today = today,
            nowMinutes = nowMinutes,
            timeRange = settings.showTimeRange,
            onLessonClick = { d, lesson -> sheetTarget = d to lesson },
        )

        // Недельные режимы рисуют сразу много дней, анимация смены даты им не нужна.
        when (settings.layout) {
            ScheduleLayout.Feed -> {
                FeedLayout(days = ui.weekDays, data = layoutData, weekMonday = ui.weekMonday)
                return@Column
            }

            ScheduleLayout.Grid -> {
                GridLayout(days = ui.weekDays, data = layoutData, weekMonday = ui.weekMonday)
                return@Column
            }

            else -> AnimatedContent(
                targetState = date,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it / 4 * dir } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it / 4 * dir } + fadeOut())
                        .using(SizeTransform(clip = false))
                },
                label = "day",
            ) { shownDate ->
                DayLayout(shownDate, ui.day, layoutData, ui.loading)
            }
        }
    }

    sheetTarget?.let { (sheetDate, lesson) ->
        var upcomingLessonDates by remember { mutableStateOf(emptyList<LocalDate>()) }

        // Вложения, список ближайших заданий и даты следующих пар по этому
        // предмету подтягиваются под конкретную пару, как только окно открылось.
        LaunchedEffect(sheetDate, lesson.number) {
            vm.loadAttachments(sheetDate, lesson.subject, lesson.number)
            vm.loadUpcomingLessonDates(lesson.subject, sheetDate) { upcomingLessonDates = it }
        }

        LessonSheet(
            lesson = lesson,
            date = sheetDate,
            today = today,
            record = ui.rangeRecords[
                ru.uust.schedule.data.local.RecordKey(
                    sheetDate.toString(), lesson.subject, lesson.number,
                )
            ],
            attachments = ui.attachments,
            upcomingLessonDates = upcomingLessonDates,
            onDismiss = { sheetTarget = null },
            onAttach = { uri, name ->
                vm.attachFile(sheetDate, lesson.subject, lesson.number, uri, name)
            },
            onDetach = { uri -> vm.detachFile(sheetDate, lesson.subject, lesson.number, uri) },
            onSave = { homework, done, grade, dueDate ->
                vm.saveRecord(
                    sheetDate, lesson.subject, lesson.number, homework, done, grade, dueDate,
                )
                sheetTarget = null
            },
        )
    }
}
/**
 * Заголовок: крупно — какой это день относительно сегодня, мелко — дата и группа.
 * Индикатор синхронизации появляется, только когда она реально идёт.
 */
@Composable
private fun Header(
    date: LocalDate,
    today: LocalDate,
    groupName: String,
    syncing: Boolean,
    calendarOpen: Boolean,
    onToggleCalendar: () -> Unit,
) {
    val palette = LocalPalette.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 58.dp, bottom = 14.dp)
            // Потянуть вниз по заголовку — открыть календарь, как системную панель.
            .pointerInput(calendarOpen) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag > 50f && !calendarOpen) onToggleCalendar()
                        drag = 0f
                    },
                    onVerticalDrag = { _, amount -> drag += amount },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // День недели — крупная подпись акцентом: по ней ориентируются
            // в первую очередь, а раньше она терялась в мелком тексте.
            Text(
                text = DayLogic.fullDay(date).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = DayLogic.title(date, today),
                style = MaterialTheme.typography.displayMedium,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = DayLogic.formatDate(date),
                style = MaterialTheme.typography.titleMedium,
                color = palette.textSecondary,
            )
            Text(
                text = groupName,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
        }

        if (syncing) {
            CircularProgressIndicator(
                color = palette.textMuted,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }

        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (calendarOpen) palette.tint(0.16f) else palette.surface)
                .quietClickable(onToggleCalendar),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (calendarOpen) Icons.Rounded.Close else Icons.Rounded.CalendarMonth,
                if (calendarOpen) "Закрыть календарь" else "Календарь",
                tint = palette.accent,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** Полоса недели — она же навигация: заменяет стрелки и показывает, где ты находишься. */
/**
 * Лента дат: прокручивается влево-вправо на несколько недель вперёд и назад,
 * свайп вверх по ней открывает календарь месяца.
 *
 * Пришла на смену стрелкам: лента сразу показывает, где ты находишься,
 * и не требует считать нажатия.
 */
@Composable
private fun DateStrip(
    date: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val palette = LocalPalette.current
    val listState = rememberLazyListState()

    // Лента строится вокруг сегодняшнего дня, воскресенья пропускаются.
    val days = remember(today) {
        buildList {
            for (i in -WEEKS_BACK * 6..WEEKS_FORWARD * 6) {
                add(DayLogic.shift(today, i))
            }
        }.distinct()
    }
    val selectedIndex = days.indexOf(date)
    val density = androidx.compose.ui.platform.LocalDensity.current

    LaunchedEffect(date) {
        if (selectedIndex < 0) return@LaunchedEffect
        // Центрируем выбранный день в видимой области, а не прижимаем к краю —
        // тогда видно и что было, и что будет, в равной мере.
        val itemWidthPx = with(density) { (DATE_ITEM_WIDTH + DATE_ITEM_GAP).toPx() }
        val viewport = listState.layoutInfo.viewportSize.width.takeIf { it > 0 }
            ?: return@LaunchedEffect
        val centerOffset = (viewport / 2 - itemWidthPx / 2).toInt().coerceAtLeast(0)
        listState.animateScrollToItem(selectedIndex, -centerOffset)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var dragUp = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragUp < -40f) onOpenCalendar()
                        dragUp = 0f
                    },
                    onVerticalDrag = { _, amount -> dragUp += amount },
                )
            },
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(DATE_ITEM_GAP),
    ) {
        items(days, key = { it.toEpochDay() }) { d ->
            val selected = d == date
            val isToday = d == today

            Box(
                Modifier
                    .width(DATE_ITEM_WIDTH)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) palette.accent else palette.surface)
                    .quietClickable { onPick(d) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        DayLogic.shortDay(d),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) palette.onAccent.copy(alpha = 0.7f)
                        else palette.textMuted,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            selected -> palette.onAccent
                            isToday -> palette.accent
                            else -> palette.textSecondary
                        },
                        fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private const val WEEKS_BACK = 4
private const val WEEKS_FORWARD = 8
private val DATE_ITEM_WIDTH = 50.dp
private val DATE_ITEM_GAP = 6.dp
