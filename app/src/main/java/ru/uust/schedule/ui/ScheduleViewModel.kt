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
import ru.uust.schedule.data.local.LessonRecordEntity
import ru.uust.schedule.data.local.RecordKey
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.data.prefs.AppSettings
import ru.uust.schedule.data.prefs.AppTheme
import ru.uust.schedule.data.prefs.ScheduleLayout
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
import java.time.YearMonth

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
        val records = repo.recordsFor(groupId, date)
        _ui.value = _ui.value.copy(
            day = day,
            notes = notes,
            records = records,
            weekMonday = ScheduleRepository.mondayOf(date),
        )
        // Недельные режимы рисуют сразу неделю — держим её загруженной.
        loadWeek(groupId, ScheduleRepository.mondayOf(date))
    }

    /**
     * Дни недели, показываемой в ленте и сетке, плюс записи вокруг неё.
     * Записи берутся с запасом: карточки соседних дней тоже должны знать
     * про домашку, когда пользователь листает неделю.
     */
    private suspend fun loadWeek(groupId: Int, monday: LocalDate) {
        _ui.value = _ui.value.copy(
            weekDays = repo.daysBetween(groupId, monday, monday.plusDays(6)),
            rangeRecords = repo.recordsBetween(
                groupId, monday.minusWeeks(1), monday.plusWeeks(2),
            ),
        )
        repo.ensureWeekLoaded(groupId, monday)
        _ui.value = _ui.value.copy(
            weekDays = repo.daysBetween(groupId, monday, monday.plusDays(6)),
        )
    }

    /**
     * Листание недели в «Ленте» и «Двух колонках».
     *
     * Двигает саму [_selectedDate], а не отдельное поле недели: раньше неделя
     * хранилась параллельно и любое сохранение (оценка, ДЗ, заметка) вызывало
     * [loadDay] от устаревшей даты и откатывало экран на прошлую неделю.
     * Одна дата — один источник правды, откатывать больше нечему.
     */
    fun shiftWeek(delta: Int) {
        _selectedDate.value = DayLogic.skipSunday(_selectedDate.value.plusWeeks(delta.toLong()))
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

    fun updateTheme(theme: AppTheme) {
        viewModelScope.launch {
            store.update { it.copy(theme = theme) }
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
            // Без этого включение тумблера ставило будильники только на следующий
            // тик фоновой задачи (до получаса) — сегодняшние пары могли остаться без напоминания.
            ru.uust.schedule.work.LessonNotifier.rescheduleToday(getApplication())
        }
    }

    fun updateExtraNotifications(scheduleChanges: Boolean, nextWeekAdded: Boolean) {
        viewModelScope.launch {
            store.update {
                it.copy(notifyScheduleChanges = scheduleChanges, notifyNextWeekAdded = nextWeekAdded)
            }
        }
    }

    // --- предметы и заметки ---

    fun loadSubjects(onResult: (List<SubjectSummary>) -> Unit) {
        viewModelScope.launch { onResult(repo.subjectsOf(settings.value.groupId)) }
    }

    fun notesFlow() = repo.notesFlow(settings.value.groupId)

    fun saveNote(
        subject: String,
        text: String,
        hue: Int,
        custom: Boolean = false,
        teacherFull: String = "",
    ) {
        viewModelScope.launch {
            repo.saveNote(
                SubjectNoteEntity(
                    groupId = settings.value.groupId,
                    subject = subject,
                    note = text,
                    hue = hue,
                    custom = custom,
                    teacherFull = teacherFull,
                )
            )
            loadDay(settings.value.groupId, _selectedDate.value)
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun addSubject(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.addCustomSubject(settings.value.groupId, name)
            onDone()
        }
    }

    fun deleteSubject(subject: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.removeSubject(settings.value.groupId, subject)
            loadDay(settings.value.groupId, _selectedDate.value)
            WidgetUpdater.updateAll(getApplication())
            onDone()
        }
    }

    // --- домашка и оценки ---

    fun saveRecord(
        date: LocalDate,
        subject: String,
        lessonNumber: Int,
        homework: String,
        done: Boolean,
        grade: Int,
    ) {
        viewModelScope.launch {
            repo.saveRecord(
                LessonRecordEntity(
                    groupId = settings.value.groupId,
                    isoDate = date.toString(),
                    subject = subject,
                    lessonNumber = lessonNumber,
                    homework = homework,
                    homeworkDone = done,
                    grade = grade,
                )
            )
            loadDay(settings.value.groupId, _selectedDate.value)
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun updateLayout(layout: ScheduleLayout) {
        viewModelScope.launch { store.update { it.copy(layout = layout) } }
    }

    // --- календарь ---

    /** Открыть или закрыть месяц. При открытии догружаются недостающие недели. */
    fun toggleCalendar() {
        val opening = !_ui.value.calendarOpen
        _ui.value = _ui.value.copy(calendarOpen = opening)
        if (opening) showMonth(YearMonth.from(_selectedDate.value))
    }

    fun showMonth(month: YearMonth) {
        _ui.value = _ui.value.copy(calendarMonth = month)
        val groupId = settings.value.groupId
        if (groupId == 0) return
        viewModelScope.launch {
            // Сначала показываем то, что уже в кеше, и только потом идём в сеть:
            // иначе календарь открывался бы пустым на время запроса.
            loadCounts(groupId, month)
            repo.ensureMonthLoaded(groupId, month.atDay(1))
            loadCounts(groupId, month)
        }
    }

    private suspend fun loadCounts(groupId: Int, month: YearMonth) {
        val counts = repo.lessonCounts(groupId, month.atDay(1), month.atEndOfMonth())
        _ui.value = _ui.value.copy(lessonCounts = _ui.value.lessonCounts + counts)
    }

    data class UiState(
        val day: DaySchedule? = null,
        val notes: Map<String, SubjectNoteEntity> = emptyMap(),
        val loading: Boolean = false,
        val loadingGroups: Boolean = false,
        val error: String? = null,
        val records: Map<RecordKey, LessonRecordEntity> = emptyMap(),
        val weekDays: List<DaySchedule> = emptyList(),
        val weekMonday: LocalDate = ScheduleRepository.mondayOf(LocalDate.now()),
        val rangeRecords: Map<RecordKey, LessonRecordEntity> = emptyMap(),
        val calendarOpen: Boolean = false,
        val calendarMonth: YearMonth = YearMonth.now(),
        val lessonCounts: Map<LocalDate, Int> = emptyMap(),
    )
}
