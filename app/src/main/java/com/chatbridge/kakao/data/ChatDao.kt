package com.chatbridge.kakao.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM monitored_chats ORDER BY chatName COLLATE NOCASE")
    fun observeAllChats(): Flow<List<MonitoredChat>>

    @Query("SELECT * FROM monitored_chats WHERE isMonitored = 1 ORDER BY lastMessageAt DESC")
    fun observeMonitoredChats(): Flow<List<MonitoredChat>>

    @Query("SELECT * FROM chat_messages WHERE chatName = :chatName ORDER BY timestamp ASC")
    fun observeMessages(chatName: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sendStatus = 'PENDING' ORDER BY id ASC LIMIT 1")
    suspend fun nextPendingOutgoing(): ChatMessage?

    @Query(
        "INSERT OR IGNORE INTO monitored_chats (chatName, isMonitored, lastMessagePreview, lastMessageAt) " +
            "VALUES (:chatName, 0, '', 0)"
    )
    suspend fun ensureChatExists(chatName: String)

    @Query("SELECT isMonitored FROM monitored_chats WHERE chatName = :chatName")
    suspend fun isMonitored(chatName: String): Boolean?

    @Query("UPDATE monitored_chats SET isMonitored = :monitored WHERE chatName = :chatName")
    suspend fun setMonitored(chatName: String, monitored: Boolean)

    @Query("UPDATE monitored_chats SET lastMessagePreview = :preview, lastMessageAt = :timestamp WHERE chatName = :chatName")
    suspend fun touchChat(chatName: String, preview: String, timestamp: Long)

    @Insert
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("UPDATE chat_messages SET sendStatus = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SendStatus)
}
