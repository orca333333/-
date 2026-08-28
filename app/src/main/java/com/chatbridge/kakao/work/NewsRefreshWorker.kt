package com.chatbridge.kakao.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chatbridge.kakao.ChatBridgeApp

class NewsRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            (applicationContext as ChatBridgeApp).newsRepository.refreshDailyDigest()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
}
