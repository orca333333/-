package com.chatbridge.kakao.data.news

import android.content.Context
import com.chatbridge.kakao.data.AppDatabase
import com.chatbridge.kakao.data.ai.ClaudeSummarizer
import com.chatbridge.kakao.util.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NewsRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).newsDao()
    private val securePrefs = SecurePrefs(context)

    fun observeByCategory(category: NewsCategory) = dao.observeByCategory(category)

    /**
     * Pulls every category's RSS feed, keeps only items published up to today 08:00 (the daily
     * cutoff the user asked for), summarizes each title with Claude when an API key is configured,
     * and stores the result. Safe to call from a background worker or a manual "새로고침".
     */
    suspend fun refreshDailyDigest() = withContext(Dispatchers.IO) {
        val cutoff = todayCutoffMillis()
        val windowStart = cutoff - TimeUnit.HOURS.toMillis(24)
        val apiKey = securePrefs.claudeApiKey
        val summarizer = if (!apiKey.isNullOrBlank()) ClaudeSummarizer(apiKey) else null
        val fetchedAt = System.currentTimeMillis()

        val articles = NewsCategory.entries.flatMap { category ->
            val items = runCatching {
                URL(NewsSources.feedUrlFor(category)).openStream().use { RssParser.parse(it) }
            }.getOrDefault(emptyList())

            items
                .filter { it.publishedAt in windowStart..cutoff }
                .take(MAX_ITEMS_PER_CATEGORY)
                .map { item ->
                    NewsArticle(
                        link = item.link,
                        category = category,
                        originalTitle = item.title,
                        summaryTitle = summarizer?.let { summarizeTitle(it, item.title) } ?: item.title,
                        source = item.source,
                        publishedAt = item.publishedAt,
                        fetchedAt = fetchedAt
                    )
                }
        }

        dao.upsertAll(articles)
        dao.pruneOlderThan(fetchedAt - TimeUnit.DAYS.toMillis(3))
    }

    private fun summarizeTitle(summarizer: ClaudeSummarizer, originalTitle: String): String =
        summarizer.complete(
            "다음 뉴스 제목을 핵심만 남겨 한국어로 15자 내외로 간결하게 다시 써줘. " +
                "다른 설명 없이 요약된 제목만 출력해:\n\n$originalTitle",
            maxTokens = 60
        )?.trim()?.ifBlank { null } ?: originalTitle

    private fun todayCutoffMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 8)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis > System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        return calendar.timeInMillis
    }

    private companion object {
        const val MAX_ITEMS_PER_CATEGORY = 15
    }
}
