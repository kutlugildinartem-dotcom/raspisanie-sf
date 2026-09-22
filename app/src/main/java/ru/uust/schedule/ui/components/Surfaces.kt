package ru.uust.schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.uust.schedule.ui.theme.LocalPalette

/** Фон экрана — сплошной, без градиентов: содержимое должно читаться, а не соревноваться с фоном. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val palette = LocalPalette.current
    Box(modifier.fillMaxSize().background(palette.background), content = content)
}

/** Непрозрачная карточка со скруглением. Ни рамки, ни тени — их роль играет разница светлоты. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    elevated: Boolean = false,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(corner)
    val fill = color ?: if (elevated) palette.surfaceHigh else palette.surface

    Box(
        modifier
            .clip(shape)
            .background(fill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Пилюля-чип: выбранное состояние заливается приглушённым акцентом. */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.tint(0.16f) else palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        androidx.compose.material3.Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = if (selected) palette.accent else palette.textSecondary,
        )
    }
}

/** Клик без ряби — для мест, где подсветка нажатия мешает (крупные области, иконки). */
@Composable
fun Modifier.quietClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * Строка, которая по свайпу влево открывает кнопки действий.
 *
 * Сделано жестом, а не постоянно видимыми иконками: в списке предметов
 * действия нужны редко, и висящие кнопки только мешали бы читать названия.
 */
@Composable
fun SwipeRevealRow(
    revealWidth: Dp = 128.dp,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxOffset = with(density) { -revealWidth.toPx() }
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box {
        // Кнопки лежат под строкой и видны ровно настолько, насколько её сдвинули.
        Row(
            Modifier
                .matchParentSize()
                .padding(end = 2.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            content = actions,
        )

        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offset.value.toInt(), 0) }
                .draggable(
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(maxOffset, 0f))
                        }
                    },
                    onDragStopped = {
                        // Доводим до ближайшего края: полуоткрытое состояние выглядит поломкой.
                        val target = if (offset.value < maxOffset / 2) maxOffset else 0f
                        scope.launch { offset.animateTo(target) }
                    },
                ),
        ) {
            content()
        }
    }
}
