package com.chatbridge.kakao.data.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"
private const val MODEL = "claude-3-5-haiku-20241022"

/**
 * Thin wrapper around the Claude Messages API for the small text-generation jobs menus 3 and 5
 * need (title summarization, a short financial status report). Uses the user's own API key from
 * Settings — a blank/missing key means "AI features off", so callers should fall back gracefully
 * rather than treat a null result as an error.
 */
class ClaudeSummarizer(private val apiKey: String) {
    private val client = OkHttpClient()

    fun complete(prompt: String, maxTokens: Int = 512): String? {
        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", maxTokens)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val request = Request.Builder()
            .url(ANTHROPIC_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())
                json.optJSONArray("content")?.optJSONObject(0)?.optString("text")
            }
        } catch (e: IOException) {
            null
        }
    }
}
