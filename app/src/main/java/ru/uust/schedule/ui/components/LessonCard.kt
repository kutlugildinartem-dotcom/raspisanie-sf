package ru.uust.schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.uust.schedule.data.local.LessonRecordEntity
import ru.uust.schedule.domain.Lesson
import ru.uust.schedule.ui.theme.LocalPalette
import ru.uust.schedule.ui.theme.Palette

/**
 * Карточка пары.
 *
 * Сверху выделено то, ради чего в расписание и смотрят на ходу: время, тип
 * занятия и кабинет. Преподаватель уходит вниз мелким — его знают наизусть,
 * а номер аудитории каждый раз ищут заново.
 */
@Composable
fun LessonCard(
    lesson: Lesson,
    modifier: Modifier = Modifier,
    isNow: Boolean = false,
    isPast: Boolean = false,
    note: String? = null,
    noteHue: Int = -1,
    /** Полное имя преподавателя, если пользователь его ввёл: сайт даёт только инициалы. */
    teacherFull: String? = null,
    record: LessonRecordEntity? = null,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val color = Palette.subjectColor(lesson.subject, palette, noteHue)
    val fade = if (isPast && !isNow) 0.45f else 1f

    Card(modifier.fillMaxWidth(), elevated = isNow, onClick = onClick) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)) {

            Box(
                Modifier
                    .width(4.dp)
                    .height(if (compact) 40.dp else 54.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = fade))
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {

                // Ключевая строка: когда, какое занятие и где.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lesson.timeRange.take(5),
                        style = MaterialTheme.typography.titleMedium,
                        color = (if (isNow) palette.accent else palette.textPrimary)
                            .copy(alpha = fade),
                        fontWeight = FontWeight.Bold,
                    )
                    if (lesson.type.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Tag(
                            // В узкой карточке (два столбца) разворачивать «Лек» в «Лекция»
                            // уже некуда — сократили бы место, отведённое под кабинет.
                            text = if (compact) lesson.type.trim() else lessonTypeLabel(lesson.type),
                            color = color.copy(alpha = fade),
                            filled = true,
                        )
                    }
                    if (lesson.room.isNotBlank() && !compact) {
                        Spacer(Modifier.width(6.dp))
                        Tag(
                            text = lesson.room,
                            color = palette.textSecondary.copy(alpha = fade),
                            filled = false,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    record?.grade?.takeIf { it > 0 }?.let { GradeBadge(it, palette) }
                }

                Spacer(Modifier.height(if (compact) 4.dp else 6.dp))

                Text(
                    text = lesson.subject,
                    style = if (compact) MaterialTheme.typography.bodyLarge
                    else MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary.copy(alpha = fade),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isPast && !isNow) TextDecoration.LineThrough else null,
                )

                if (compact) {
                    // В двух колонках места на бордюр-чип с кабинетом уже не было —
                    // выводим его простой строкой, она гарантированно помещается.
                    if (lesson.room.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = lesson.room,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted.copy(alpha = fade),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!record?.homework.isNullOrBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = record!!.homework,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (record.homeworkDone) palette.textMuted else palette.accent,
                            textDecoration = if (record.homeworkDone) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    val teacher = teacherFull?.ifBlank { null } ?: lesson.teacher.ifBlank { null }
                    if (teacher != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = teacher,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted.copy(alpha = fade),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (!record?.homework.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        HomeworkStrip(record!!, palette.accent)
                    }
                }

                if (!note.isNullOrBlank() && !compact) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(color.copy(alpha = 0.10f))
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Сайт сокращает тип до «Лек»/«Пр»/«Лаб» — разворачиваем, пока это влезает. */
private fun lessonTypeLabel(raw: String): String = when (raw.trim().lowercase()) {
    "лек" -> "Лекция"
    "пр" -> "Практика"
    "лаб" -> "Лаб"
    else -> raw.trim()
}

@Composable
private fun Tag(text: String, color: Color, filled: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (filled) Modifier.background(color.copy(alpha = 0.18f))
                else Modifier.border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun GradeBadge(grade: Int, palette: Palette) {
    val color = gradeColor(grade, palette.isDark)
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            grade.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Пастельные цвета оценок: приглушённая зелень, песок и терракота.
 * Насыщенность низкая намеренно — оценка не должна кричать с экрана,
 * особенно плохая.
 */
private fun gradeColor(grade: Int, isDark: Boolean): Color = when (grade) {
    5 -> if (isDark) Color(0xFF8FC9A8) else Color(0xFF4E8C6A)
    4 -> if (isDark) Color(0xFF9FC4D8) else Color(0xFF52819B)
    3 -> if (isDark) Color(0xFFD8C79B) else Color(0xFF9A854B)
    else -> if (isDark) Color(0xFFD9A69B) else Color(0xFFA56154)
}

@Composable
private fun HomeworkStrip(record: LessonRecordEntity, accent: Color) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(accent.copy(alpha = if (record.homeworkDone) 0.06f else 0.12f))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            record.homework,
            style = MaterialTheme.typography.bodyMedium,
            color = if (record.homeworkDone) palette.textMuted else palette.textSecondary,
            textDecoration = if (record.homeworkDone) TextDecoration.LineThrough else null,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** День без пар. Спокойная плашка, не восклицательный знак. */
@Composable
fun EmptyDayCard(text: String = "Пар нет", modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textSecondary,
            )
        }
    }
}
