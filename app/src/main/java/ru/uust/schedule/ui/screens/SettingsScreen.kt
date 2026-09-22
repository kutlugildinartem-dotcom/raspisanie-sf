package ru.uust.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.runtime.remember
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
import ru.uust.schedule.data.prefs.AppTheme
import ru.uust.schedule.data.prefs.ScheduleLayout
import ru.uust.schedule.data.update.UpdateManager
import ru.uust.schedule.data.update.UpdateState
import ru.uust.schedule.ui.ScheduleViewModel
import ru.uust.schedule.ui.components.Card
import ru.uust.schedule.ui.components.Chip
import ru.uust.schedule.ui.components.quietClickable
import ru.uust.schedule.ui.theme.LocalPalette
import ru.uust.schedule.ui.theme.Palette

/**
 * Настройки.
 *
 * Оформление сведено к трём решениям: основа светлая или тёмная, какой оттенок
 * у акцента и насколько он сочный. Тема одна на приложение и виджеты — раздельные
 * настройки приводили только к тому, что телефон выглядел собранным из двух разных
 * приложений.
 */
@Composable
fun SettingsScreen(vm: ScheduleViewModel) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val theme = settings.theme

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 58.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.displayMedium,
            color = palette.textPrimary,
        )

        Section("Главный экран") {
            ScheduleLayout.entries.forEachIndexed { index, layout ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                LayoutOption(
                    layout = layout,
                    selected = settings.layout == layout,
                    onClick = { vm.updateLayout(layout) },
                )
            }
        }

        Section("Оформление") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BaseOption(
                    title = "Тёмная",
                    selected = theme.dark,
                    preview = Palette.from(theme.copy(dark = true)),
                    modifier = Modifier.weight(1f),
                    onClick = { vm.updateTheme(theme.copy(dark = true)) },
                )
                BaseOption(
                    title = "Светлая",
                    selected = !theme.dark,
                    preview = Palette.from(theme.copy(dark = false)),
                    modifier = Modifier.weight(1f),
                    onClick = { vm.updateTheme(theme.copy(dark = false)) },
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("Акцент", style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppTheme.PRESETS.forEach { (name, hue) ->
                    Swatch(
                        name = name,
                        color = Palette.from(theme.copy(hue = hue)).accent,
                        selected = kotlin.math.abs(theme.hue - hue) < 1f,
                        onClick = { vm.updateTheme(theme.copy(hue = hue)) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HueSlider(
                value = theme.hue,
                onChange = { vm.updateTheme(theme.copy(hue = it)) },
            )
            Spacer(Modifier.height(10.dp))
            LabeledSlider(
                label = "Сочность",
                value = theme.intensity,
                onChange = { vm.updateTheme(theme.copy(intensity = it)) },
            )
            LabeledSlider(
                label = "Контраст карточек",
                value = theme.contrast,
                onChange = { vm.updateTheme(theme.copy(contrast = it)) },
            )
        }

        Section("Виджет «День»") {
            Text(
                "После этого часа виджет показывает завтрашний день",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(12, 14, 15, 16, 17, 18, 19, 20, 21, 22).forEach { hour ->
                    Chip(
                        text = "$hour:00",
                        selected = settings.switchHour == hour,
                        onClick = { vm.updateSwitchHour(hour) },
                    )
                }
            }
        }

        Section("Уведомления") {
            ToggleRow(
                title = "Напоминать о парах",
                subtitle = "За ${settings.notifyMinutesBefore} мин до начала",
                checked = settings.notificationsEnabled,
                onChange = { vm.updateNotifications(it, settings.notifyMinutesBefore) },
            )
            if (settings.notificationsEnabled) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 10, 15, 20, 30, 45, 60).forEach { minutes ->
                        Chip(
                            text = "$minutes мин",
                            selected = settings.notifyMinutesBefore == minutes,
                            onClick = { vm.updateNotifications(true, minutes) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "Изменения в расписании",
                subtitle = "Когда пара на уже показанный день меняется на сайте",
                checked = settings.notifyScheduleChanges,
                onChange = { vm.updateExtraNotifications(it, settings.notifyNextWeekAdded) },
            )
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "Расписание на след. неделю добавлено",
                subtitle = "Сайт публикует её не сразу — сообщим, когда появится",
                checked = settings.notifyNextWeekAdded,
                onChange = { vm.updateExtraNotifications(settings.notifyScheduleChanges, it) },
            )
        }

        Section("Обновления") {
            val updateState by vm.updateState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val available = updateState as? UpdateState.Available

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Версия ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                    )
                    Text(
                        when (val s = updateState) {
                            is UpdateState.Checking -> "Проверяем…"
                            is UpdateState.UpToDate -> "Установлена последняя версия"
                            is UpdateState.Available -> "Доступна ${s.release.versionName}"
                            is UpdateState.Downloading -> {
                                val kind = if (s.isDelta) "изменений" else "обновления"
                                "Загрузка $kind: ${(s.progress * 100).toInt()}%"
                            }
                            is UpdateState.ReadyToInstall -> "Подтвердите установку"
                            is UpdateState.Failed -> s.message
                            else -> "Проверяется раз в сутки"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (updateState is UpdateState.Failed) palette.danger
                        else palette.textMuted,
                    )
                }

                ActionButton(if (available != null) "Установить" else "Проверить") {
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
            }
        }

        Section("Группа") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        settings.groupName.ifBlank { "не выбрана" },
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                    )
                    Text(
                        "Расписание обновляется само",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                    )
                }
                ActionButton("Сменить") { vm.changeGroup() }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = palette.textMuted,
            modifier = Modifier.padding(start = 6.dp, bottom = 9.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

/** Наглядный выбор основы: показывает саму пару «фон + карточка», а не слово. */
@Composable
private fun BaseOption(
    title: String,
    selected: Boolean,
    preview: Palette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) palette.tint(0.14f) else palette.surfaceHigh)
            .quietClickable(onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(preview.background)
                .padding(8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(preview.surface)
            )
            Box(
                Modifier
                    .padding(top = 20.dp)
                    .width(38.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(preview.accent)
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) palette.accent else palette.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Swatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .quietClickable(onClick),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) palette.accent else palette.textMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * Ползунок оттенка с радужной подложкой на дорожке.
 *
 * Обычный Slider тут не годится: без подсказки, какой цвет на какой позиции,
 * ползунок приходится крутить вслепую и подглядывать в превью карточек.
 */
@Composable
private fun HueSlider(value: Float, onChange: (Float) -> Unit) {
    val palette = LocalPalette.current
    val rainbow = remember {
        Brush.horizontalGradient((0..360 step 30).map { Color.hsv(it.toFloat(), 0.85f, 0.9f) })
    }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Оттенок", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            Spacer(Modifier.weight(1f))
            Text("${value.toInt()}°", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(rainbow)
            )
            Slider(
                value = value.coerceIn(0f, 360f),
                onValueChange = onChange,
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
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
    display: String? = null,
    onChange: (Float) -> Unit,
) {
    val palette = LocalPalette.current
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                display ?: "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
            )
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.divider,
            ),
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
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.accent,
                uncheckedThumbColor = palette.textMuted,
                uncheckedTrackColor = palette.surfaceHigh,
                uncheckedBorderColor = palette.divider,
            ),
        )
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(palette.tint(0.16f))
            .quietClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = palette.accent,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Строка выбора режима: название, пояснение и мини-схема раскладки. */
