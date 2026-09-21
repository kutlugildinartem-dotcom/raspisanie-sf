package ru.uust.schedule.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.uust.schedule.ui.theme.LocalNeon

enum class Tab(val title: String, val icon: ImageVector) {
    Schedule("Расписание", Icons.Rounded.CalendarMonth),
    Subjects("Предметы", Icons.Rounded.MenuBook),
    Settings("Настройки", Icons.Rounded.Tune),
}

/** Нижняя панель — плавающее стекло, а не сплошная полоса, чтобы фон просвечивал. */
@Composable
fun NeonNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    val neon = LocalNeon.current

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(listOf(neon.glassStrong, neon.glass))
                )
                .border(1.dp, neon.stroke.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(5.dp),
        ) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val tint by animateColorAsState(
                    if (selected) neon.accent else neon.textMuted,
                    label = "tint",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selected) neon.accent.copy(alpha = 0.16f)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable { onSelect(tab) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(tab.icon, tab.title, tint = tint, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(
                            tab.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
