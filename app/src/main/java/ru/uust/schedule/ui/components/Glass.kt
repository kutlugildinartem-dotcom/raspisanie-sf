package ru.uust.schedule.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.uust.schedule.ui.theme.LocalNeon

/**
 * Стеклянная карточка: полупрозрачная заливка, неоновый контур и мягкое свечение.
 *
 * Свечение рисуется радиальным градиентом за карточкой, а не Modifier.blur —
 * blur требует API 31, а minSdk здесь 26.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    accent: Color? = null,
    glowScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val neon = LocalNeon.current
    val glowColor = accent ?: neon.glowColor
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .drawBehind {
                val a = neon.glowAlpha * 0.5f * glowScale
                if (a > 0.01f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = a), Color.Transparent),
                            center = Offset(size.width * 0.15f, size.height * 0.5f),
                            radius = size.maxDimension * 0.85f,
                        ),
                        size = Size(size.width, size.height),
                    )
                }
            }
            .shadow(
                elevation = (12 * neon.glowAlpha * glowScale).dp,
                shape = shape,
                ambientColor = glowColor,
                spotColor = glowColor,
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(neon.glassStrong, neon.glass),
                )
            )
            .border(1.dp, Brush.verticalGradient(
                listOf(glowColor.copy(alpha = 0.55f * neon.glowAlpha + 0.12f), neon.stroke.copy(alpha = 0.10f)),
            ), shape),
        content = content,
    )
}

/** Фон приложения: два медленно дышащих цветных пятна поверх тёмной базы. */
@Composable
fun NeonBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val neon = LocalNeon.current
    val drift = if (animated) {
        val t = rememberInfiniteTransition(label = "bg")
        t.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18_000), RepeatMode.Reverse),
            label = "drift",
        ).value
    } else 0.5f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(neon.background)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(neon.backgroundTop.copy(alpha = 0.9f), Color.Transparent),
                        center = Offset(size.width * (0.15f + drift * 0.25f), size.height * 0.12f),
                        radius = size.maxDimension * 0.75f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(neon.backgroundBottom.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(size.width * (0.9f - drift * 0.3f), size.height * 0.88f),
                        radius = size.maxDimension * 0.7f,
                    )
                )
            },
        content = content,
    )
}

/** Тонкая вертикальная полоса-маркер слева от карточки пары. */
@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f))))
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val neon = LocalNeon.current
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(neon.glass)
            .border(1.dp, neon.stroke.copy(alpha = 0.25f), RoundedCornerShape(corner))
            .padding(1.dp),
        content = content,
    )
}
