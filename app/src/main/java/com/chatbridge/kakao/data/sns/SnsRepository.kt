package com.chatbridge.kakao.data.sns

import android.content.Context
import com.chatbridge.kakao.data.AppDatabase
import com.chatbridge.kakao.data.scrape.ScrapeJob
import com.chatbridge.kakao.data.scrape.ScrapeTarget

class SnsRepository(context: Context) {
    private val snsDao = AppDatabase.getInstance(context).snsDao()
    private val scrapeDao = AppDatabase.getInstance(context).scrapeDao()

    fun observeAccounts() = snsDao.observeAccounts()
    suspend fun getAccountsOnce() = snsDao.getAccountsOnce()
    fun observeFeed() = snsDao.observeFeed()

    suspend fun addAccount(platform: ScrapeTarget, handle: String) =
        snsDao.addAccount(SnsAccount(platform, handle.removePrefix("@").trim()))

    suspend fun removeAccount(platform: ScrapeTarget, handle: String) =
        snsDao.removeAccount(platform, handle)

    /** Queues an accessibility-driven scrape of this account's profile. */
    suspend fun requestRefresh(account: SnsAccount): Long =
        scrapeDao.insert(
            ScrapeJob(
                target = account.platform,
                detail = account.handle,
                requestedAt = System.currentTimeMillis()
            )
        )

    /** Called by the accessibility service once it finishes reading an account's profile screen. */
    suspend fun onScrapeCompleted(platform: ScrapeTarget, handle: String, textBlocks: List<String>) {
        val now = System.currentTimeMillis()
        val posts = textBlocks.take(MAX_POSTS_PER_SCRAPE).map { text ->
            SnsPost(
                platform = platform,
                handle = handle,
                contentPreview = text,
                link = profileUrl(platform, handle),
                scrapedAt = now
            )
        }
        snsDao.insertPosts(posts)
    }

    companion object {
        private const val MAX_POSTS_PER_SCRAPE = 10

        /**
         * Falls back to the account's profile — accessibility scraping can't recover a real
         * per-post permalink since none of these apps render one as on-screen text.
         */
        fun profileUrl(platform: ScrapeTarget, handle: String): String = when (platform) {
            ScrapeTarget.SNS_X -> "https://x.com/$handle"
            ScrapeTarget.SNS_INSTAGRAM -> "https://www.instagram.com/$handle"
            ScrapeTarget.SNS_TIKTOK -> "https://www.tiktok.com/@$handle"
            ScrapeTarget.BANKSALAD -> error("BankSalad has no profile URL")
        }
    }
}
