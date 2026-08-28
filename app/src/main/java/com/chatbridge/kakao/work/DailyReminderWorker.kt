package com.chatbridge.kakao.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chatbridge.kakao.MainActivity

private const val CHANNEL_ID = "daily_reminders"

/**
 * BankSalad and SNS scraping can't be launched silently from the background (Android blocks a
 * background service from starting another app's activity), so instead this posts a notification;
 * tapping it opens [MainActivity] in the foreground, which is allowed to launch the target app and
 * hand off to [com.chatbridge.kakao.accessibility.BridgeAccessibilityService].
 */
class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE) ?: return Result.failure()
        postReminder(applicationContext, type)
        return Result.success()
    }

    companion object {
        const val KEY_TYPE = "type"
        const val TYPE_BANKSALAD = "banksalad"
        const val TYPE_SNS = "sns"

        fun postReminder(context: Context, type: String) {
            ensureChannel(context)
            val title: String
            val message: String
            val extraKey: String
            when (type) {
                TYPE_BANKSALAD -> {
                    title = "가계부 갱신 준비됨"
                    message = "탭하여 오늘의 뱅크샐러드 가계부를 불러옵니다."
                    extraKey = MainActivity.EXTRA_REFRESH_BANKSALAD
                }
                else -> {
                    title = "SNS 새 게시글 확인"
                    message = "탭하여 등록한 계정의 새 게시글을 불러옵니다."
                    extraKey = MainActivity.EXTRA_REFRESH_SNS
                }
            }

            val contentIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(extraKey, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                type.hashCode(),
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(type.hashCode(), notification)
            }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "일일 갱신 알림", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }
}
