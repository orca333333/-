package com.chatbridge.kakao.data.scrape

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ScrapeTarget(val packageName: String) {
    BANKSALAD("com.rainist.banksalad2"),
    SNS_X("com.twitter.android"),
    SNS_INSTAGRAM("com.instagram.android"),
    SNS_TIKTOK("com.zhiliaoapp.musically")
}

enum class ScrapeStatus { PENDING, RUNNING, DONE, FAILED }

/**
 * A single "open the target app and read its screen" job for the accessibility service. [detail]
 * carries the SNS handle to open (null for BankSalad, which has only one screen to read).
 */
@Entity(tableName = "scrape_jobs")
data class ScrapeJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val target: ScrapeTarget,
    val detail: String? = null,
    val status: ScrapeStatus = ScrapeStatus.PENDING,
    val requestedAt: Long,
    val resultText: String? = null
)
