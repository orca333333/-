package com.chatbridge.kakao.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds user-supplied API keys (YouTube Data API, Claude API) encrypted at rest. These are keys
 * the user pastes in on the Settings screen — never committed to source, never sent anywhere but
 * to the respective API over HTTPS.
 */
class SecurePrefs(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var youtubeApiKey: String?
        get() = prefs.getString(KEY_YOUTUBE, null)
        set(value) = prefs.edit().putString(KEY_YOUTUBE, value).apply()

    var claudeApiKey: String?
        get() = prefs.getString(KEY_CLAUDE, null)
        set(value) = prefs.edit().putString(KEY_CLAUDE, value).apply()

    companion object {
        private const val KEY_YOUTUBE = "youtube_api_key"
        private const val KEY_CLAUDE = "claude_api_key"
    }
}
