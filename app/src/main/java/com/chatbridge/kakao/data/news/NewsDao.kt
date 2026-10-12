package com.chatbridge.kakao.data.news

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Query("SELECT * FROM news_articles WHERE category = :category ORDER BY publishedAt DESC")
    fun observeByCategory(category: NewsCategory): Flow<List<NewsArticle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(articles: List<NewsArticle>)

    @Query("DELETE FROM news_articles WHERE fetchedAt < :olderThan")
    suspend fun pruneOlderThan(olderThan: Long)
}
