package ru.uust.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.data.repo.SubjectSummary
import ru.uust.schedule.ui.ScheduleViewModel
import ru.uust.schedule.ui.components.GlassCard
import ru.uust.schedule.ui.theme.LocalNeon
import ru.uust.schedule.ui.theme.NeonPalette

/**
 * Вкладка «Предметы».
 *
 * Список собирается из уже загруженного расписания, поэтому вручную заводить
 * предметы не нужно — добавляется только то, чего на сайте нет: заметка и цвет.
 */
@Composable
fun SubjectsScreen(vm: ScheduleViewModel) {
    val neon = LocalNeon.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notes by vm.notesFlow().collectAsState(initial = emptyMap())

    var subjects by remember { mutableStateOf<List<SubjectSummary>>(emptyList()) }
    var editing by remember { mutableStateOf<SubjectSummary?>(null) }

    LaunchedEffect(settings.groupId, notes.size) {
        vm.loadSubjects { subjects = it }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 56.dp, bottom = 10.dp)) {
            Text(
                "Предметы",
                style = MaterialTheme.typography.displaySmall,
                color = neon.textPrimary,
            )
            Text(
                if (subjects.isEmpty()) settings.groupName
                else "${subjects.size} предметов · ${settings.groupName}",
                style = MaterialTheme.typography.bodyLarge,
                color = neon.textMuted,
            )
        }

        if (subjects.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Загрузите расписание,\nи предметы появятся здесь",
                    style = MaterialTheme.typography.bodyLarge,
                    color = neon.textMuted,
                )
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp, end = 18.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(subjects, key = { it.subject }) { summary ->
                    SubjectCard(
                        summary = summary,
                        note = notes[summary.subject],
                        onEdit = { editing = summary },
                    )
                }
            }
        }
    }

    editing?.let { summary ->
        NoteDialog(
            summary = summary,
            existing = notes[summary.subject],
            onDismiss = { editing = null },
            onSave = { text, hue ->
                vm.saveNote(summary.subject, text, hue)
                editing = null
            },
        )
    }
}

@Composable
private fun SubjectCard(
    summary: SubjectSummary,
    note: SubjectNoteEntity?,
    onEdit: () -> Unit,
) {
    val neon = LocalNeon.current
    val color = NeonPalette.subjectColor(summary.subject, neon, note?.hue ?: -1)

    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        corner = 18.dp,
        accent = color,
        glowScale = 0.5f,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Box(
                Modifier
                    .size(width = 4.dp, height = if (note?.note.isNullOrBlank()) 46.dp else 70.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    summary.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = neon.textPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(summary.teachers.take(2).joinToString(", "))
                        if (summary.types.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(summary.types.joinToString("/"))
                        }
                    }.ifBlank { "${summary.lessonCount} занятий" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = neon.textMuted,
                    maxLines = 2,
                )

                if (!note?.note.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            note!!.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = neon.textSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f))
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Edit, "Заметка", tint = color, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NoteDialog(
    summary: SubjectSummary,
    existing: SubjectNoteEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    val neon = LocalNeon.current
    var text by remember { mutableStateOf(existing?.note.orEmpty()) }
    var hue by remember { mutableStateOf(existing?.hue ?: -1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = neon.background,
        titleContentColor = neon.textPrimary,
        textContentColor = neon.textSecondary,
        title = { Text(summary.subject, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (summary.rooms.isNotEmpty()) {
                    Text(
                        "Аудитории: " + summary.rooms.take(3).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = neon.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Что принести, дедлайны, кабинет…", color = neon.textMuted) },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = neon.accent,
                        unfocusedBorderColor = neon.stroke,
                        focusedTextColor = neon.textPrimary,
                        unfocusedTextColor = neon.textPrimary,
                        cursorColor = neon.accent,
                    ),
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    "Цвет метки",
                    style = MaterialTheme.typography.labelLarge,
                    color = neon.textMuted,
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HueDot(
                        color = NeonPalette.subjectColor(summary.subject, neon, -1),
                        selected = hue < 0,
                        onClick = { hue = -1 },
                    )
                    listOf(0, 30, 60, 100, 140, 180, 200, 240, 280, 310, 340).forEach { h ->
                        HueDot(
                            color = NeonPalette.hsv(h.toFloat(), 0.8f, 1f),
                            selected = hue == h,
                            onClick = { hue = h },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim(), hue) }) {
                Text("Сохранить", color = neon.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = neon.textMuted) }
        },
    )
}

@Composable
private fun HueDot(
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (selected) 1f else 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check, null,
                tint = androidx.compose.ui.graphics.Color.Black,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
