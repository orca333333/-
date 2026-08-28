package com.chatbridge.kakao

import android.app.Application
import com.chatbridge.kakao.data.ChatRepository

class ChatBridgeApp : Application() {
    lateinit var repository: ChatRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ChatRepository(this)
    }
}
