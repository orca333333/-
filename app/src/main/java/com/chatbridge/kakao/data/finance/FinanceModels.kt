package com.chatbridge.kakao.data.finance

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One BankSalad ledger read. [rawText] is a best-effort dump of every text node visible on the
 * "가계부" screen when it was scraped — BankSalad has no export API, so this is the only signal
 * available. [summary] is an AI-written financial status report built from that dump, when a
 * Claude API key is configured; otherwise it's blank and the raw dump is shown as-is.
 */
@Entity(tableName = "finance_snapshots")
data class FinanceSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    val rawText: String,
    val summary: String
)
