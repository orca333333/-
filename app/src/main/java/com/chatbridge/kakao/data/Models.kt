package com.chatbridge.kakao.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageDirection { INCOMING, OUTGOING }

enum class SendStatus { PENDING, SENT, FAILED }

/**
 * A chat surfaced from a KakaoTalk notification. Every chat KakaoTalk notifies about is recorded
 * here (chatName is the notification title, our only way to identify a room), but message bodies
 * are only kept for chats the user has explicitly marked [isMonitored] — see
 * [ChatRepository.onIncomingNotification].
 */
@Entity(tableName = "monitored_chats")
data class MonitoredChat(
    @PrimaryKey val chatName: String,
    val isMonitored: Boolean = false,
    val lastMessagePreview: String = "",
    val lastMessageAt: Long = 0L
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatName: String,
    val sender: String?,
    val body: String,
    val timestamp: Long,
    val direction: MessageDirection,
    val sendStatus: SendStatus = SendStatus.SENT
)
