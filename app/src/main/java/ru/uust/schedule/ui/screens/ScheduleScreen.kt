package ru.uust.schedule.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import ru.uust.schedule.data.update.UpdateManager
import ru.uust.schedule.data.update.UpdateState
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.ui.ScheduleViewModel
import ru.uust.schedule.ui.components.EmptyDayCard
import ru.uust.schedule.ui.components.LessonCard
import ru.uust.schedule.ui.components.UpdateBanner
import ru.uust.schedule.ui.components.quietClickable
import ru.uust.schedule.ui.theme.LocalPalette
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/**
 * Главный экран: один день за раз.
 *
 * Кнопок навигации и обновления здесь нет намеренно. Дни листаются свайпом
 * и полосой недели, а расписание подтягивается само — при открытии экрана
 * и фоновой задачей. Кнопка «обновить» означала бы, что приложению нельзя
 * доверять без ручного вмешательства.
 */
@Composable
fun ScheduleScreen(vm: ScheduleViewModel) {
    val palette = LocalPalette.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val today = LocalDate.now()
    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag) > 70f) vm.shiftDay(if (drag < 0) 1 else -1)
                        drag = 0f
                    },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            }
    ) {
        Header(date = date, today = today, groupName = settings.groupName, syncing = ui.loading)

        WeekStrip(date = date, today = today, onPick = vm::selectDate)

        UpdateBanner(
            state = updateState,
            onInstall = {
                val release = (updateState as? UpdateState.Available)?.release ?: return@UpdateBanner
                if (UpdateManager.canInstallPackages(context)) {
                    vm.installUpdate(release)
                } else {
                    context.startActivity(UpdateManager.unknownSourcesSettingsIntent(context))
                }
            },
            onDismiss = vm::dismissUpdate,
            modifier = Modifier.padding(top = 6.dp),
        )

        ui.error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.danger.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .quietClickable { vm.refresh(force = true) }
            ) {
                Text(
                    "$message · нажмите, чтобы повторить",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.danger,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        AnimatedContent(
            targetState = date,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it / 4 * dir } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 4 * dir } + fadeOut())
                    .using(SizeTransform(clip = false))
            },
            label = "day",
        ) { shownDate ->
            val lessons = ui.day
                ?.takeIf { it.isoDate == shownDate.toString() }
                ?.realLessons
                .orEmpty()

            if (lessons.isEmpty()) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    EmptyDayCard(if (ui.day == null && ui.loading) "Загружаем…" else "Пар нет")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(lessons, key = { it.number }) { lesson ->
                        val note = ui.notes[lesson.subject]
                        LessonCard(
                            lesson = lesson,
                            isNow = shownDate == today && lesson.startMin >= 0 &&
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

/**
 * Заголовок: крупно — какой это день относительно сегодня, мелко — дата и группа.
 * Индикатор синхронизации появляется, только когда она реально идёт.
 */
@Composable
private fun Header(
    date: LocalDate,
    today: LocalDate,
    groupName: String,
    syncing: Boolean,
) {
    val palette = LocalPalette.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 58.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = DayLogic.fullDay(date),
                style = MaterialTheme.typography.labelMedium,
                color = palette.accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = DayLogic.title(date, today),
                style = MaterialTheme.typography.displayMedium,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${DayLogic.formatDate(date)} · $groupName",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
        }

        if (syncing) {
            CircularProgressIndicator(
                color = palette.textMuted,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Полоса недели — она же навигация: заменяет стрелки и показывает, где ты находишься. */
@Composable
private fun WeekStrip(date: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val palette = LocalPalette.current
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        (0..5).forEach { i ->
            val d = monday.plusDays(i.toLong())
            val selected = d == date
            val isToday = d == today

            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) palette.accent else palette.surface)
                    .quietClickable { onPick(d) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        DayLogic.shortDay(d),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) palette.onAccent.copy(alpha = 0.7f)
                        else palette.textMuted,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            selected -> palette.onAccent
                            isToday -> palette.accent
                            else -> palette.textSecondary
                        },
                        fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
