package ru.uust.schedule.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import ru.uust.schedule.domain.Lesson
import ru.uust.schedule.ui.theme.LocalPalette
import ru.uust.schedule.ui.theme.Palette

/**
 * Карточка пары.
 *
 * Слева — время отдельной колонкой: взгляд идёт по нему сверху вниз, как по
 * таймлайну, и не прыгает внутрь карточки. Прошедшая пара приглушается, но
 * не прячется.
 */
@Composable
fun LessonCard(
    lesson: Lesson,
    modifier: Modifier = Modifier,
    isNow: Boolean = false,
    isPast: Boolean = false,
    note: String? = null,
    noteHue: Int = -1,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val color = Palette.subjectColor(lesson.subject, palette, noteHue)
    val fade = if (isPast && !isNow) 0.4f else 1f

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {

        Column(
            Modifier.width(52.dp).padding(top = 16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = lesson.timeRange.take(5),
                style = MaterialTheme.typography.labelMedium,
                color = (if (isNow) palette.accent else palette.textSecondary).copy(alpha = fade),
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                text = lesson.timeRange.takeLast(5),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted.copy(alpha = fade),
            )
        }

        Card(
            modifier = Modifier.weight(1f),
            elevated = isNow,
            onClick = onClick,
        ) {
            Row(Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {

                Box(
                    Modifier
                        .width(3.dp)
                        .height(if (note.isNullOrBlank()) 38.dp else 60.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = fade))
                )
                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = lesson.subject,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary.copy(alpha = fade),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isPast && !isNow) TextDecoration.LineThrough else null,
                    )
                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (lesson.type.isNotBlank()) {
                            TypeTag(lesson.type, color.copy(alpha = fade))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = listOfNotNull(
                                lesson.room.ifBlank { null },
                                lesson.teacher.ifBlank { null },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted.copy(alpha = fade),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (!note.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
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
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (isNow) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accent)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeTag(type: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            type,
            style = MaterialTheme.typography.labelSmall,
            color = color,
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
