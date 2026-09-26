package ru.uust.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.data.repo.SubjectSummary
import ru.uust.schedule.ui.ScheduleViewModel
import ru.uust.schedule.ui.components.Card
import ru.uust.schedule.ui.components.SwipeRevealRow
import ru.uust.schedule.ui.components.quietClickable
import ru.uust.schedule.ui.theme.LocalPalette
import ru.uust.schedule.ui.theme.Palette

/**
 * Вкладка «Предметы».
 *
 * Список собирается из расписания автоматически; вручную добавляют то, чего на
 * сайте нет — факультатив, курсы, секцию.
 *
 * Свайп влево открывает «изменить» и «удалить». Заведённый вручную предмет
 * удаляется совсем, пришедший с сайта прячется: удалить его по-настоящему
 * нельзя, он вернётся с ближайшей синхронизацией.
 */
@Composable
fun SubjectsScreen(vm: ScheduleViewModel) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notes by vm.notesFlow().collectAsState(initial = emptyMap())

    var subjects by remember { mutableStateOf<List<SubjectSummary>>(emptyList()) }
    var editing by remember { mutableStateOf<SubjectSummary?>(null) }
    var deleting by remember { mutableStateOf<SubjectSummary?>(null) }
    var adding by remember { mutableStateOf(false) }
    var homeworkTarget by remember {
        mutableStateOf<Triple<java.time.LocalDate, ru.uust.schedule.domain.Lesson,
            ru.uust.schedule.data.local.LessonRecordEntity?>?>(null)
    }
    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(settings.groupId, notes.size, reloadToken) {
        vm.loadSubjects { subjects = it }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 58.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Предметы",
                        style = MaterialTheme.typography.displayMedium,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (subjects.isEmpty()) settings.groupName
                        else "${subjects.size} предметов · ${settings.groupName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                    )
                }

                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                        .quietClickable { adding = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Add, "Добавить предмет",
                        tint = palette.onAccent, modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (subjects.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Пока пусто — расписание подтянет предметы само",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textMuted,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(subjects, key = { it.subject }) { summary ->
                        SubjectCard(
                            summary = summary,
                            note = notes[summary.subject],
                            onEdit = { editing = summary },
                            onDelete = { deleting = summary },
                        )
                    }
                }
            }
        }
    }

    editing?.let { summary ->
        NoteDialog(
            summary = summary,
            existing = notes[summary.subject],
            onDismiss = { editing = null },
            onAddHomework = {
                editing = null
                vm.openSubjectHomework(summary.subject) { date, lesson, record ->
                    homeworkTarget = Triple(date, lesson, record)
                }
            },
            onSave = { text, hue, teacher ->
                vm.saveNote(
                    subject = summary.subject,
                    text = text,
                    hue = hue,
                    custom = summary.custom,
                    teacherFull = teacher,
                )
                editing = null
            },
        )
    }

    homeworkTarget?.let { (date, lesson, record) ->
        val ui by vm.ui.collectAsStateWithLifecycle()
        val today = java.time.LocalDate.now()
        var upcoming by remember(date, lesson.subject) {
            mutableStateOf<List<java.time.LocalDate>>(emptyList())
        }
        LaunchedEffect(date, lesson.subject, lesson.number) {
            vm.loadAttachments(date, lesson.subject, lesson.number)
            // Задание с прошедшей пары сдают к следующей паре начиная с сегодня,
            // а не к той, что шла сразу после неё и уже прошла.
            val after = if (date < today) today.minusDays(1) else date
            vm.loadUpcomingLessonDates(lesson.subject, after) { upcoming = it }
        }
        ru.uust.schedule.ui.components.LessonSheet(
            lesson = lesson,
            date = date,
            today = today,
            record = record,
            attachments = ui.attachments,
            upcomingLessonDates = upcoming,
            onDismiss = { homeworkTarget = null },
            onAttach = { uri, name ->
                vm.attachFile(date, lesson.subject, lesson.number, uri, name)
            },
            onDetach = { uri -> vm.detachFile(date, lesson.subject, lesson.number, uri) },
            onSave = { homework, done, grade, dueDate ->
                vm.saveRecord(date, lesson.subject, lesson.number, homework, done, grade, dueDate)
                homeworkTarget = null
            },
        )
    }

    if (adding) {
        AddSubjectDialog(
            onDismiss = { adding = false },
            onAdd = { name ->
                vm.addSubject(name) { reloadToken++ }
                adding = false
            },
        )
    }

    deleting?.let { summary ->
        val palette2 = LocalPalette.current
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = palette2.surfaceHigh,
            titleContentColor = palette2.textPrimary,
            textContentColor = palette2.textSecondary,
            title = { Text("Удалить «${summary.subject}»?") },
            text = {
                Text(
                    if (summary.custom) "Предмет и заметка к нему исчезнут. Отменить будет нельзя."
                    // Пара останется в расписании: оно приходит с сайта и нам не принадлежит.
                    else "Предмет пропадёт из списка вместе с заметкой. В расписании " +
                        "пары по нему останутся."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSubject(summary.subject) { reloadToken++ }
                    deleting = null
                }) {
                    Text("Удалить", color = palette2.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Отмена", color = palette2.textMuted)
                }
            },
        )
    }
}

