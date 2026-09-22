package ru.uust.schedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.uust.schedule.data.update.UpdateState
import ru.uust.schedule.ui.theme.LocalPalette

/**
 * Баннер обновления над расписанием.
 *
 * Появляется только когда есть что сказать: «доступна версия», ход загрузки
 * или ошибка. В остальное время не занимает места.
 */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = state !is UpdateState.Idle && state !is UpdateState.Checking

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        when (state) {
            is UpdateState.Available -> AvailableBanner(state, onInstall, onDismiss)
            is UpdateState.Downloading -> DownloadingBanner(state)
            is UpdateState.ReadyToInstall -> InfoBanner("Подтвердите установку в окне Android")
            is UpdateState.UpToDate -> InfoBanner("У вас последняя версия")
            is UpdateState.Failed -> InfoBanner(state.message, isError = true)
            else -> Unit
        }
    }
}

@Composable
private fun AvailableBanner(
    state: UpdateState.Available,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val release = state.release

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        corner = 18.dp,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Download, null,
                    tint = palette.accent, modifier = Modifier.size(19.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "Версия ${release.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
                Text(
                    release.notes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
                        ?: downloadSizeHint(release),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.accent.copy(alpha = 0.20f))
                    .clickable(onClick = onInstall)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    "Установить",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent,
                    fontWeight = FontWeight.Bold,
                )
            }

            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close, "Скрыть",
                    tint = palette.textMuted, modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadingBanner(state: UpdateState.Downloading) {
    val palette = LocalPalette.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        corner = 18.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Загрузка ${state.release.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                    )
                    if (state.isDelta) {
                        Text(
                            "только изменения, ${state.release.patch?.sizeMb ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent,
                        )
                    }
                }
                Text(
                    "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                )
            }
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = palette.accent,
                trackColor = palette.divider,
                drawStopIndicator = {},
            )
        }
    }
}

/**
 * Что покажет счётчик под названием версии в баннере «доступно обновление» —
 * до нажатия «Установить» человек должен видеть, сколько реально скачается.
 */
private fun downloadSizeHint(release: ru.uust.schedule.data.remote.ReleaseInfo): String {
    val patch = release.patch
    return if (patch != null && patch.fromVersion == ru.uust.schedule.BuildConfig.VERSION_NAME) {
        "Обновление · ${patch.sizeMb} вместо ${release.sizeMb}"
    } else {
        "Обновление · ${release.sizeMb}"
    }
}

@Composable
private fun InfoBanner(text: String, isError: Boolean = false) {
    val palette = LocalPalette.current
    val tint = if (isError) palette.danger else palette.accent

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}
