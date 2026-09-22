package ru.uust.schedule.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.uust.schedule.ui.theme.LocalPalette
import java.time.LocalDate
import java.time.YearMonth

/**
 * Месяц в стиле системного календаря: скруглённые квадратные клетки
 * одного цвета, выбранный день — тонкое кольцо, а не заливка.
 *
 * Загруженность дня показывают точки под числом (1 точка на пару, максимум
 * три плюс «+N»), а не оттенок клетки: ровный фон клеток читается спокойнее
 * и ближе к тому, как выглядят системные календари.
 *
 * Дни, которых нет в кеше, точек не получают: «нет данных» и «пар нет»
 * — разные вещи, метить их одинаково нельзя.
 */
@Composable
fun CalendarSheet(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    counts: Map<LocalDate, Int>,
    onPick: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.surface)
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthArrow("‹", "Предыдущий месяц") { onMonthChange(month.minusMonths(1)) }
            Text(
                monthName(month),
                style = MaterialTheme.typography.headlineSmall,
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            MonthArrow("›", "Следующий месяц") { onMonthChange(month.plusMonths(1)) }
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth()) {
            listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс").forEach { name ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        val first = month.atDay(1)
        // Неделя начинается с понедельника, поэтому сетку смещаем на день недели первого числа.
        val leading = first.dayOfWeek.value - 1
        val weeks = (leading + month.lengthOfMonth() + 6) / 7

        repeat(weeks) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { dow ->
                    val index = week * 7 + dow - leading
                    if (index < 0 || index >= month.lengthOfMonth()) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(index + 1)
                        DayCell(
                            date = date,
                            count = counts[date],
                            isSelected = date == selected,
                            isToday = date == today,
                            modifier = Modifier.weight(1f),
                            onClick = { onPick(date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    count: Int?,
    isSelected: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)

    val ring by animateColorAsState(
        if (isSelected || isToday) palette.accent else palette.accent.copy(alpha = 0f),
        label = "ring",
    )

    Box(modifier.aspectRatio(1f).padding(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(palette.surfaceHigh)
                .border(if (isSelected) 2.dp else 1.5.dp, ring, shape)
                .quietClickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected || isToday) palette.accent else palette.textPrimary,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                )
                if ((count ?: 0) > 0) {
                    Spacer(Modifier.height(3.dp))
                    LoadDots(count!!, palette.accent)
                }
            }
        }
    }
}

/** До трёх точек по числу пар, дальше — «+N», чтобы клетка не раздувалась. */
@Composable
private fun LoadDots(count: Int, accent: androidx.compose.ui.graphics.Color) {
    val palette = LocalPalette.current
    if (count > 4) {
        Text(
            "+$count",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(count.coerceAtMost(4)) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }
}

private fun monthName(month: YearMonth): String =
    "${NOMINATIVE[month.monthValue - 1]} ${month.year}"

/** В DayLogic месяцы стоят в родительном падеже («21 сентября»), заголовку нужен именительный. */
private val NOMINATIVE = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

@Composable
private fun MonthArrow(glyph: String, description: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(palette.surfaceHigh)
            .quietClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.headlineSmall,
            color = palette.accent,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Полоска-ручка над выезжающей панелью — привычный признак «можно тянуть». */
@Composable
fun DragHandle(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(38.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.divider)
        )
    }
}
