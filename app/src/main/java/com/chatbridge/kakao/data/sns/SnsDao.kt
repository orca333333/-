package com.chatbridge.kakao.data.sns

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chatbridge.kakao.data.scrape.ScrapeTarget
import kotlinx.coroutines.flow.Flow

@Dao
interface SnsDao {
    @Query("SELECT * FROM sns_accounts ORDER BY platform, handle")
    fun observeAccounts(): Flow<List<SnsAccount>>

    @Query("SELECT * FROM sns_accounts ORDER BY platform, handle")
    suspend fun getAccountsOnce(): List<SnsAccount>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAccount(account: SnsAccount)

    @Query("DELETE FROM sns_accounts WHERE platform = :platform AND handle = :handle")
    suspend fun removeAccount(platform: ScrapeTarget, handle: String)

    @Query("SELECT * FROM sns_posts ORDER BY scrapedAt DESC LIMIT 200")
    fun observeFeed(): Flow<List<SnsPost>>

    @Insert
    suspend fun insertPosts(posts: List<SnsPost>)
}