@Composable
private fun LayoutOption(
    layout: ScheduleLayout,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) palette.tint(0.14f) else palette.surfaceHigh)
            .quietClickable(onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LayoutPreview(layout, selected)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                layout.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) palette.accent else palette.textPrimary,
            )
            Text(
                layout.description,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
        }
    }
}

/** Схема из полосок: показать раскладку нагляднее, чем описать словами. */
@Composable
private fun LayoutPreview(layout: ScheduleLayout, selected: Boolean) {
    val palette = LocalPalette.current
    val bar = if (selected) palette.accent else palette.textMuted
    val faint = bar.copy(alpha = 0.35f)

    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(palette.background)
            .padding(6.dp),
    ) {
        when (layout) {
            ScheduleLayout.Day -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { Bar(bar, 1f) }
            }

            ScheduleLayout.Feed -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Bar(bar, 0.45f)
                Bar(faint, 1f)
                Bar(bar, 0.45f)
                Bar(faint, 1f)
            }

            ScheduleLayout.Grid -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.weight(1f)) { Bar(bar, 1f, tall = true) }
                        Box(Modifier.weight(1f)) { Bar(faint, 1f, tall = true) }
                    }
                }
            }

        }
    }
}

@Composable
private fun Bar(color: Color, widthFraction: Float, tall: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(if (tall) 13.dp else 6.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}
