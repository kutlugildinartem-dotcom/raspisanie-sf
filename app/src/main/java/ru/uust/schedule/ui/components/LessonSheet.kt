package ru.uust.schedule.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.uust.schedule.data.local.AttachmentEntity
import ru.uust.schedule.data.local.LessonRecordEntity
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.Lesson
import ru.uust.schedule.ui.theme.LocalPalette
import java.time.LocalDate

/** Окно пары: домашка, срок, файлы и оценка. */
@Composable
fun LessonSheet(
    lesson: Lesson,
    date: LocalDate,
    today: LocalDate,
    record: LessonRecordEntity?,
    attachments: List<AttachmentEntity>,
    /** Даты 1–2 ближайших пар по этому предмету — для «к следующей»/«через пару» с числом дней. */
    upcomingLessonDates: List<LocalDate>,
    onDismiss: () -> Unit,
    onAttach: (uri: String, name: String) -> Unit,
    onDetach: (uri: String) -> Unit,
    onSave: (homework: String, done: Boolean, grade: Int, dueDate: String) -> Unit,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current

    var homework by remember { mutableStateOf(record?.homework.orEmpty()) }
    var done by remember { mutableStateOf(record?.homeworkDone ?: false) }
    var grade by remember { mutableStateOf(record?.grade ?: 0) }
    var due by remember { mutableStateOf(record?.dueDate.orEmpty()) }
    // Уже сохранённый срок не трогаем; для новой записи подставляем дату
    // следующей пары, как только она подгрузится из кеша.
    androidx.compose.runtime.LaunchedEffect(upcomingLessonDates) {
        if (record?.dueDate.isNullOrBlank() && due.isBlank()) {
            upcomingLessonDates.firstOrNull()?.let { due = it.toString() }
        }
    }

    val isPastDay = date <= today

    // Постоянное разрешение обязательно: без него ссылка на файл протухнет
    // после перезапуска телефона, и вложение станет битым.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onAttach(uri.toString(), fileName(context, uri))
        }
    }

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
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = homework,
                    onValueChange = { homework = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Домашнее задание", color = palette.textMuted) },
                    placeholder = { Text("Что задали", color = palette.textMuted) },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = textFieldColors(),
                )

                // Срок и файлы видны сразу, ещё до того, как набран текст задания.
                Spacer(Modifier.height(12.dp))
                DueDatePicker(
                    due = due,
                    today = today,
                    upcomingLessonDates = upcomingLessonDates,
                    onPick = { due = it },
                )

                Spacer(Modifier.height(14.dp))
                AttachmentsSection(
                    attachments = attachments,
                    onAdd = { picker.launch(arrayOf("*/*")) },
                    onOpen = { uri -> openFile(context, uri) },
                    onRemove = onDetach,
                )

                if (homework.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    DoneRow(done) { done = !done }
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
            TextButton(onClick = { onSave(homework.trim(), done, grade, due) }) {
                Text("Сохранить", color = palette.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = palette.textMuted) }
        },
    )
}

/**
 * Срок сдачи. Первые два варианта — не абстрактные, а с конкретной датой
 * пары по этому предмету, как только становится известно, когда она:
 * «к следующей паре» без даты ничего не говорит о том, сколько дней есть
 * на самом деле, а именно это интересует в первую очередь.
 */
@Composable
private fun DueDatePicker(
    due: String,
    today: LocalDate,
    upcomingLessonDates: List<LocalDate>,
    onPick: (String) -> Unit,
) {
    val palette = LocalPalette.current
    val options = remember(upcomingLessonDates) {
        buildList {
            upcomingLessonDates.getOrNull(0)?.let {
                add(it.toString() to "К следующей паре · ${daysHint(it, today)}")
            }
            upcomingLessonDates.getOrNull(1)?.let {
                add(it.toString() to "Через пару · ${daysHint(it, today)}")
            }
            // Пока расписание для этого предмета не подгружено — сработает,
            // даты появятся сами при следующей синхронизации.
            if (upcomingLessonDates.isEmpty()) add("" to "К следующей паре")
        }
    }

    Column {
        Text("Сдать к паре", style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                Chip(text = label, selected = due == value, onClick = { onPick(value) })
            }
        }
    }
}

/** «сегодня» / «завтра» / «через N дней» — то самое число дней, ради которого выбирают срок. */
private fun daysHint(date: LocalDate, today: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when (days) {
        0L -> "сегодня"
        1L -> "завтра"
        else -> {
            val mod = days % 100
            val word = when {
                mod in 11..19 -> "дней"
                mod % 10 == 1L -> "день"
                mod % 10 in 2..4 -> "дня"
                else -> "дней"
            }
            "через $days $word"
        }
    }
}

@Composable
private fun DoneRow(done: Boolean, onToggle: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (done) palette.tint(0.14f) else palette.surface)
            .quietClickable(onToggle)
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

@Composable
internal fun AttachmentsSection(
    attachments: List<AttachmentEntity>,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val palette = LocalPalette.current

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Файлы", style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(palette.tint(0.14f))
                    .quietClickable(onAdd)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.AttachFile, null,
                    tint = palette.accent, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "Прикрепить",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent,
                )
            }
        }

        attachments.forEach { file ->
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(palette.surface)
                    .quietClickable { onOpen(file.uri) }
                    .padding(start = 11.dp, end = 5.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.AttachFile, null,
                    tint = palette.textMuted, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .quietClickable { onRemove(file.uri) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close, "Открепить",
                        tint = palette.textMuted, modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
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

/** Человекочитаемое имя файла из content-URI; если провайдер его не отдал — хвост пути. */
internal fun fileName(context: android.content.Context, uri: android.net.Uri): String {
    val fromProvider = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
    return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Файл"
}

/**
 * Раньше при отсутствии приложения для этого типа файла [Intent.ACTION_VIEW]
 * падал с ActivityNotFoundException, которую runCatching молча проглатывал —
 * пользователь нажимал на файл, и ничего не происходило без единой подсказки.
 */
internal fun openFile(context: android.content.Context, uri: String) {
    val parsed = android.net.Uri.parse(uri)
    val type = context.contentResolver.getType(parsed)?.takeIf { it.isNotBlank() } ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(parsed, type)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) {
        android.widget.Toast.makeText(
            context,
            "На телефоне нет приложения, чтобы открыть этот файл",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }
}
