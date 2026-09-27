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
import ru.uust.schedule.domain.HomeworkItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Напоминание о домашке за 24, 48 и/или 72 часа до срока: раз в день в
 * выбранный час проверяем задания со сроком через 1, 2, 3 дня (какие
 * выбраны в настройках). Нажатие открывает окно задания во вкладке «Задания».
 */
object HomeworkReminder {

    private const val CHANNEL_ID = "homework_reminders"
    private const val REQUEST_ALARM = 7900

    const val EXTRA_SUBJECT = "hw_subject"
    const val EXTRA_LESSON_DATE = "hw_lesson_date"
    const val EXTRA_LESSON_NUMBER = "hw_lesson_number"

    /** Ставит следующий будильник на ближайший выбранный час или снимает его. */
    fun schedule(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { scheduleNow(context) }
    }

    suspend fun scheduleNow(context: Context) {
        val alarms = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        val settings = SettingsStore.get(context).current()
        if (!settings.homeworkReminder) {
            alarms.cancel(pi)
            return
        }

        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(settings.homeworkReminderHour.coerceIn(0, 23), 0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Неточный будильник: разрешения на точные не нужно, сдвиг — минуты.
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi) }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_ALARM,
        Intent(context, HomeworkReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private val sentLock = Any()
    private const val PREFS = "homework_reminders"
    private const val KEY_SENT = "sent"

    /**
     * Несданные задания, до срока которых осталось выбранное число дней.
     *
     * Вызывается и ежедневным будильником, и фоновой задачей раз в полчаса:
     * раньше проверка шла только в сам час напоминания, и задание, добавленное
     * позже этого часа, или пропущенный системой будильник означали, что
     * напоминания не будет вовсе. Что уже отправлено — запоминаем, чтобы
     * каждое напоминание пришло ровно один раз.
     */
    suspend fun notifyDue(context: Context) {
        val settings = SettingsStore.get(context).current()
        if (!settings.homeworkReminder || settings.groupId == 0) return

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        // Неточный будильник может сработать на пару минут раньше часа.
        val start = today.atTime(settings.homeworkReminderHour.coerceIn(0, 23), 0).minusMinutes(5)
        if (now.isBefore(start)) return

        val days = settings.homeworkReminderDays.toSet()
        val items = ScheduleRepository.get(context).homework(settings.groupId).filter { !it.done }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        synchronized(sentLock) {
            val sent = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty().toMutableSet()
            // Ключ заканчивается на «срок|дней» — всё со сроком в прошлом больше не нужно.
            sent.removeAll { key ->
                val parts = key.split('|')
                val due = parts.getOrNull(parts.size - 2)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                due == null || due < today
            }
            items.forEach { item ->
                val left = java.time.temporal.ChronoUnit.DAYS.between(today, item.due).toInt()
                if (left !in days) return@forEach
                val key = "${item.lessonDate}|${item.subject}|${item.lessonNumber}|${item.due}|$left"
                if (sent.add(key)) show(context, item, left)
            }
            prefs.edit().putStringSet(KEY_SENT, sent).apply()
        }
    }

    private fun show(context: Context, item: HomeworkItem, daysLeft: Int) {
        ensureChannel(context)
        val id = "${item.lessonDate}|${item.subject}|${item.lessonNumber}".hashCode()
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_SUBJECT, item.subject)
                .putExtra(EXTRA_LESSON_DATE, item.lessonDate.toString())
                .putExtra(EXTRA_LESSON_NUMBER, item.lessonNumber),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_schedule)
            .setContentTitle("${whenLabel(daysLeft)} домашка: ${item.subject}")
            .setContentText(item.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun whenLabel(daysLeft: Int): String = when (daysLeft) {
        1 -> "Завтра"
        2 -> "Послезавтра"
        else -> "Через $daysLeft дня"
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Домашние задания",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "За 1–3 дня до срока сдачи домашнего задания" }
        )
    }
}

class HomeworkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { HomeworkReminder.notifyDue(context) }
            runCatching { HomeworkReminder.scheduleNow(context) }
            pending.finish()
        }
    }
}
