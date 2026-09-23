package ru.uust.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.HomeworkBucket
import ru.uust.schedule.domain.HomeworkItem
import ru.uust.schedule.ui.ScheduleViewModel
import ru.uust.schedule.ui.components.Card
import ru.uust.schedule.ui.components.quietClickable
import ru.uust.schedule.ui.theme.LocalPalette
import ru.uust.schedule.ui.theme.Palette
import java.time.LocalDate

/**
 * Вкладка «Задания»: всё заданное, сгруппированное по сроку.
 *
 * Группы идут по срочности, а не по предметам: когда садишься делать домашку,
 * первый вопрос — что горит, а не по какому оно предмету.
 */
@Composable
fun HomeworkScreen(vm: ScheduleViewModel) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()

    var items by remember { mutableStateOf<List<HomeworkItem>>(emptyList()) }
    var reloadToken by remember { mutableStateOf(0) }
    val today = LocalDate.now()

    LaunchedEffect(settings.groupId, reloadToken, ui.records) {
        vm.loadHomework { items = it }
    }

    val pending = items.count { !it.done }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 58.dp, bottom = 14.dp)) {
            Text(
                "Задания",
                style = MaterialTheme.typography.displayMedium,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                when {
                    items.isEmpty() -> settings.groupName
                    pending == 0 -> "Всё сделано"
                    else -> "$pending не сделано · ${settings.groupName}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Заданий пока нет.\nНажмите на пару в расписании, чтобы записать домашку.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textMuted,
                )
            }
            return@Column
        }

        val grouped = items.groupBy { it.bucket(today) }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeworkBucket.entries.forEach { bucket ->
                val bucketItems = grouped[bucket].orEmpty()
                if (bucketItems.isEmpty()) return@forEach

                item(key = "h-${bucket.name}") {
                    BucketHeader(bucket, bucketItems.size, palette)
                }
                items(
                    bucketItems,
                    key = { "${it.lessonDate}-${it.subject}-${it.lessonNumber}" },
                ) { homework ->
                    HomeworkCard(
                        item = homework,
                        today = today,
                        onToggle = {
                            vm.toggleHomeworkDone(homework) { reloadToken++ }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BucketHeader(bucket: HomeworkBucket, count: Int, palette: Palette) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            bucket.title,
            style = MaterialTheme.typography.headlineSmall,
            color = if (bucket == HomeworkBucket.Overdue) palette.danger else palette.textPrimary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textMuted,
        )
    }
}

@Composable
private fun HomeworkCard(item: HomeworkItem, today: LocalDate, onToggle: () -> Unit) {
    val palette = LocalPalette.current
    val color = Palette.subjectColor(item.subject, palette)
    val overdue = !item.done && item.due < today

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Галочка слева: отметить сделанным — самое частое действие в этом списке.
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(
                        if (item.done) Modifier.background(palette.accent)
                        else Modifier.border(2.dp, palette.divider, CircleShape)
                    )
                    .quietClickable(onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (item.done) {
                    Icon(
                        Icons.Rounded.Check, "Сделано",
                        tint = palette.onAccent, modifier = Modifier.size(15.dp),
                    )
                }
            }

            Spacer(Modifier.width(13.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 3.dp, height = 13.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        item.subject,
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.attachments > 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.AttachFile, "Есть файлы",
                            tint = palette.textMuted, modifier = Modifier.size(13.dp),
                        )
                        Text(
                            item.attachments.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted,
                        )
                    }
                }

                Spacer(Modifier.height(5.dp))
                Text(
                    item.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.done) palette.textMuted else palette.textPrimary,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    "Задано ${DayLogic.formatDate(item.lessonDate)} · срок ${item.dueLabel(today)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) palette.danger else palette.textMuted,
                )
            }
        }
    }
}
