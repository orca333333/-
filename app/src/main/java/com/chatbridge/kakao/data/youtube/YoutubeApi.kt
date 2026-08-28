package com.chatbridge.kakao.data.youtube

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class ChannelLookupResult(val channelId: String, val title: String, val thumbnailUrl: String)
data class VideoResult(val videoId: String, val title: String, val thumbnailUrl: String, val publishedAt: Long)

/** Thin wrapper around the official YouTube Data API v3 — the user supplies their own API key. */
class YoutubeApi(private val apiKey: String) {
    private val client = OkHttpClient()

    /** Best-effort channel search by name, handle, or URL fragment the user typed. */
    fun findChannel(query: String): ChannelLookupResult? {
        val url = "https://www.googleapis.com/youtube/v3/search".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet")
            .addQueryParameter("type", "channel")
            .addQueryParameter("maxResults", "1")
            .addQueryParameter("q", query)
            .addQueryParameter("key", apiKey)
            .build()

        val json = get(url.toString()) ?: return null
        val item = json.optJSONArray("items")?.optJSONObject(0) ?: return null
        val snippet = item.getJSONObject("snippet")
        return ChannelLookupResult(
            channelId = item.getJSONObject("id").getString("channelId"),
            title = snippet.getString("title"),
            thumbnailUrl = snippet.getJSONObject("thumbnails").getJSONObject("default").getString("url")
        )
    }

    fun uploadsPlaylistId(channelId: String): String? {
        val url = "https://www.googleapis.com/youtube/v3/channels".toHttpUrl().newBuilder()
            .addQueryParameter("part", "contentDetails")
            .addQueryParameter("id", channelId)
            .addQueryParameter("key", apiKey)
            .build()

        val json = get(url.toString()) ?: return null
        return json.optJSONArray("items")?.optJSONObject(0)
            ?.getJSONObject("contentDetails")
            ?.getJSONObject("relatedPlaylists")
            ?.getString("uploads")
    }

    fun latestVideos(playlistId: String, max: Int = 15): List<VideoResult> {
        val url = "https://www.googleapis.com/youtube/v3/playlistItems".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet")
            .addQueryParameter("maxResults", max.toString())
            .addQueryParameter("playlistId", playlistId)
            .addQueryParameter("key", apiKey)
            .build()

        val json = get(url.toString()) ?: return emptyList()
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val snippet = items.optJSONObject(i)?.optJSONObject("snippet") ?: return@mapNotNull null
            val videoId = snippet.optJSONObject("resourceId")?.optString("videoId") ?: return@mapNotNull null
            VideoResult(
                videoId = videoId,
                title = snippet.optString("title"),
                thumbnailUrl = snippet.optJSONObject("thumbnails")
                    ?.optJSONObject("medium")
                    ?.optString("url").orEmpty(),
                publishedAt = parseIso8601(snippet.optString("publishedAt"))
            )
        }
    }

    private fun get(url: String): JSONObject? {
        val request = Request.Builder().url(url).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else JSONObject(response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIso8601(value: String): Long =
        try {
            java.time.Instant.parse(value).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
}
