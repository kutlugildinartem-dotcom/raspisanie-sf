package ru.uust.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.uust.schedule.BuildConfig
import ru.uust.schedule.data.prefs.NeonTheme
import ru.uust.schedule.data.update.UpdateManager
import ru.uust.schedule.data.update.UpdateState
import ru.uust.schedule.ui.ScheduleViewModel
import ru.uust.schedule.ui.components.GlassCard
import ru.uust.schedule.ui.theme.LocalNeon
import ru.uust.schedule.ui.theme.NeonPalette

/**
 * Настройки: внешний вид, поведение виджета «День», уведомления, группа.
 *
 * Тема виджетов по умолчанию следует за темой приложения, но её можно отвязать —
 * на тёмных обоях часто нужна другая прозрачность, чем внутри приложения.
 */
@Composable
fun SettingsScreen(vm: ScheduleViewModel) {
    val neon = LocalNeon.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 56.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.displaySmall,
            color = neon.textPrimary,
        )

        // --- пресеты ---
        SettingsSection("Готовые схемы") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NeonTheme.PRESETS.forEach { (name, preset) ->
                    PresetSwatch(
                        name = name,
                        theme = preset,
                        selected = settings.appTheme.hue == preset.hue &&
                            settings.appTheme.backgroundLift == preset.backgroundLift,
                        onClick = { vm.updateAppTheme(preset) },
                    )
                }
            }
        }

        // --- тонкая настройка ---
        SettingsSection("Цвет приложения") {
            HueSlider(
                value = settings.appTheme.hue,
                onChange = { vm.updateAppTheme(settings.appTheme.copy(hue = it)) },
            )
            LabeledSlider(
                label = "Насыщенность",
                value = settings.appTheme.saturation,
                onChange = { vm.updateAppTheme(settings.appTheme.copy(saturation = it)) },
            )
            LabeledSlider(
                label = "Яркость неона",
                value = settings.appTheme.brightness,
                range = 0.35f..1f,
                onChange = { vm.updateAppTheme(settings.appTheme.copy(brightness = it)) },
            )
            LabeledSlider(
                label = "Прозрачность стекла",
                value = settings.appTheme.glassOpacity,
                range = 0.05f..0.85f,
                onChange = { vm.updateAppTheme(settings.appTheme.copy(glassOpacity = it)) },
            )
            LabeledSlider(
                label = "Свечение",
                value = settings.appTheme.glow,
                onChange = { vm.updateAppTheme(settings.appTheme.copy(glow = it)) },
            )
            LabeledSlider(
                label = "Светлота фона",
                value = settings.appTheme.backgroundLift,
                onChange = { vm.updateAppTheme(settings.appTheme.copy(backgroundLift = it)) },
            )
            LabeledSlider(
                label = "Разброс градиента",
                value = (settings.appTheme.hueSpread + 120f) / 240f,
                onChange = {
                    vm.updateAppTheme(settings.appTheme.copy(hueSpread = it * 240f - 120f))
                },
            )
        }

        // --- виджеты ---
        SettingsSection("Виджеты") {
            ToggleRow(
                title = "Своя тема для виджетов",
                subtitle = if (settings.widgetTheme == null)
                    "Сейчас как в приложении" else "Настраивается отдельно",
                checked = settings.widgetTheme != null,
                onChange = { on ->
                    vm.updateWidgetTheme(if (on) settings.appTheme.copy() else null)
                },
            )

            settings.widgetTheme?.let { wt ->
                Spacer(Modifier.height(4.dp))
                HueSlider(value = wt.hue, onChange = { vm.updateWidgetTheme(wt.copy(hue = it)) })
                LabeledSlider(
                    label = "Прозрачность виджета",
                    value = wt.glassOpacity,
                    range = 0.05f..0.95f,
                    onChange = { vm.updateWidgetTheme(wt.copy(glassOpacity = it)) },
                )
                LabeledSlider(
                    label = "Свечение виджета",
                    value = wt.glow,
                    onChange = { vm.updateWidgetTheme(wt.copy(glow = it)) },
                )
                LabeledSlider(
                    label = "Светлота подложки",
                    value = wt.backgroundLift,
                    onChange = { vm.updateWidgetTheme(wt.copy(backgroundLift = it)) },
                )
            }
        }

        // --- поведение ---
        SettingsSection("Виджет «День»") {
            Text(
                "После этого часа виджет показывает завтрашний день",
                style = MaterialTheme.typography.bodyMedium,
                color = neon.textMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(12, 14, 15, 16, 17, 18, 19, 20, 21, 22).forEach { hour ->
                    HourChip(
                        hour = hour,
                        selected = settings.switchHour == hour,
                        onClick = { vm.updateSwitchHour(hour) },
                    )
                }
            }
        }

        // --- уведомления ---
        SettingsSection("Уведомления") {
            ToggleRow(
                title = "Напоминать о парах",
                subtitle = "Пуш за ${settings.notifyMinutesBefore} мин до начала",
                checked = settings.notificationsEnabled,
                onChange = { vm.updateNotifications(it, settings.notifyMinutesBefore) },
            )
            if (settings.notificationsEnabled) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 10, 15, 20, 30, 45, 60).forEach { minutes ->
                        MinutesChip(
                            minutes = minutes,
                            selected = settings.notifyMinutesBefore == minutes,
                            onClick = { vm.updateNotifications(true, minutes) },
                        )
                    }
                }
            }
        }

        // --- обновления ---
        SettingsSection("Обновления") {
            val updateState by vm.updateState.collectAsStateWithLifecycle()
            val context = LocalContext.current

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Версия ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        color = neon.textPrimary,
                    )
                    Text(
                        when (val s = updateState) {
                            is UpdateState.Checking -> "Проверяем…"
                            is UpdateState.UpToDate -> "Установлена последняя версия"
                            is UpdateState.Available -> "Доступна ${s.release.versionName}"
                            is UpdateState.Downloading ->
                                "Загрузка ${(s.progress * 100).toInt()}%"
                            is UpdateState.ReadyToInstall -> "Подтвердите установку"
                            is UpdateState.Failed -> s.message
                            else -> "Обновляется через GitHub, поверх установленной версии"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (updateState is UpdateState.Failed) neon.danger else neon.textMuted,
                    )
                }

                val available = updateState as? UpdateState.Available
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(neon.accent.copy(alpha = 0.16f))
                        .clickable {
                            if (available != null) {
                                if (UpdateManager.canInstallPackages(context)) {
                                    vm.installUpdate(available.release)
                                } else {
                                    context.startActivity(
                                        UpdateManager.unknownSourcesSettingsIntent(context)
                                    )
                                }
                            } else {
                                vm.checkForUpdates(silent = false)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        if (available != null) "Установить" else "Проверить",
                        style = MaterialTheme.typography.labelLarge,
                        color = neon.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // --- группа ---
        SettingsSection("Группа") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        settings.groupName.ifBlank { "не выбрана" },
                        style = MaterialTheme.typography.titleMedium,
                        color = neon.textPrimary,
                    )
                    Text(
                        "edu.str.uust.ru",
                        style = MaterialTheme.typography.bodyMedium,
                        color = neon.textMuted,
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(neon.accent.copy(alpha = 0.16f))
                        .clickable { vm.changeGroup() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Сменить",
                        style = MaterialTheme.typography.labelLarge,
                        color = neon.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val neon = LocalNeon.current
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = neon.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, glowScale = 0.4f) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

/** Ползунок оттенка на фоне полной радуги — сразу видно, какой цвет выбираешь. */
@Composable
private fun HueSlider(value: Float, onChange: (Float) -> Unit) {
    val neon = LocalNeon.current
    val rainbow = remember {
        (0..12).map { NeonPalette.hsv(it * 30f, 0.85f, 1f) }
    }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Оттенок", style = MaterialTheme.typography.bodyMedium, color = neon.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                "${value.toInt()}°",
                style = MaterialTheme.typography.labelSmall,
                color = neon.accent,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(rainbow))
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = neon.accent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    onChange: (Float) -> Unit,
) {
    val neon = LocalNeon.current
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = neon.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                "${((value - range.start) / (range.endInclusive - range.start) * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = neon.textMuted,
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = neon.accent,
                activeTrackColor = neon.accent,
                inactiveTrackColor = neon.stroke.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun PresetSwatch(
    name: String,
    theme: NeonTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val neon = LocalNeon.current
    val palette = NeonPalette.from(theme)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(palette.accent, palette.accentAlt)))
                .clickable(onClick = onClick)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) palette.accent else neon.textMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val neon = LocalNeon.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = neon.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = neon.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = neon.background,
                checkedTrackColor = neon.accent,
                uncheckedThumbColor = neon.textMuted,
                uncheckedTrackColor = neon.glass,
            ),
        )
    }
}

@Composable
private fun HourChip(hour: Int, selected: Boolean, onClick: () -> Unit) =
    Chip(text = "$hour:00", selected = selected, onClick = onClick)

@Composable
private fun MinutesChip(minutes: Int, selected: Boolean, onClick: () -> Unit) =
    Chip(text = "$minutes мин", selected = selected, onClick = onClick)

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val neon = LocalNeon.current
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) neon.accent.copy(alpha = 0.22f) else neon.glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) neon.accent else neon.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
