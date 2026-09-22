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
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.DaySchedule
import ru.uust.schedule.domain.Lesson
import ru.uust.schedule.ui.components.Card
import ru.uust.schedule.ui.components.EmptyDayCard
import ru.uust.schedule.ui.components.LessonCard
import ru.uust.schedule.ui.theme.LocalPalette
import ru.uust.schedule.ui.theme.Palette
import java.time.LocalDate

/** Всё, что нужно любому из режимов отображения. */
data class LayoutData(
    val notes: Map<String, SubjectNoteEntity>,
    val records: Map<Pair<String, String>, LessonRecordEntity>,
    val today: LocalDate,
    val nowMinutes: Int,
    val onLessonClick: (LocalDate, Lesson) -> Unit,
) {
    fun recordFor(date: LocalDate, lesson: Lesson): LessonRecordEntity? =
        records[date.toString() to lesson.subject]

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

/** Две колонки: весь день виден сразу, без прокрутки. */
@Composable
fun GridLayout(date: LocalDate, day: DaySchedule?, data: LayoutData, loading: Boolean) {
    val lessons = day?.takeIf { it.isoDate == date.toString() }?.realLessons.orEmpty()

    if (lessons.isEmpty()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            EmptyDayCard(if (day == null && loading) "Загружаем…" else "Пар нет")
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = SidePadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(lessons, key = { it.number }) { lesson ->
            LessonCard(
                lesson = lesson,
                isNow = data.isNow(date, lesson),
                isPast = data.isPast(date, lesson),
                note = data.notes[lesson.subject]?.note?.takeIf { it.isNotBlank() },
                noteHue = data.notes[lesson.subject]?.hue ?: -1,
                teacherFull = data.notes[lesson.subject]?.teacherFull,
                record = data.recordFor(date, lesson),
                compact = true,
                onClick = { data.onLessonClick(date, lesson) },
            )
        }
    }
}

/**
 * Таймлайн: вертикальная шкала дня с окнами между парами.
 *
 * Показывает не только пары, но и промежутки — по расписанию сразу видно,
 * где четыре часа свободны, а где пара идёт за парой.
 */
@Composable
fun TimelineLayout(date: LocalDate, day: DaySchedule?, data: LayoutData, loading: Boolean) {
    val palette = LocalPalette.current
    val lessons = day?.takeIf { it.isoDate == date.toString() }?.realLessons.orEmpty()

    if (lessons.isEmpty()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            EmptyDayCard(if (day == null && loading) "Загружаем…" else "Пар нет")
        }
        return
    }

    LazyColumn(contentPadding = SidePadding) {
        itemsIndexed(lessons) { index, lesson ->
            val previous = lessons.getOrNull(index - 1)
            val gap = if (previous != null && previous.endMin > 0 && lesson.startMin > 0) {
                lesson.startMin - previous.endMin
            } else 0

            // Окно меньше получаса — это перемена, о ней сообщать незачем.
            if (gap >= 30) GapRow(gap, palette)

            Row(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.width(22.dp).padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val color = Palette.subjectColor(
                        lesson.subject, palette, data.notes[lesson.subject]?.hue ?: -1,
                    )
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (data.isPast(date, lesson)) palette.divider else color)
                    )
                    if (index < lessons.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(78.dp)
                                .background(palette.divider)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).padding(bottom = 10.dp)) {
                    LessonEntry(date, lesson, data)
                }
            }
        }
    }
}

@Composable
private fun GapRow(minutes: Int, palette: Palette) {
    Row(
        Modifier.fillMaxWidth().padding(start = 32.dp, top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Окно ${formatGap(minutes)}",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
        )
    }
}

private fun formatGap(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m мин"
        m == 0 -> "$h ч"
        else -> "$h ч $m мин"
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
