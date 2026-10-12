package com.chatbridge.kakao.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Schedulers {

    fun scheduleAll(context: Context) {
        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniquePeriodicWork(
            "news_daily_digest",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NewsRefreshWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(8, 5), TimeUnit.MILLISECONDS)
                .build()
        )

        workManager.enqueueUniquePeriodicWork(
            "banksalad_daily_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(9, 0), TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder().putString(DailyReminderWorker.KEY_TYPE, DailyReminderWorker.TYPE_BANKSALAD).build()
                )
                .build()
        )

        workManager.enqueueUniquePeriodicWork(
            "sns_daily_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(9, 10), TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder().putString(DailyReminderWorker.KEY_TYPE, DailyReminderWorker.TYPE_SNS).build()
                )
                .build()
        )
    }

    // WorkManager can't guarantee an exact wall-clock time, but a target-aligned initial delay
    // keeps each periodic run close to its intended hour instead of drifting from first app launch.
    private fun delayUntil(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
