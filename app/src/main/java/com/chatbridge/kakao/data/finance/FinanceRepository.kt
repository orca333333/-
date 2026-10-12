package com.chatbridge.kakao.data.finance

import android.content.Context
import com.chatbridge.kakao.data.AppDatabase
import com.chatbridge.kakao.data.ai.ClaudeSummarizer
import com.chatbridge.kakao.data.scrape.ScrapeJob
import com.chatbridge.kakao.data.scrape.ScrapeTarget
import com.chatbridge.kakao.util.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FinanceRepository(private val context: Context) {
    private val financeDao = AppDatabase.getInstance(context).financeDao()
    private val scrapeDao = AppDatabase.getInstance(context).scrapeDao()
    private val securePrefs = SecurePrefs(context)

    fun observeSnapshots() = financeDao.observeSnapshots()

    /** Queues an accessibility-driven read of BankSalad's 가계부 screen. */
    suspend fun requestRefresh(): Long =
        scrapeDao.insert(
            ScrapeJob(target = ScrapeTarget.BANKSALAD, requestedAt = System.currentTimeMillis())
        )

    /** Called by the accessibility service once it finishes reading the ledger screen. */
    suspend fun onScrapeCompleted(rawText: String): Unit = withContext(Dispatchers.IO) {
        val apiKey = securePrefs.claudeApiKey
        val summary = if (!apiKey.isNullOrBlank()) {
            ClaudeSummarizer(apiKey).complete(
                "다음은 뱅크샐러드 가계부 화면에서 읽어온 텍스트 조각들이야. 이걸 바탕으로 오늘 기준 " +
                    "재정 상태(수입/지출/저축 등 파악되는 대로)를 3~5줄로 간단히 요약 보고서 형태로 써줘. " +
                    "숫자가 불명확하면 추측하지 말고 '확인 필요'라고 표시해:\n\n$rawText"
            )?.trim().orEmpty()
        } else {
            ""
        }
        financeDao.insert(
            FinanceSnapshot(capturedAt = System.currentTimeMillis(), rawText = rawText, summary = summary)
        )
    }
}