@Composable
private fun SubjectCard(
    summary: SubjectSummary,
    note: SubjectNoteEntity?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalPalette.current
    val color = Palette.subjectColor(summary.subject, palette, note?.hue ?: -1)

    SwipeRevealRow(
        actions = {
            ActionCircle(Icons.Rounded.Edit, "Изменить", palette.accent, onEdit)
            ActionCircle(Icons.Rounded.DeleteOutline, "Удалить", palette.danger, onDelete)
        },
    ) {
        Card(Modifier.fillMaxWidth(), onClick = onEdit) {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 20.dp)) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(if (note?.note.isNullOrBlank()) 46.dp else 74.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        summary.subject,
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitleOf(summary, note),
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textMuted,
                        maxLines = 2,
                    )

                    if (!note?.note.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(color.copy(alpha = 0.10f))
                                .padding(horizontal = 13.dp, vertical = 10.dp)
                        ) {
                            Text(
                                note!!.note,
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Подпись под названием: полное имя преподавателя важнее инициалов с сайта. */
private fun subtitleOf(summary: SubjectSummary, note: SubjectNoteEntity?): String {
    note?.teacherFull?.takeIf { it.isNotBlank() }?.let { return it }
    if (summary.custom) return "Добавлен вручную"
    return buildString {
        append(summary.teachers.take(2).joinToString(", "))
        if (summary.types.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(summary.types.joinToString("/"))
        }
    }.ifBlank { "${summary.lessonCount} занятий" }
}

@Composable
private fun ActionCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(palette.surfaceHigh)
            .quietClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}


@Composable
private fun AddSubjectDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val palette = LocalPalette.current
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceHigh,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = { Text("Новый предмет") },
        text = {
            Column {
                Text(
                    "Для того, чего нет в расписании на сайте",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Например, Автошкола", color = palette.textMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = textFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(
                    "Добавить",
                    color = if (name.isNotBlank()) palette.accent else palette.textMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = palette.textMuted) }
        },
    )
}

@Composable
private fun NoteDialog(
    summary: SubjectSummary,
    existing: SubjectNoteEntity?,
    onDismiss: () -> Unit,
    onAddHomework: () -> Unit,
    onSave: (String, Int, String) -> Unit,
) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf(existing?.note.orEmpty()) }
    var hue by remember { mutableStateOf(existing?.hue ?: -1) }
    var teacher by remember { mutableStateOf(existing?.teacherFull.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceHigh,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = { Text(summary.subject, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(palette.tint(0.16f))
                        .quietClickable(onAddHomework)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "+ Добавить домашнее задание",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(14.dp))

                if (summary.rooms.isNotEmpty()) {
                    Text(
                        "Аудитории: " + summary.rooms.take(3).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // Сайт отдаёт только «Иванов И.И.» — полное имя вводится вручную.
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Преподаватель", color = palette.textMuted) },
                    placeholder = {
                        Text(
                            summary.teachers.firstOrNull() ?: "Иванов Иван Иванович",
                            color = palette.textMuted,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = textFieldColors(),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Короткая заметка", color = palette.textMuted) },
                    placeholder = { Text("Показывается под парой", color = palette.textMuted) },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = textFieldColors(),
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "Цвет метки",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textMuted,
                )
                Spacer(Modifier.height(9.dp))

                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    HueDot(
                        color = Palette.subjectColor(summary.subject, palette, -1),
                        selected = hue < 0,
                        onClick = { hue = -1 },
                    )
                    listOf(174, 200, 232, 262, 300, 340, 18, 38, 96, 145).forEach { h ->
                        HueDot(
                            color = Palette.subjectColor(summary.subject, palette, h),
                            selected = hue == h,
                            onClick = { hue = h },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim(), hue, teacher.trim()) }) {
                Text("Сохранить", color = palette.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = palette.textMuted) }
        },
    )
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalPalette.current.accent,
    unfocusedBorderColor = LocalPalette.current.divider,
    focusedTextColor = LocalPalette.current.textPrimary,
    unfocusedTextColor = LocalPalette.current.textPrimary,
    cursorColor = LocalPalette.current.accent,
)

@Composable
private fun HueDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (selected) 1f else 0.45f))
            .quietClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check, null,
                tint = Color.White, modifier = Modifier.size(17.dp),
            )
        }
    }
}
