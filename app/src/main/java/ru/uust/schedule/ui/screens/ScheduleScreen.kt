package ru.uust.schedule.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.ui.ScheduleViewModel
import ru.uust.schedule.data.update.UpdateManager
import ru.uust.schedule.data.update.UpdateState
import ru.uust.schedule.ui.components.EmptyDayCard
import ru.uust.schedule.ui.components.LessonCard
import ru.uust.schedule.ui.components.UpdateBanner
import ru.uust.schedule.ui.theme.LocalNeon
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/**
 * Основной экран: один день за раз.
 *
 * Дни листаются свайпом и стрелками — той же логикой, что и в виджете,
 * чтобы поведение не расходилось.
 */
@Composable
fun ScheduleScreen(vm: ScheduleViewModel) {
    val neon = LocalNeon.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag) > 80f) vm.shiftDay(if (drag < 0) 1 else -1)
                        drag = 0f
                    },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            }
    ) {
        ScheduleHeader(
            vm = vm,
            date = date,
            today = today,
            groupName = settings.groupName,
            loading = ui.loading,
        )

        WeekStrip(date = date, today = today, onPick = vm::selectDate)

        val updateState by vm.updateState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        UpdateBanner(
            state = updateState,
            onInstall = {
                val release = (updateState as? UpdateState.Available)?.release ?: return@UpdateBanner
                // На Android 8+ без этого разрешения установка молча провалится,
                // поэтому сначала отправляем пользователя его выдать.
                if (UpdateManager.canInstallPackages(context)) {
                    vm.installUpdate(release)
                } else {
                    context.startActivity(UpdateManager.unknownSourcesSettingsIntent(context))
                }
            },
            onDismiss = vm::dismissUpdate,
            modifier = Modifier.padding(top = 4.dp),
        )

        ui.error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(neon.danger.copy(alpha = 0.14f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = neon.danger,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        AnimatedContent(
            targetState = date,
            transitionSpec = {
                val forward = targetState > initialState
                val offset = if (forward) 1 else -1
                (slideInHorizontally { it / 3 * offset } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 3 * offset } + fadeOut())
                    .using(SizeTransform(clip = false))
            },
            label = "day",
        ) { shownDate ->
            val lessons = ui.day
                ?.takeIf { it.isoDate == shownDate.toString() }
                ?.realLessons
                .orEmpty()

            if (lessons.isEmpty()) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    EmptyDayCard(
                        if (ui.day == null) "Нет данных — потяните обновить" else "Пар нет",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 18.dp, end = 18.dp, bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(lessons, key = { it.number }) { lesson ->
                        val note = ui.notes[lesson.subject]
                        LessonCard(
                            lesson = lesson,
                            isNow = shownDate == today &&
                                lesson.startMin >= 0 &&
                                nowMinutes >= lesson.startMin && nowMinutes < lesson.endMin,
                            isPast = shownDate < today ||
                                (shownDate == today && lesson.endMin in 0..nowMinutes),
                            note = note?.note?.takeIf { it.isNotBlank() },
                            noteHue = note?.hue ?: -1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleHeader(
    vm: ScheduleViewModel,
    date: LocalDate,
    today: LocalDate,
    groupName: String,
    loading: Boolean,
) {
    val neon = LocalNeon.current
    val relative = DayLogic.relativeLabel(date, today)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 12.dp, top = 56.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = relative ?: DayLogic.shortDay(date),
                style = MaterialTheme.typography.displaySmall,
                color = neon.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DayLogic.formatDate(date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = neon.accent,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "  ·  $groupName",
                    style = MaterialTheme.typography.bodyLarge,
                    color = neon.textMuted,
                )
            }
        }

        if (relative == null) {
            IconBubble(Icons.Rounded.Today, "Сегодня") { vm.jumpToDefault() }
            Spacer(Modifier.width(6.dp))
        }

        if (loading) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = neon.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            IconBubble(Icons.Rounded.Refresh, "Обновить") { vm.refresh(force = true) }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBubble(Icons.Rounded.ChevronLeft, "Предыдущий день") { vm.shiftDay(-1) }
        Spacer(Modifier.weight(1f))
        IconBubble(Icons.Rounded.ChevronRight, "Следующий день") { vm.shiftDay(1) }
    }
}

@Composable
private fun IconBubble(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val neon = LocalNeon.current
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(neon.glass)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = neon.accent, modifier = Modifier.size(20.dp))
    }
}

/** Полоса дней недели: быстрый прыжок на любой день без листания. */
@Composable
private fun WeekStrip(date: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val neon = LocalNeon.current
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0..5).forEach { i ->
            val d = monday.plusDays(i.toLong())
            val selected = d == date
            val isToday = d == today
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        when {
                            selected -> neon.accent.copy(alpha = 0.22f)
                            isToday -> neon.glass
                            else -> neon.glass.copy(alpha = neon.glass.alpha * 0.5f)
                        }
                    )
                    .clickable { onPick(d) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        DayLogic.shortDay(d),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) neon.accent else neon.textMuted,
                    )
                    Text(
                        d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            selected -> neon.accent
                            isToday -> neon.textPrimary
                            else -> neon.textSecondary
                        },
                        fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
