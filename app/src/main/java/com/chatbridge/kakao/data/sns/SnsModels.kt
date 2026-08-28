package com.chatbridge.kakao.data.sns

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chatbridge.kakao.data.scrape.ScrapeTarget

/** An account the user asked to follow. [platform] reuses [ScrapeTarget]; BANKSALAD never appears here. */
@Entity(tableName = "sns_accounts", primaryKeys = ["platform", "handle"])
data class SnsAccount(
    val platform: ScrapeTarget,
    val handle: String
)

/**
 * A post read off an account's profile screen. Accessibility scraping can't recover a real
 * per-post permalink (the URL isn't rendered as visible text in any of these apps), so [link]
 * falls back to the account's profile page — tapping a card reopens that profile rather than the
 * exact post. See the accessibility service for the scraping heuristic and its limits.
 */
@Entity(tableName = "sns_posts")
data class SnsPost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: ScrapeTarget,
    val handle: String,
    val contentPreview: String,
    val link: String,
    val scrapedAt: Long
)
