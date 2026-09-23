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
    /** Предмет скрыт из списка. Нужен для тех, что приходят с сайта и иначе вернулись бы. */
    val hidden: Boolean = false,
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
@Entity(
    tableName = "lesson_records",
    primaryKeys = ["groupId", "isoDate", "subject", "lessonNumber"],
)
data class LessonRecordEntity(
    val groupId: Int,
    val isoDate: String,
    val subject: String,
    /** Номер пары в дне. Без него один предмет дважды за день делил бы одну запись. */
    val lessonNumber: Int,
    val homework: String = "",
    val homeworkDone: Boolean = false,
    /** Оценка 1..5; 0 — не выставлена. */
    val grade: Int = 0,
    /**
     * Срок сдачи в формате yyyy-MM-dd. Пусто — «к следующей паре»:
     * так задают срок чаще всего, и заставлять выбирать дату каждый раз
     * значит мешать там, где ответ и так очевиден.
     */
    val dueDate: String = "",
    val updatedAt: Long = 0L,
) {
    val hasHomework: Boolean get() = homework.isNotBlank()
    val isEmpty: Boolean get() = homework.isBlank() && grade == 0 && !homeworkDone
}

/** Чем однозначно определяется запись: день, предмет и номер пары в этом дне. */
data class RecordKey(val isoDate: String, val subject: String, val lessonNumber: Int)

val LessonRecordEntity.key: RecordKey
    get() = RecordKey(isoDate, subject, lessonNumber)

/**
 * Файл, приложенный к домашнему заданию.
 *
 * Хранится не копия файла, а его content-URI с постоянным разрешением:
 * копия занимала бы место и устаревала бы, стоит автору поправить документ.
 */
@Entity(
    tableName = "homework_attachments",
    primaryKeys = ["groupId", "isoDate", "subject", "lessonNumber", "uri"],
)
data class AttachmentEntity(
    val groupId: Int,
    val isoDate: String,
    val subject: String,
    val lessonNumber: Int,
    val uri: String,
    val name: String,
    val addedAt: Long = 0L,
)
