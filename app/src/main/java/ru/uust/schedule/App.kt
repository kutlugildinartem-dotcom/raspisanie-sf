package ru.uust.schedule

import android.app.Application
import ru.uust.schedule.work.LessonNotifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LessonNotifier.ensureChannel(this)
        ru.uust.schedule.work.HomeworkReminder.schedule(this)
    }
}
