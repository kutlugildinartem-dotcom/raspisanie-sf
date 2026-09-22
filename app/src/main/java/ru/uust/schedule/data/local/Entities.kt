package ru.uust.schedule.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Один день расписания. Пары лежат JSON-строкой: их всегда ровно 8,
 * они читаются только целиком, и отдельная таблица дала бы join без выгоды.
 */
@Entity(tableName = "days", primaryKeys = ["groupId", "isoDate"])
data class DayEntity(
    val groupId: Int,
    val isoDate: String,
    val dayName: String,
    val lessonsJson: String,
    val fetchedAt: Long,
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val facultyId: Int,
    val facultyName: String,
)

/**
 * Заметка по предмету. Предмет опознаётся по названию в рамках группы —
 * своего id у него на сайте нет.
 */
@Entity(tableName = "subject_notes", primaryKeys = ["groupId", "subject"], indices = [Index("groupId")])
data class SubjectNoteEntity(
    val groupId: Int,
    val subject: String,
    val note: String = "",
    /** Оттенок 0..360 для метки предмета; -1 — использовать акцент темы. */
    val hue: Int = -1,
    val pinned: Boolean = false,
    /** Предмет добавлен вручную: его нет в расписании, но он должен остаться в списке. */
    val custom: Boolean = false,
    /** Полное имя преподавателя: сайт отдаёт только «Иванов И.И.». */
    val teacherFull: String = "",
    val updatedAt: Long = 0L,
)

/**
 * Что пользователь добавил к конкретной паре конкретного дня: домашка и оценка.
 *
 * Ключ — дата плюс название предмета, а не id занятия с сайта: id меняется при
 * пересборке расписания, и домашка тогда отвязалась бы от пары.
 */
@Entity(tableName = "lesson_records", primaryKeys = ["groupId", "isoDate", "subject"])
data class LessonRecordEntity(
    val groupId: Int,
    val isoDate: String,
    val subject: String,
    val homework: String = "",
    val homeworkDone: Boolean = false,
    /** Оценка 1..5; 0 — не выставлена. */
    val grade: Int = 0,
    val updatedAt: Long = 0L,
) {
    val hasHomework: Boolean get() = homework.isNotBlank()
    val isEmpty: Boolean get() = homework.isBlank() && grade == 0 && !homeworkDone
}
