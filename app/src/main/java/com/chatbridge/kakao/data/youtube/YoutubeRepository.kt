package com.chatbridge.kakao.data.youtube

import android.content.Context
import com.chatbridge.kakao.data.AppDatabase
import com.chatbridge.kakao.util.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YoutubeRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).youtubeDao()
    private val securePrefs = SecurePrefs(context)

    fun observeChannels() = dao.observeChannels()
    fun observeVideos() = dao.observeVideos()

    fun hasApiKey(): Boolean = !securePrefs.youtubeApiKey.isNullOrBlank()

    /** Looks up [query] (name, handle, or URL) and adds the best-matching channel, if any. */
    suspend fun addChannel(query: String): Boolean = withContext(Dispatchers.IO) {
        val api = apiOrNull() ?: return@withContext false
        val channel = api.findChannel(query) ?: return@withContext false
        val uploadsPlaylistId = api.uploadsPlaylistId(channel.channelId) ?: return@withContext false
        dao.upsertChannel(
            YoutubeChannel(
                channelId = channel.channelId,
                title = channel.title,
                thumbnailUrl = channel.thumbnailUrl,
                uploadsPlaylistId = uploadsPlaylistId
            )
        )
        refreshVideosFor(channel.channelId, uploadsPlaylistId)
        true
    }

    suspend fun removeChannel(channelId: String) = dao.removeChannel(channelId)

    /** Re-fetches the latest uploads for every followed channel. */
    suspend fun refreshAll(): Unit = withContext(Dispatchers.IO) {
        dao.getChannelsOnce().forEach { channel ->
            refreshVideosFor(channel.channelId, channel.uploadsPlaylistId, channel.title)
        }
    }

    private suspend fun refreshVideosFor(channelId: String, uploadsPlaylistId: String, channelTitle: String = "") {
        val api = apiOrNull() ?: return
        val videos = api.latestVideos(uploadsPlaylistId).map {
            YoutubeVideo(
                videoId = it.videoId,
                channelId = channelId,
                channelTitle = channelTitle,
                title = it.title,
                thumbnailUrl = it.thumbnailUrl,
                publishedAt = it.publishedAt
            )
        }
        dao.upsertVideos(videos)
    }

    private fun apiOrNull(): YoutubeApi? =
        securePrefs.youtubeApiKey?.takeIf { it.isNotBlank() }?.let { YoutubeApi(it) }
}
