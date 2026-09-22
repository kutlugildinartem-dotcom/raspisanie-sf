package ru.uust.schedule.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.uust.schedule.data.local.LessonRecordEntity
import ru.uust.schedule.data.local.RecordKey
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.DaySchedule
import ru.uust.schedule.domain.Lesson
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.uust.schedule.ui.components.Card
import ru.uust.schedule.ui.components.quietClickable
import ru.uust.schedule.ui.components.EmptyDayCard
import ru.uust.schedule.ui.components.LessonCard
import ru.uust.schedule.ui.theme.LocalPalette
import ru.uust.schedule.ui.theme.Palette
import java.time.LocalDate

/** Всё, что нужно любому из режимов отображения. */
data class LayoutData(
    val notes: Map<String, SubjectNoteEntity>,
    val records: Map<RecordKey, LessonRecordEntity>,
    val today: LocalDate,
    val nowMinutes: Int,
    val onLessonClick: (LocalDate, Lesson) -> Unit,
) {
    /**
     * Запись именно этой пары. Строки из старой базы лежат с номером 0 —
     * подхватываем их, пока пользователь не пересохранит запись.
     */
    fun recordFor(date: LocalDate, lesson: Lesson): LessonRecordEntity? =
        records[RecordKey(date.toString(), lesson.subject, lesson.number)]
            ?: records[RecordKey(date.toString(), lesson.subject, 0)]

    fun isNow(date: LocalDate, lesson: Lesson): Boolean =
        date == today && lesson.startMin >= 0 &&
            nowMinutes >= lesson.startMin && nowMinutes < lesson.endMin

    fun isPast(date: LocalDate, lesson: Lesson): Boolean =
        date < today || (date == today && lesson.endMin in 0..nowMinutes)
}

private val SidePadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)

/** Один день на экран. */
@Composable
fun DayLayout(date: LocalDate, day: DaySchedule?, data: LayoutData, loading: Boolean) {
    val lessons = day?.takeIf { it.isoDate == date.toString() }?.realLessons.orEmpty()

    if (lessons.isEmpty()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            EmptyDayCard(if (day == null && loading) "Загружаем…" else "Пар нет")
        }
        return
    }

    LazyColumn(
        contentPadding = SidePadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(lessons, key = { it.number }) { lesson ->
            LessonEntry(date, lesson, data)
        }
    }
}

/**
 * Лента: все дни подряд с заголовками.
 *
 * Пустые дни пропускаются — в непрерывном списке заголовок «Пар нет»
 * только отодвигал бы вниз то, ради чего список открыли.
 */
@Composable
fun FeedLayout(days: List<DaySchedule>, data: LayoutData) {
    val withLessons = days.filter { it.realLessons.isNotEmpty() }

    if (withLessons.isEmpty()) {
        Column(Modifier.padding(horizontal = 20.dp)) { EmptyDayCard("Пар нет") }
        return
    }

    LazyColumn(
        contentPadding = SidePadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        withLessons.forEach { day ->
            val date = runCatching { LocalDate.parse(day.isoDate) }.getOrNull() ?: return@forEach

            item(key = "h-${day.isoDate}") {
                DayDivider(date, data.today, day.realLessons.size)
            }
            items(day.realLessons, key = { "${day.isoDate}-${it.number}" }) { lesson ->
                LessonEntry(date, lesson, data)
            }
        }
    }
}

/**
 * Две колонки: вся неделя сразу, по дням с заголовками.
 *
 * Дни переключаются не здесь, а недельным переключателем сверху — в сетке
 * важно видеть неделю целиком, иначе смысл двух колонок теряется.
 */
@Composable
fun GridLayout(days: List<DaySchedule>, data: LayoutData) {
    val withLessons = days.filter { it.realLessons.isNotEmpty() }

    if (withLessons.isEmpty()) {
        Column(Modifier.padding(horizontal = 20.dp)) { EmptyDayCard("Пар нет") }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = SidePadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        withLessons.forEach { day ->
            val date = runCatching { LocalDate.parse(day.isoDate) }.getOrNull() ?: return@forEach

            item(key = "h-${day.isoDate}", span = { GridItemSpan(maxLineSpan) }) {
                DayDivider(date, data.today, day.realLessons.size)
            }
            items(day.realLessons, key = { "${day.isoDate}-${it.number}" }) { lesson ->
                val note = data.notes[lesson.subject]
                LessonCard(
                    lesson = lesson,
                    isNow = data.isNow(date, lesson),
                    isPast = data.isPast(date, lesson),
                    note = note?.note?.takeIf { it.isNotBlank() },
                    noteHue = note?.hue ?: -1,
                    teacherFull = note?.teacherFull,
                    record = data.recordFor(date, lesson),
                    compact = true,
                    onClick = { data.onLessonClick(date, lesson) },
                )
            }
        }
    }
}


@Composable
private fun DayDivider(date: LocalDate, today: LocalDate, count: Int) {
    val palette = LocalPalette.current
    val isToday = date == today

    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                DayLogic.title(date, today),
                style = MaterialTheme.typography.headlineSmall,
                color = if (isToday) palette.accent else palette.textPrimary,
            )
            Text(
                "${DayLogic.fullDay(date)}, ${DayLogic.formatDate(date)} · $count пар",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
        }
        if (isToday) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(palette.tint(0.16f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    "сегодня",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LessonEntry(date: LocalDate, lesson: Lesson, data: LayoutData) {
    val note = data.notes[lesson.subject]
    LessonCard(
        lesson = lesson,
        isNow = data.isNow(date, lesson),
        isPast = data.isPast(date, lesson),
        note = note?.note?.takeIf { it.isNotBlank() },
        noteHue = note?.hue ?: -1,
        teacherFull = note?.teacherFull,
        record = data.recordFor(date, lesson),
        onClick = { data.onLessonClick(date, lesson) },
    )
}

/**
 * Недельный переключатель для режимов, которые показывают не один день.
 * Диапазон подписан целиком: «14 — 20 сентября», а если неделя переходит
 * из месяца в месяц — с обоими месяцами.
 */
@Composable
fun WeekSwitcher(
    monday: LocalDate,
    today: LocalDate,
    onShift: (Int) -> Unit,
) {
    val palette = LocalPalette.current
    val sunday = monday.plusDays(6)
    val isCurrent = today >= monday && today <= sunday

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeekArrow("‹", "Прошлая неделя") { onShift(-1) }
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                weekRangeLabel(monday),
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
            )
            Text(
                when {
                    isCurrent -> "Эта неделя"
                    monday > today -> "Впереди"
                    else -> "Прошедшая"
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.accent,
            )
        }

        Spacer(Modifier.width(10.dp))
        WeekArrow("›", "Следующая неделя") { onShift(1) }
    }
}

/** «14 — 20 сентября», а на стыке месяцев «29 сентября — 5 октября». */
fun weekRangeLabel(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    return if (monday.month == sunday.month) {
        "${monday.dayOfMonth} — ${DayLogic.formatDate(sunday)}"
    } else {
        "${DayLogic.formatDate(monday)} — ${DayLogic.formatDate(sunday)}"
    }
}

@Composable
private fun WeekArrow(glyph: String, description: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .semantics { contentDescription = description }
            .quietClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.headlineSmall,
            color = palette.accent,
            fontWeight = FontWeight.Bold,
        )
    }
}
