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
    val updatedAt: Long = 0L,
)
