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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.uust.schedule.data.local.LessonRecordEntity
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.Lesson
import ru.uust.schedule.ui.theme.LocalPalette
import java.time.LocalDate

/**
 * Окно пары: домашка и оценка.
 *
 * Оценка предлагается только для прошедших дней — ставить её наперёд
 * бессмысленно, а лишний выбор на экране мешает.
 */
@Composable
fun LessonSheet(
    lesson: Lesson,
    date: LocalDate,
    today: LocalDate,
    record: LessonRecordEntity?,
    onDismiss: () -> Unit,
    onSave: (homework: String, done: Boolean, grade: Int) -> Unit,
) {
    val palette = LocalPalette.current
    var homework by remember { mutableStateOf(record?.homework.orEmpty()) }
    var done by remember { mutableStateOf(record?.homeworkDone ?: false) }
    var grade by remember { mutableStateOf(record?.grade ?: 0) }

    val isPastDay = date <= today

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceHigh,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = {
            Column {
                Text(lesson.subject, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(
                        DayLogic.title(date, today),
                        lesson.timeRange.ifBlank { null },
                        lesson.room.ifBlank { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = homework,
                    onValueChange = { homework = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Домашнее задание", color = palette.textMuted) },
                    placeholder = { Text("Что задали", color = palette.textMuted) },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.divider,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        cursorColor = palette.accent,
                    ),
                )

                if (homework.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (done) palette.tint(0.14f) else palette.surface)
                            .quietClickable { done = !done }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .then(
                                    if (done) Modifier.background(palette.accent)
                                    else Modifier.border(2.dp, palette.divider, CircleShape)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (done) {
                                Text(
                                    "✓",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.onAccent,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Text(
                            if (done) "Сделано" else "Отметить выполненным",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (done) palette.accent else palette.textSecondary,
                        )
                    }
                }

                if (isPastDay) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Оценка",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textMuted,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradeOption("—", grade == 0) { grade = 0 }
                        (2..5).forEach { value ->
                            GradeOption(value.toString(), grade == value) { grade = value }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(homework.trim(), done, grade) }) {
                Text("Сохранить", color = palette.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = palette.textMuted) }
        },
    )
}

@Composable
private fun GradeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accent else palette.surface)
            .quietClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) palette.onAccent else palette.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
