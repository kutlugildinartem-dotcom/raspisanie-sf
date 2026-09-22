package ru.uust.schedule.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DayEntity::class,
        GroupEntity::class,
        SubjectNoteEntity::class,
        LessonRecordEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun groupDao(): GroupDao
    abstract fun noteDao(): NoteDao
    abstract fun recordDao(): LessonRecordDao

    companion object {
        /**
         * Добавляет флаг «предмет заведён вручную».
         *
         * Миграция, а не пересоздание базы: заметки пользователя восстановить
         * будет неоткуда, в расписании на сайте их нет.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE subject_notes ADD COLUMN custom INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** Полное имя преподавателя: на сайте оно всегда сокращено до инициалов. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE subject_notes ADD COLUMN teacherFull TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * Домашние задания и оценки.
         *
         * SQL повторяет схему, которую генерирует Room, дословно — включая
         * отсутствие DEFAULT. Room сверяет структуру таблицы при открытии базы
         * и падает на любом расхождении, даже безобидном.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lesson_records` (" +
                        "`groupId` INTEGER NOT NULL, " +
                        "`isoDate` TEXT NOT NULL, " +
                        "`subject` TEXT NOT NULL, " +
                        "`homework` TEXT NOT NULL, " +
                        "`homeworkDone` INTEGER NOT NULL, " +
                        "`grade` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`groupId`, `isoDate`, `subject`))"
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "uust_schedule.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
