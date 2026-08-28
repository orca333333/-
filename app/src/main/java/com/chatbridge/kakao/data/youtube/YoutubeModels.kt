package com.chatbridge.kakao.data.youtube

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "youtube_channels")
data class YoutubeChannel(
    @PrimaryKey val channelId: String,
    val title: String,
    val thumbnailUrl: String,
    val uploadsPlaylistId: String
)

@Entity(tableName = "youtube_videos", primaryKeys = ["videoId"])
data class YoutubeVideo(
    val videoId: String,
    val channelId: String,
    val channelTitle: String,
    val title: String,
    val thumbnailUrl: String,
    val publishedAt: Long
)
