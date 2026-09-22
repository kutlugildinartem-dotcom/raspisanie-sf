package ru.uust.schedule.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.uust.schedule.ui.theme.LocalPalette
import java.time.LocalDate
import java.time.YearMonth

/**
 * Месяц в духе системного календаря: круглые клетки, минимум линий,
 * выбранный день — залитый кружок.
 *
 * Заливка клетки показывает загруженность: свободный день прозрачный,
 * полный залит акцентом целиком. Так месяц читается одним взглядом, без цифр.
 *
 * Дни, которых нет в кеше, намеренно не красятся: «нет данных» и «нет пар»
 * это разные вещи, и одинаковый вид вводил бы в заблуждение.
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
            .clip(RoundedCornerShape(24.dp))
            .background(palette.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    monthName(month),
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.textPrimary,
                )
                Text(
                    busyHint(month, counts),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                )
            }
            MonthArrow("‹", "Предыдущий месяц") { onMonthChange(month.minusMonths(1)) }
            Spacer(Modifier.width(4.dp))
            MonthArrow("›", "Следующий месяц") { onMonthChange(month.plusMonths(1)) }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth()) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { name ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

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

    // Выбранный день заливается целиком, остальные — по загруженности.
    val fill by animateColorAsState(
        when {
            isSelected -> palette.accent
            else -> palette.accent.copy(alpha = loadAlpha(count))
        },
        label = "cell",
    )

    Box(modifier.aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(fill)
                .then(
                    if (isToday && !isSelected) Modifier.border(1.5.dp, palette.accent, CircleShape)
                    else Modifier
                )
                .quietClickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    isSelected -> palette.onAccent
                    (count ?: 0) >= 4 -> palette.textPrimary
                    isToday -> palette.accent
                    else -> palette.textSecondary
                },
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/**
 * Прозрачность клетки по числу пар.
 *
 * Шкала нелинейная: разница между «одной парой» и «двумя» важнее для глаза,
 * чем между «пятью» и «шестью», поэтому нижние ступени разведены сильнее.
 */
private fun loadAlpha(count: Int?): Float = when (count) {
    null -> 0f      // нет данных
    0 -> 0f         // свободный день
    1 -> 0.12f
    2 -> 0.26f
    3 -> 0.44f
    4 -> 0.62f
    5 -> 0.80f
    else -> 0.94f
}

private fun busyHint(month: YearMonth, counts: Map<LocalDate, Int>): String {
    val inMonth = counts.filterKeys { YearMonth.from(it) == month }
    if (inMonth.isEmpty()) return "Нет данных"
    val lessons = inMonth.values.sum()
    val busyDays = inMonth.count { it.value > 0 }
    return "$lessons пар · $busyDays учебных дней"
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
    Box(modifier.fillMaxWidth().padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(38.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.divider)
        )
    }
}
