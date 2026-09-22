package ru.uust.schedule.data.repo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.uust.schedule.data.local.AppDatabase
import ru.uust.schedule.data.local.DayEntity
import ru.uust.schedule.data.local.GroupEntity
import ru.uust.schedule.data.local.LessonRecordEntity
import ru.uust.schedule.data.local.SubjectNoteEntity
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.remote.ScheduleApi
import ru.uust.schedule.domain.DaySchedule
import ru.uust.schedule.domain.FACULTIES
import ru.uust.schedule.domain.Group
import ru.uust.schedule.domain.Lesson
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ScheduleRepository(
    context: Context,
    private val api: ScheduleApi = ScheduleApi(),
) {
    private val db = AppDatabase.get(context)
    private val settings = SettingsStore.get(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val lessonsSerializer = ListSerializer(Lesson.serializer())

    // --- чтение ---

    /** Кешированный день. Сети не касается — этим пользуются виджеты. */
    suspend fun cachedDay(groupId: Int, date: LocalDate): DaySchedule? =
        withContext(Dispatchers.IO) {
            db.scheduleDao().day(groupId, date.toString())?.toDomain()
        }

    fun weekFlow(groupId: Int, monday: LocalDate): Flow<List<DaySchedule>> =
        db.scheduleDao()
            .daysBetweenFlow(groupId, monday.toString(), monday.plusDays(6).toString())
            .map { list -> list.map { it.toDomain() } }

    suspend fun cachedWeek(groupId: Int, monday: LocalDate): List<DaySchedule> =
        withContext(Dispatchers.IO) {
            db.scheduleDao()
                .daysBetween(groupId, monday.toString(), monday.plusDays(6).toString())
                .map { it.toDomain() }
        }

    /**
     * Предметы группы: найденные в расписании плюс заведённые вручную.
     *
     * Ручные предметы остаются в списке, даже когда их нет ни в одной неделе
     * кеша — иначе заметка к редкому предмету исчезала бы вместе с ним.
     */
    suspend fun subjectsOf(groupId: Int): List<SubjectSummary> = withContext(Dispatchers.IO) {
        val days = db.scheduleDao().daysBetween(groupId, "0000-00-00", "9999-99-99")
        val fromSchedule = days.flatMap { it.toDomain().realLessons }
            .groupBy { it.subject }
            .map { (subject, lessons) ->
                SubjectSummary(
                    subject = subject,
                    teachers = lessons.mapNotNull { it.teacher.ifBlank { null } }.distinct(),
                    types = lessons.mapNotNull { it.type.ifBlank { null } }.distinct(),
                    rooms = lessons.mapNotNull { it.room.ifBlank { null } }.distinct(),
                    lessonCount = lessons.size,
                    custom = false,
                )
            }

        val known = fromSchedule.mapTo(mutableSetOf()) { it.subject }
        val manual = db.noteDao().notes(groupId)
            .filter { it.custom && it.subject !in known }
            .map { note ->
                SubjectSummary(
                    subject = note.subject,
                    teachers = emptyList(),
                    types = emptyList(),
                    rooms = emptyList(),
                    lessonCount = 0,
                    custom = true,
                )
            }

        (fromSchedule + manual).sortedBy { it.subject.lowercase() }
    }

    /**
     * Сколько пар в каждый день диапазона — для тепловой карты календаря.
     * Дни, которых нет в кеше, в карту не попадают: «нет данных» и «нет пар»
     * это разные вещи, и красить их одинаково нельзя.
     */
    suspend fun lessonCounts(groupId: Int, from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        withContext(Dispatchers.IO) {
            db.scheduleDao().daysBetween(groupId, from.toString(), to.toString())
                .mapNotNull { entity ->
                    val date = runCatching { LocalDate.parse(entity.isoDate) }.getOrNull()
                        ?: return@mapNotNull null
                    date to entity.toDomain().realLessons.size
                }
                .toMap()
        }

    /** Заводит предмет вручную. Возвращает false, если такой уже есть. */
    suspend fun addCustomSubject(groupId: Int, subject: String): Boolean =
        withContext(Dispatchers.IO) {
            val name = subject.trim()
            if (name.isEmpty()) return@withContext false
            if (db.noteDao().note(groupId, name) != null) return@withContext false
            db.noteDao().upsert(
                SubjectNoteEntity(
                    groupId = groupId,
                    subject = name,
                    custom = true,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            true
        }

    // --- синхронизация ---

    /**
     * Тянет недели со смещениями [offsets] и кладёт в кеш.
     * Возвращает число сохранённых дней; при сетевой ошибке бросает исключение,
     * но уже сохранённые недели остаются в базе.
     */
    suspend fun syncWeeks(groupId: Int, offsets: List<Int> = listOf(0, 1)): Int =
        withContext(Dispatchers.IO) {
            var saved = 0
            val now = System.currentTimeMillis()
            for (offset in offsets) {
                val week = api.getWeek(groupId, offset)
                val rows = week.days.map { day ->
                    DayEntity(
                        groupId = groupId,
                        isoDate = day.isoDate,
                        dayName = day.dayName,
                        lessonsJson = json.encodeToString(lessonsSerializer, day.lessons),
                        fetchedAt = now,
                    )
                }
                db.scheduleDao().upsertDays(rows)
                saved += rows.size
            }
            db.scheduleDao().pruneOlderThan(LocalDate.now().minusDays(30).toString())
            saved
        }

    /**
     * Догружает недели, которых не хватает для показа месяца [anyDayOfMonth].
     * Уже закешированные недели не перезапрашиваются: календарь открывают часто,
     * а расписание на прошлый месяц не меняется.
     */
    suspend fun ensureMonthLoaded(groupId: Int, anyDayOfMonth: LocalDate) =
        withContext(Dispatchers.IO) {
            if (groupId == 0) return@withContext
            val first = anyDayOfMonth.withDayOfMonth(1)
            val last = first.plusMonths(1).minusDays(1)
            val thisMonday = mondayOf(LocalDate.now())

            var monday = mondayOf(first)
            val missing = mutableListOf<Int>()
            while (!monday.isAfter(last)) {
                val cached = db.scheduleDao()
                    .daysBetween(groupId, monday.toString(), monday.plusDays(5).toString())
                if (cached.isEmpty()) {
                    missing += ChronoUnit.WEEKS.between(thisMonday, monday).toInt()
                }
                monday = monday.plusWeeks(1)
            }
            if (missing.isNotEmpty()) {
                runCatching { syncWeeks(groupId, missing) }
            }
        }

    /** Нужно ли обновлять: кеш старше [maxAgeMinutes] или пуст. */
    suspend fun isStale(groupId: Int, maxAgeMinutes: Long = 180): Boolean =
        withContext(Dispatchers.IO) {
            val monday = mondayOf(LocalDate.now())
            val oldest = db.scheduleDao()
                .oldestFetchedAt(groupId, monday.toString(), monday.plusDays(13).toString())
                ?: return@withContext true
            ChronoUnit.MINUTES.between(
                java.time.Instant.ofEpochMilli(oldest),
                java.time.Instant.now(),
            ) >= maxAgeMinutes
        }

    // --- справочник групп ---

    suspend fun ensureGroupsLoaded(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!force && db.groupDao().count() > 0) return@withContext
        val all = FACULTIES.flatMap { f ->
            runCatching { api.getGroups(f.id, f.name) }.getOrDefault(emptyList())
        }
        if (all.isNotEmpty()) {
            db.groupDao().insertAll(all.map { GroupEntity(it.id, it.name, it.facultyId, it.facultyName) })
        }
    }

    suspend fun groupsOfFaculty(facultyId: Int): List<Group> = withContext(Dispatchers.IO) {
        db.groupDao().byFaculty(facultyId).map { Group(it.id, it.name, it.facultyId, it.facultyName) }
    }

    suspend fun searchGroups(query: String): List<Group> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        db.groupDao().search(q).map { Group(it.id, it.name, it.facultyId, it.facultyName) }
    }

    suspend fun groupName(groupId: Int): String? = withContext(Dispatchers.IO) {
        db.groupDao().byId(groupId)?.name
    }

    /** Учебные дни диапазона — для ленты, сетки и таймлайна. */
    suspend fun daysBetween(groupId: Int, from: LocalDate, to: LocalDate): List<DaySchedule> =
        withContext(Dispatchers.IO) {
            if (groupId == 0) return@withContext emptyList()
            db.scheduleDao().daysBetween(groupId, from.toString(), to.toString())
                .map { it.toDomain() }
        }

    // --- домашка и оценки ---

    suspend fun recordsFor(groupId: Int, date: LocalDate): Map<String, LessonRecordEntity> =
        withContext(Dispatchers.IO) {
            db.recordDao().forDate(groupId, date.toString()).associateBy { it.subject }
        }

    suspend fun recordsBetween(
        groupId: Int,
        from: LocalDate,
        to: LocalDate,
    ): Map<Pair<String, String>, LessonRecordEntity> = withContext(Dispatchers.IO) {
        db.recordDao().between(groupId, from.toString(), to.toString())
            .associateBy { it.isoDate to it.subject }
    }

    fun pendingHomeworkFlow(groupId: Int): Flow<List<LessonRecordEntity>> =
        db.recordDao().pendingFlow(groupId, LocalDate.now().toString())

    /**
     * Сохраняет домашку и оценку. Пустая запись удаляется, а не хранится:
     * иначе таблица копила бы строки от каждого открытого и закрытого окна.
     */
    suspend fun saveRecord(record: LessonRecordEntity) = withContext(Dispatchers.IO) {
        if (record.isEmpty) {
            db.recordDao().delete(record.groupId, record.isoDate, record.subject)
        } else {
            db.recordDao().upsert(record.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    // --- заметки ---

    fun notesFlow(groupId: Int): Flow<Map<String, SubjectNoteEntity>> =
        db.noteDao().notesFlow(groupId).map { list -> list.associateBy { it.subject } }

    suspend fun note(groupId: Int, subject: String): SubjectNoteEntity? =
        withContext(Dispatchers.IO) { db.noteDao().note(groupId, subject) }

    suspend fun notes(groupId: Int): Map<String, SubjectNoteEntity> =
        withContext(Dispatchers.IO) { db.noteDao().notes(groupId).associateBy { it.subject } }

    suspend fun saveNote(note: SubjectNoteEntity) = withContext(Dispatchers.IO) {
        db.noteDao().upsert(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNote(groupId: Int, subject: String) = withContext(Dispatchers.IO) {
        db.noteDao().delete(groupId, subject)
    }

    private fun DayEntity.toDomain() = DaySchedule(
        isoDate = isoDate,
        dayName = dayName,
        lessons = runCatching { json.decodeFromString(lessonsSerializer, lessonsJson) }
            .getOrDefault(emptyList()),
    )

    companion object {
        fun mondayOf(date: LocalDate): LocalDate =
            date.minusDays((date.dayOfWeek.value - 1).toLong())

        @Volatile private var instance: ScheduleRepository? = null

        fun get(context: Context): ScheduleRepository = instance ?: synchronized(this) {
            instance ?: ScheduleRepository(context.applicationContext).also { instance = it }
        }
    }
}

data class SubjectSummary(
    val subject: String,
    val teachers: List<String>,
    val types: List<String>,
    val rooms: List<String>,
    val lessonCount: Int,
    /** Заведён вручную: такой предмет можно удалить насовсем. */
    val custom: Boolean = false,
)
