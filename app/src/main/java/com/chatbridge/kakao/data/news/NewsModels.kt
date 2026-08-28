package com.chatbridge.kakao.data.news

import androidx.room.Entity

enum class NewsCategory(val displayName: String) {
    POLITICS("정치"),
    ECONOMY("경제"),
    WAR("전쟁"),
    SCIENCE("과학"),
    ENTERTAINMENT("연예"),
    SPORTS("스포츠"),
    WEATHER("날씨")
}

/** [link] is the primary key: articles are naturally deduplicated by URL across daily refreshes. */
@Entity(tableName = "news_articles", primaryKeys = ["link"])
data class NewsArticle(
    val link: String,
    val category: NewsCategory,
    val originalTitle: String,
    val summaryTitle: String,
    val source: String,
    val publishedAt: Long,
    val fetchedAt: Long
)
