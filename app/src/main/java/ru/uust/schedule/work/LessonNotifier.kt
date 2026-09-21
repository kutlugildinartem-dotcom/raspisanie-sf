package ru.uust.schedule.work

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.uust.schedule.MainActivity
import ru.uust.schedule.R
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.repo.ScheduleRepository
import ru.uust.schedule.domain.Lesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Напоминания перед парой.
 *
 * Будильники ставятся только на оставшиеся сегодня пары и перевзводятся при
 * каждом запуске [SyncWorker]. Так расписание может меняться на сайте,
 * а лишние уведомления не приходят.
 */
object LessonNotifier {

    const val CHANNEL_ID = "lesson_reminders"
    private const val REQUEST_BASE = 7000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминания о парах",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Уведомление за несколько минут до начала пары"
            enableVibration(true)
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun rescheduleToday(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { rescheduleNow(context) }
    }

    suspend fun rescheduleNow(context: Context) {
        val store = SettingsStore.get(context)
        val settings = store.current()
        val alarms = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return

        cancelAll(context, alarms)
        if (!settings.notificationsEnabled || settings.groupId == 0) return

        ensureChannel(context)

        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val day = ScheduleRepository.get(context).cachedDay(settings.groupId, today) ?: return

        day.realLessons.forEachIndexed { index, lesson ->
            if (lesson.startMin < 0) return@forEachIndexed
            val fireAt = today.atStartOfDay()
                .plusMinutes((lesson.startMin - settings.notifyMinutesBefore).toLong())
            if (fireAt.isBefore(now)) return@forEachIndexed

            val millis = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val pi = pendingIntent(context, index, lesson, settings.notifyMinutesBefore)

            // Неточного будильника достаточно: напоминание за 15 минут не требует
            // секундной точности и не просит у пользователя SCHEDULE_EXACT_ALARM.
            alarms.set(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun cancelAll(context: Context, alarms: AlarmManager) {
        repeat(MAX_LESSONS) { index ->
            val intent = Intent(context, LessonAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_BASE + index, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                alarms.cancel(pi)
                pi.cancel()
            }
        }
    }

    private fun pendingIntent(
        context: Context,
        index: Int,
        lesson: Lesson,
        minutesBefore: Int,
    ): PendingIntent {
        val intent = Intent(context, LessonAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SUBJECT, lesson.subject)
            putExtra(EXTRA_ROOM, lesson.room)
            putExtra(EXTRA_TIME, lesson.timeRange)
            putExtra(EXTRA_MINUTES, minutesBefore)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_BASE + index, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun show(context: Context, subject: String, room: String, time: String, minutesBefore: Int) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = listOfNotNull(
            time.ifBlank { null },
            room.ifBlank { null },
        ).joinToString(" · ")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_schedule)
            .setContentTitle("Через $minutesBefore мин: $subject")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(subject.hashCode(), notification)
        }
    }

    const val EXTRA_SUBJECT = "subject"
    const val EXTRA_ROOM = "room"
    const val EXTRA_TIME = "time"
    const val EXTRA_MINUTES = "minutes"
    private const val MAX_LESSONS = 8
}

class LessonAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LessonNotifier.show(
            context,
            subject = intent.getStringExtra(LessonNotifier.EXTRA_SUBJECT).orEmpty(),
            room = intent.getStringExtra(LessonNotifier.EXTRA_ROOM).orEmpty(),
            time = intent.getStringExtra(LessonNotifier.EXTRA_TIME).orEmpty(),
            minutesBefore = intent.getIntExtra(LessonNotifier.EXTRA_MINUTES, 15),
        )
    }
}

/** После перезагрузки будильники стираются системой — ставим заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        SyncScheduler.schedule(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { LessonNotifier.rescheduleNow(context) }
            pending.finish()
        }
    }
}
