package com.chatbridge.kakao.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.chatbridge.kakao.ChatBridgeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val KAKAO_PACKAGE = "com.kakao.talk"

/**
 * KakaoTalk has no API to read a user's personal chats, so incoming messages are captured from
 * the notifications KakaoTalk itself posts. Only the room name (notification title) is recorded
 * for every chat; message content is kept only for chats the user marked as monitored, see
 * [com.chatbridge.kakao.data.ChatRepository.onIncomingNotification].
 */
class KakaoNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != KAKAO_PACKAGE) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification.extras
        val chatName = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val rawText = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        val (sender, body) = splitSenderAndBody(rawText)
        val repo = (application as ChatBridgeApp).repository
        scope.launch {
            repo.onIncomingNotification(chatName, sender, body, sbn.postTime)
        }
    }

    // Kakao renders group/open-chat lines as "sender: message" — best-effort split only, since
    // there's no structured field for the sender name.
    private fun splitSenderAndBody(raw: String): Pair<String?, String> {
        val separatorIndex = raw.indexOf(": ")
        if (separatorIndex <= 0) return null to raw
        return raw.substring(0, separatorIndex) to raw.substring(separatorIndex + 2)
    }
}
