package ru.uust.schedule.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.uust.schedule.ui.components.quietClickable
import ru.uust.schedule.ui.theme.LocalPalette

enum class Tab(val title: String, val icon: ImageVector) {
    Schedule("Расписание", Icons.Rounded.CalendarToday),
    Subjects("Предметы", Icons.Rounded.MenuBook),
    Settings("Настройки", Icons.Rounded.Tune),
}

/**
 * Плавающая панель-пилюля. Активная вкладка — залитая пилюля с подписью,
 * у остальных подпись скрыта: так строка не рябит и видно, где находишься.
 */
@Composable
fun NavBar(current: Tab, onSelect: (Tab) -> Unit) {
    val palette = LocalPalette.current

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surface)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val tint by animateColorAsState(
                    if (selected) palette.onAccent else palette.textMuted,
                    label = "tint",
                )

                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (selected) palette.accent else Color.Transparent)
                        .quietClickable { onSelect(tab) }
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tab.icon, tab.title, tint = tint, modifier = Modifier.size(19.dp))
                    if (selected) {
                        Spacer(Modifier.size(7.dp))
                        Text(
                            tab.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

