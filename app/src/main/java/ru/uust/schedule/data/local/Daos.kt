package ru.uust.schedule.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Upsert
    suspend fun upsertDays(days: List<DayEntity>)

    @Query("SELECT * FROM days WHERE groupId = :groupId AND isoDate = :isoDate LIMIT 1")
    suspend fun day(groupId: Int, isoDate: String): DayEntity?

    @Query("SELECT * FROM days WHERE groupId = :groupId AND isoDate BETWEEN :from AND :to ORDER BY isoDate")
    fun daysBetweenFlow(groupId: Int, from: String, to: String): Flow<List<DayEntity>>

    @Query("SELECT * FROM days WHERE groupId = :groupId AND isoDate BETWEEN :from AND :to ORDER BY isoDate")
    suspend fun daysBetween(groupId: Int, from: String, to: String): List<DayEntity>

    @Query("SELECT * FROM days WHERE groupId = :groupId ORDER BY isoDate")
    fun allDaysFlow(groupId: Int): Flow<List<DayEntity>>

    /** Свежесть кеша: когда последний раз обновляли этот диапазон. */
    @Query("SELECT MIN(fetchedAt) FROM days WHERE groupId = :groupId AND isoDate BETWEEN :from AND :to")
    suspend fun oldestFetchedAt(groupId: Int, from: String, to: String): Long?

    @Query("DELETE FROM days WHERE isoDate < :before")
    suspend fun pruneOlderThan(before: String)
}

@Dao
interface GroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<GroupEntity>)

    @Query("SELECT * FROM groups WHERE facultyId = :facultyId ORDER BY name")
    suspend fun byFaculty(facultyId: Int): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE name LIKE '%' || :q || '%' ORDER BY name LIMIT 50")
    suspend fun search(q: String): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE id = :id LIMIT 1")
    suspend fun byId(id: Int): GroupEntity?

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun count(): Int
}

@Dao
interface NoteDao {

    @Upsert
    suspend fun upsert(note: SubjectNoteEntity)

    @Query("SELECT * FROM subject_notes WHERE groupId = :groupId ORDER BY pinned DESC, subject")
    fun notesFlow(groupId: Int): Flow<List<SubjectNoteEntity>>

    @Query("SELECT * FROM subject_notes WHERE groupId = :groupId")
    suspend fun notes(groupId: Int): List<SubjectNoteEntity>

    @Query("SELECT * FROM subject_notes WHERE groupId = :groupId AND subject = :subject LIMIT 1")
    suspend fun note(groupId: Int, subject: String): SubjectNoteEntity?

    @Query("DELETE FROM subject_notes WHERE groupId = :groupId AND subject = :subject")
    suspend fun delete(groupId: Int, subject: String)
}

@Dao
interface LessonRecordDao {

    @Upsert
    suspend fun upsert(record: LessonRecordEntity)

    @Query("SELECT * FROM lesson_records WHERE groupId = :groupId AND isoDate = :isoDate")
    suspend fun forDate(groupId: Int, isoDate: String): List<LessonRecordEntity>

    @Query("SELECT * FROM lesson_records WHERE groupId = :groupId AND isoDate BETWEEN :from AND :to")
    suspend fun between(groupId: Int, from: String, to: String): List<LessonRecordEntity>

    @Query("SELECT * FROM lesson_records WHERE groupId = :groupId AND isoDate BETWEEN :from AND :to")
    fun betweenFlow(groupId: Int, from: String, to: String): Flow<List<LessonRecordEntity>>

    /** Незакрытая домашка на сегодня и вперёд — для вкладки заданий. */
    @Query(
        "SELECT * FROM lesson_records WHERE groupId = :groupId AND homework != '' " +
            "AND homeworkDone = 0 AND isoDate >= :from ORDER BY isoDate LIMIT 100"
    )
    fun pendingFlow(groupId: Int, from: String): Flow<List<LessonRecordEntity>>

    @Query(
        "DELETE FROM lesson_records WHERE groupId = :groupId AND isoDate = :isoDate " +
            "AND subject = :subject AND lessonNumber = :lessonNumber"
    )
    suspend fun delete(groupId: Int, isoDate: String, subject: String, lessonNumber: Int)
}

@Dao
interface AttachmentDao {

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query(
        "SELECT * FROM homework_attachments WHERE groupId = :groupId AND isoDate = :isoDate " +
            "AND subject = :subject AND lessonNumber = :lessonNumber ORDER BY addedAt"
    )
    suspend fun forLesson(
        groupId: Int,
        isoDate: String,
        subject: String,
        lessonNumber: Int,
    ): List<AttachmentEntity>

    @Query("SELECT * FROM homework_attachments WHERE groupId = :groupId")
    suspend fun all(groupId: Int): List<AttachmentEntity>

    @Query("DELETE FROM homework_attachments WHERE uri = :uri AND groupId = :groupId")
    suspend fun delete(groupId: Int, uri: String)
}
