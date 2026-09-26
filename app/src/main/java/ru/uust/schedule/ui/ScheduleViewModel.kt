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
import ru.uust.schedule.data.local.AttachmentEntity
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
import ru.uust.schedule.domain.HomeworkItem
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
        viewModelScope.launch { UpdateManager.check(getApplication(), silent) }
    }

    fun installUpdate(release: ReleaseInfo) {
        viewModelScope.launch { UpdateManager.downloadAndInstall(getApplication(), release) }
    }

    fun dismissUpdate() = UpdateManager.dismiss(getApplication())

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

    fun updateHomeworkReminder(enabled: Boolean, hour: Int) {
        viewModelScope.launch {
            store.update { it.copy(homeworkReminder = enabled, homeworkReminderHour = hour.coerceIn(0, 23)) }
            ru.uust.schedule.work.HomeworkReminder.scheduleNow(getApplication())
        }
    }

    /** Включает или выключает напоминание за [days] дней до срока (1, 2 или 3). */
    fun toggleHomeworkReminderDays(days: Int) {
        viewModelScope.launch {
            store.update { s ->
                val set = s.homeworkReminderDays.toMutableSet()
                if (!set.add(days)) set.remove(days)
                // Совсем без вариантов напоминание теряет смысл — для этого есть тумблер.
                if (set.isEmpty()) s else s.copy(homeworkReminderDays = set.sorted())
            }
        }
    }

    fun updateShowTimeRange(show: Boolean) {
        viewModelScope.launch {
            store.update { it.copy(showTimeRange = show) }
            WidgetUpdater.updateAll(getApplication())
        }
    }

    /** Задание, которое нужно открыть по нажатию на уведомление. */
    data class HomeworkTarget(val subject: String, val lessonDate: LocalDate, val lessonNumber: Int)

    private val _openHomework = kotlinx.coroutines.flow.MutableStateFlow<HomeworkTarget?>(null)
    val openHomework: kotlinx.coroutines.flow.StateFlow<HomeworkTarget?> = _openHomework

    fun requestOpenHomework(target: HomeworkTarget) {
        _openHomework.value = target
    }

    fun consumeOpenHomework() {
        _openHomework.value = null
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
        dueDate: String = "",
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
                    dueDate = dueDate,
                )
            )
            loadDay(settings.value.groupId, _selectedDate.value)
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun loadHomework(onResult: (List<HomeworkItem>) -> Unit) {
        viewModelScope.launch { onResult(repo.homework(settings.value.groupId)) }
    }

    /** Галочка в списке заданий: меняем только признак выполнения, остальное не трогаем. */
    fun toggleHomeworkDone(item: HomeworkItem, onDone: () -> Unit) {
        viewModelScope.launch {
            val groupId = settings.value.groupId
            val existing = repo.recordsFor(groupId, item.lessonDate)[
                RecordKey(item.lessonDate.toString(), item.subject, item.lessonNumber)
            ] ?: return@launch

            repo.saveRecord(existing.copy(homeworkDone = !existing.homeworkDone))
            loadDay(groupId, _selectedDate.value)
            WidgetUpdater.updateAll(getApplication())
            onDone()
        }
    }

    /** Даты двух ближайших пар по предмету — для подсказок «к следующей» / «через пару». */
    /**
     * Куда записать новое задание по предмету из вкладки «Предметы».
     *
     * Обычно — на последнюю прошедшую пару. Если там уже есть задание, новое
     * не должно его затирать: тогда берём отдельную запись на сегодня
     * (номер 0 — «без пары»), она так же попадает в «Задания» и под сегодняшнюю
     * пару по этому предмету, если она есть.
     */
    fun openSubjectHomework(
        subject: String,
        onResult: (LocalDate, ru.uust.schedule.domain.Lesson, LessonRecordEntity?) -> Unit,
    ) {
        viewModelScope.launch {
            val groupId = settings.value.groupId
            val today = LocalDate.now()
            val ref = repo.referenceLesson(groupId, subject, today)

            if (ref != null) {
                val (date, lesson) = ref
                val records = repo.recordsFor(groupId, date)
                val existing = records[RecordKey(date.toString(), subject, lesson.number)]
                    ?: records[RecordKey(date.toString(), subject, 0)]
                if (existing == null || existing.homework.isBlank()) {
                    onResult(date, lesson, existing)
                    return@launch
                }
            }

            val blank = ru.uust.schedule.domain.Lesson(
                number = 0, type = "", subject = subject, room = "", teacher = "",
            )
            val todayRecord = repo.recordsFor(groupId, today)[RecordKey(today.toString(), subject, 0)]
            onResult(today, blank, todayRecord)
        }
    }

    fun loadUpcomingLessonDates(subject: String, after: LocalDate, onResult: (List<LocalDate>) -> Unit) {
        viewModelScope.launch {
            onResult(repo.upcomingLessonDates(settings.value.groupId, subject, after))
        }
    }

    fun loadAttachments(date: LocalDate, subject: String, lessonNumber: Int) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                attachments = repo.attachments(settings.value.groupId, date, subject, lessonNumber),
            )
        }
    }

    fun attachFile(
        date: LocalDate,
        subject: String,
        lessonNumber: Int,
        uri: String,
        name: String,
    ) {
        viewModelScope.launch {
            repo.addAttachment(
                AttachmentEntity(
                    groupId = settings.value.groupId,
                    isoDate = date.toString(),
                    subject = subject,
                    lessonNumber = lessonNumber,
                    uri = uri,
                    name = name,
                )
            )
            loadAttachments(date, subject, lessonNumber)
        }
    }

    fun detachFile(date: LocalDate, subject: String, lessonNumber: Int, uri: String) {
        viewModelScope.launch {
            repo.removeAttachment(settings.value.groupId, uri)
            loadAttachments(date, subject, lessonNumber)
        }
    }

    fun updateWidgetTextScale(scale: Float) {
        viewModelScope.launch {
            store.update { it.copy(widgetTextScale = scale) }
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
        val attachments: List<AttachmentEntity> = emptyList(),
        val calendarOpen: Boolean = false,
        val calendarMonth: YearMonth = YearMonth.now(),
        val lessonCounts: Map<LocalDate, Int> = emptyMap(),
    )
}
