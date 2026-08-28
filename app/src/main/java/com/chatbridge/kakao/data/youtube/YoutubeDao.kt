package com.chatbridge.kakao.data.youtube

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface YoutubeDao {
    @Query("SELECT * FROM youtube_channels ORDER BY title")
    fun observeChannels(): Flow<List<YoutubeChannel>>

    @Query("SELECT * FROM youtube_channels ORDER BY title")
    suspend fun getChannelsOnce(): List<YoutubeChannel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannel(channel: YoutubeChannel)

    @Query("DELETE FROM youtube_channels WHERE channelId = :channelId")
    suspend fun removeChannel(channelId: String)

    @Query("SELECT * FROM youtube_videos ORDER BY publishedAt DESC LIMIT 100")
    fun observeVideos(): Flow<List<YoutubeVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideos(videos: List<YoutubeVideo>)
}
