package ru.uust.schedule.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DayEntity::class, GroupEntity::class, SubjectNoteEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun groupDao(): GroupDao
    abstract fun noteDao(): NoteDao

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

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "uust_schedule.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
