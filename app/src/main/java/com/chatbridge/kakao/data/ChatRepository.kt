package com.chatbridge.kakao.data

import android.content.Context

class ChatRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).chatDao()

    fun observeMonitoredChats() = dao.observeMonitoredChats()
    fun observeAllChats() = dao.observeAllChats()
    fun observeMessages(chatName: String) = dao.observeMessages(chatName)

    suspend fun setMonitored(chatName: String, monitored: Boolean) = dao.setMonitored(chatName, monitored)

    /**
     * Called for every KakaoTalk notification. The room name is always recorded so it can be
     * picked from the "select chats" screen, but the message body is only persisted for chats the
     * user already marked as monitored — unrequested chats are never stored beyond their name.
     */
    suspend fun onIncomingNotification(chatName: String, sender: String?, body: String, timestamp: Long) {
        dao.ensureChatExists(chatName)
        if (dao.isMonitored(chatName) != true) return
        dao.touchChat(chatName, body, timestamp)
        dao.insertMessage(
            ChatMessage(
                chatName = chatName,
                sender = sender,
                body = body,
                timestamp = timestamp,
                direction = MessageDirection.INCOMING,
                sendStatus = SendStatus.SENT
            )
        )
    }

    /** Queues an outgoing reply; the accessibility service picks it up and types it into KakaoTalk. */
    suspend fun queueOutgoingMessage(chatName: String, body: String): Long {
        val timestamp = System.currentTimeMillis()
        dao.touchChat(chatName, body, timestamp)
        return dao.insertMessage(
            ChatMessage(
                chatName = chatName,
                sender = null,
                body = body,
                timestamp = timestamp,
                direction = MessageDirection.OUTGOING,
                sendStatus = SendStatus.PENDING
            )
        )
    }

    suspend fun nextPendingOutgoing() = dao.nextPendingOutgoing()
    suspend fun updateStatus(id: Long, status: SendStatus) = dao.updateStatus(id, status)
}
