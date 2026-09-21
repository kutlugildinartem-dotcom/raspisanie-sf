package ru.uust.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.uust.schedule.domain.FACULTIES
import ru.uust.schedule.domain.Group
import ru.uust.schedule.ui.ScheduleViewModel
import ru.uust.schedule.ui.components.Card
import ru.uust.schedule.ui.theme.LocalPalette

/**
 * Первый экран: выбор группы.
 *
 * Поиск идёт по кешированному справочнику всех факультетов — так группу можно
 * найти, не зная, к какому факультету она относится.
 */
@Composable
fun OnboardingScreen(vm: ScheduleViewModel) {
    val palette = LocalPalette.current
    val ui by vm.ui.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var facultyId by remember { mutableStateOf(FACULTIES.first().id) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var results by remember { mutableStateOf<List<Group>>(emptyList()) }

    LaunchedEffect(Unit) { vm.loadGroupCatalog() }

    LaunchedEffect(facultyId, ui.loadingGroups) {
        if (!ui.loadingGroups) vm.groupsOfFaculty(facultyId) { groups = it }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) results = emptyList()
        else vm.searchGroups(query) { results = it }
    }

    val shown = if (query.isBlank()) groups else results

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 64.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.tint(0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.School, null, tint = palette.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Какая у вас группа?",
                    style = MaterialTheme.typography.displaySmall,
                    color = palette.textPrimary,
                )
                Text(
                    "Расписание СФ УУНиТ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Например, ПМИ21", color = palette.textMuted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = palette.accent) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.divider,
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                cursorColor = palette.accent,
            ),
        )

        if (query.isBlank()) {
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FACULTIES) { faculty ->
                    FacultyChip(
                        name = faculty.name,
                        selected = faculty.id == facultyId,
                        onClick = { facultyId = faculty.id },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            ui.loadingGroups -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = palette.accent)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Загружаем список групп",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                    )
                }
            }

            shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "Групп не найдено" else "Ничего не нашлось",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textMuted,
                )
            }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { group ->
                    GroupRow(group, showFaculty = query.isNotBlank()) { vm.chooseGroup(group) }
                }
            }
        }
    }
}

@Composable
private fun FacultyChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.tint(0.18f) else palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) palette.accent else palette.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun GroupRow(group: Group, showFaculty: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        corner = 16.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
                if (showFaculty) {
                    Text(
                        group.facultyName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                    )
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = palette.accent)
        }
    }
}
