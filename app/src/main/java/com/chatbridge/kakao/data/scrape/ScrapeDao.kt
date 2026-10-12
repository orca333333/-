package com.chatbridge.kakao.data.scrape

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScrapeDao {

    @Query("SELECT * FROM scrape_jobs WHERE status = 'PENDING' ORDER BY id ASC LIMIT 1")
    suspend fun nextPending(): ScrapeJob?

    @Insert
    suspend fun insert(job: ScrapeJob): Long

    @Query("UPDATE scrape_jobs SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ScrapeStatus)

    @Query("UPDATE scrape_jobs SET status = :status, resultText = :resultText WHERE id = :id")
    suspend fun complete(id: Long, status: ScrapeStatus, resultText: String?)
}
