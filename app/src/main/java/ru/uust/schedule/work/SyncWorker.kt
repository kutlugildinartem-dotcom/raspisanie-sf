package ru.uust.schedule.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.uust.schedule.data.prefs.SettingsStore
import ru.uust.schedule.data.repo.ScheduleRepository
import ru.uust.schedule.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Периодическая задача обслуживания виджетов.
 *
 * Она делает две разные вещи с разной частотой: перерисовывает виджеты каждый
 * запуск (дёшево, без сети — от этого зависит переключение «сегодня/завтра»
 * и подсветка идущей пары) и ходит в сеть только когда кеш протух.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SettingsStore.get(applicationContext)
        val repo = ScheduleRepository.get(applicationContext)
        val settings = store.current()

        if (settings.groupId == 0) return Result.success()

        if (repo.isStale(settings.groupId, maxAgeMinutes = CACHE_TTL_MINUTES)) {
            val ok = runCatching { repo.syncWeeks(settings.groupId, listOf(0, 1)) }.isSuccess
            if (!ok) {
                // Сеть недоступна — виджеты всё равно перерисуем на кеше, а WorkManager повторит.
                WidgetUpdater.updateAllNow(applicationContext)
                return Result.retry()
            }
        }

        WidgetUpdater.updateAllNow(applicationContext)

        if (settings.notificationsEnabled) {
            LessonNotifier.rescheduleToday(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val CACHE_TTL_MINUTES = 180L
    }
}

object SyncScheduler {

    private const val WORK_NAME = "uust_schedule_sync"

    /** Запускается при старте приложения, выборе группы и добавлении виджета. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
