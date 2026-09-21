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
