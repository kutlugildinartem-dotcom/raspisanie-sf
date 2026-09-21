package ru.uust.schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.uust.schedule.domain.Lesson
import ru.uust.schedule.ui.theme.LocalNeon
import ru.uust.schedule.ui.theme.NeonPalette

/**
 * Карточка пары. [isNow] подсвечивает идущую сейчас пару — это то,
 * ради чего на экран смотрят чаще всего.
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
    val neon = LocalNeon.current
    val color = NeonPalette.subjectColor(lesson.subject, neon, noteHue)
    val dim = if (isPast && !isNow) 0.45f else 1f

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        glowScale = if (isNow) 1.8f else 0.7f,
        accent = if (isNow) color else null,
    ) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            AccentBar(
                color = color.copy(alpha = dim),
                modifier = Modifier.width(4.dp).height(if (note != null) 78.dp else 58.dp),
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lesson.timeRange.ifBlank { "${lesson.number} пара" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isNow) color else neon.textSecondary.copy(alpha = dim),
                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (lesson.type.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        TypeChip(lesson.type, color.copy(alpha = dim))
                    }
                    if (isNow) {
                        Spacer(Modifier.width(8.dp))
                        NowPulse(color)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = lesson.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = neon.textPrimary.copy(alpha = dim),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lesson.room.isNotBlank()) {
                        MetaItem(Icons.Rounded.Place, lesson.room, dim)
                        Spacer(Modifier.width(12.dp))
                    }
                    if (lesson.teacher.isNotBlank()) {
                        MetaItem(Icons.Rounded.Person, lesson.teacher, dim)
                    }
                }
                if (!note.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    NoteStrip(note, color)
                }
            }
        }
    }
}

@Composable
private fun TypeChip(type: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(type, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun NowPulse(color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text("СЕЙЧАС", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, dim: Float) {
    val neon = LocalNeon.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(13.dp), tint = neon.textMuted.copy(alpha = dim))
        Spacer(Modifier.width(4.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium,
            color = neon.textSecondary.copy(alpha = dim),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoteStrip(note: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(Icons.Rounded.Notes, null, Modifier.size(13.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(
            note, style = MaterialTheme.typography.bodyMedium,
            color = LocalNeon.current.textSecondary,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Заглушка для дня без пар. */
@Composable
fun EmptyDayCard(text: String = "Пар нет", modifier: Modifier = Modifier) {
    val neon = LocalNeon.current
    GlassCard(modifier.fillMaxWidth(), glowScale = 0.4f) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✦", style = MaterialTheme.typography.headlineMedium, color = neon.accent.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = neon.textSecondary)
        }
    }
}
