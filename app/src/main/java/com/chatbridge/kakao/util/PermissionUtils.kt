package com.chatbridge.kakao.util

import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.chatbridge.kakao.accessibility.KakaoReplyAccessibilityService

object PermissionUtils {

    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${KakaoReplyAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
