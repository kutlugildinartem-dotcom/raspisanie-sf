package ru.uust.schedule.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.data.prefs.AppSettings
import ru.uust.schedule.data.prefs.NeonTheme
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.repo.ScheduleRepository
import ru.uust.schedule.data.remote.ReleaseInfo
import ru.uust.schedule.data.repo.SubjectSummary
import ru.uust.schedule.data.update.UpdateManager
import ru.uust.schedule.data.update.UpdateState
import ru.uust.schedule.domain.DayLogic
import ru.uust.schedule.domain.DaySchedule
import ru.uust.schedule.domain.Group
import ru.uust.schedule.widget.WidgetUpdater
import ru.uust.schedule.work.SyncScheduler
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ScheduleRepository.get(app)
    private val store = SettingsStore.get(app)

    val settings: StateFlow<AppSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var dateInitialized = false

    init {
        viewModelScope.launch {
            // Первый выбранный день подчиняется тому же правилу, что и виджет.
            settings.collect { s ->
                if (!dateInitialized && s.groupId != 0) {
                    dateInitialized = true
                    _selectedDate.value = DayLogic.defaultDate(LocalDateTime.now(), s.switchHour)
                    refresh(silent = true)
                }
            }
        }
        viewModelScope.launch {
            combine(settings, _selectedDate) { s, date -> s.groupId to date }
                .collect { (groupId, date) -> loadDay(groupId, date) }
        }
    }

    private suspend fun loadDay(groupId: Int, date: LocalDate) {
        if (groupId == 0) return
        val day = repo.cachedDay(groupId, date)
        val notes = repo.notes(groupId)
        _ui.value = _ui.value.copy(day = day, notes = notes)
    }

    fun shiftDay(delta: Int) {
        _selectedDate.value = DayLogic.shift(_selectedDate.value, delta)
    }

    fun jumpToDefault() {
        _selectedDate.value = DayLogic.defaultDate(LocalDateTime.now(), settings.value.switchHour)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = DayLogic.skipSunday(date)
    }

    /** Подтягивает текущую и следующую неделю. [silent] — не показывать спиннер, если кеш уже есть. */
    fun refresh(silent: Boolean = false, force: Boolean = false) {
        val groupId = settings.value.groupId
        if (groupId == 0) return
        viewModelScope.launch {
            if (!force && silent && !repo.isStale(groupId)) {
                loadDay(groupId, _selectedDate.value)
                return@launch
            }
            _ui.value = _ui.value.copy(loading = true, error = null)
            runCatching { repo.syncWeeks(groupId, listOf(-1, 0, 1)) }
                .onSuccess {
                    _ui.value = _ui.value.copy(loading = false, error = null)
                    loadDay(groupId, _selectedDate.value)
                    WidgetUpdater.updateAll(getApplication())
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        error = e.message ?: "Не удалось загрузить расписание",
                    )
                    loadDay(groupId, _selectedDate.value)
                }
        }
    }

    // --- выбор группы ---

    fun loadGroupCatalog() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loadingGroups = true)
            runCatching { repo.ensureGroupsLoaded() }
            _ui.value = _ui.value.copy(loadingGroups = false)
        }
    }

    fun groupsOfFaculty(facultyId: Int, onResult: (List<Group>) -> Unit) {
        viewModelScope.launch { onResult(repo.groupsOfFaculty(facultyId)) }
    }

    fun searchGroups(query: String, onResult: (List<Group>) -> Unit) {
        viewModelScope.launch { onResult(repo.searchGroups(query)) }
    }

    fun chooseGroup(group: Group) {
        viewModelScope.launch {
            store.update { it.copy(groupId = group.id, groupName = group.name, onboarded = true) }
            _selectedDate.value = DayLogic.defaultDate(LocalDateTime.now(), settings.value.switchHour)
            refresh(force = true)
            SyncScheduler.schedule(getApplication())
        }
    }

    // --- обновления ---

    val updateState: StateFlow<UpdateState> = UpdateManager.state

    /** Тихая проверка при запуске: баннер появляется, только если версия новее. */
    fun checkForUpdates(silent: Boolean = true) {
        viewModelScope.launch { UpdateManager.check(silent) }
    }

    fun installUpdate(release: ReleaseInfo) {
        viewModelScope.launch { UpdateManager.downloadAndInstall(getApplication(), release) }
    }

    fun dismissUpdate() = UpdateManager.dismiss()

    /** Возврат к выбору группы. Кеш расписания не трогаем — он ещё пригодится при возврате. */
    fun changeGroup() {
        viewModelScope.launch { store.update { it.copy(onboarded = false) } }
    }

    // --- тема ---

    fun updateAppTheme(theme: NeonTheme) {
        viewModelScope.launch {
            store.update { it.copy(appTheme = theme) }
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun updateWidgetTheme(theme: NeonTheme?) {
        viewModelScope.launch {
            store.update { it.copy(widgetTheme = theme) }
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun updateSwitchHour(hour: Int) {
        viewModelScope.launch {
            store.update { it.copy(switchHour = hour.coerceIn(0, 23)) }
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun updateNotifications(enabled: Boolean, minutesBefore: Int) {
        viewModelScope.launch {
            store.update { it.copy(notificationsEnabled = enabled, notifyMinutesBefore = minutesBefore) }
            SyncScheduler.schedule(getApplication())
        }
    }

    // --- предметы и заметки ---

    fun loadSubjects(onResult: (List<SubjectSummary>) -> Unit) {
        viewModelScope.launch { onResult(repo.subjectsOf(settings.value.groupId)) }
    }

    fun notesFlow() = repo.notesFlow(settings.value.groupId)

    fun saveNote(subject: String, text: String, hue: Int) {
        viewModelScope.launch {
            repo.saveNote(
                SubjectNoteEntity(
                    groupId = settings.value.groupId,
                    subject = subject,
                    note = text,
                    hue = hue,
                )
            )
            loadDay(settings.value.groupId, _selectedDate.value)
            WidgetUpdater.updateAll(getApplication())
        }
    }

    data class UiState(
        val day: DaySchedule? = null,
        val notes: Map<String, SubjectNoteEntity> = emptyMap(),
        val loading: Boolean = false,
        val loadingGroups: Boolean = false,
        val error: String? = null,
    )
}
