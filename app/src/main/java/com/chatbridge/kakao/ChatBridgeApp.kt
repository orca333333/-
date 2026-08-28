package com.chatbridge.kakao

import android.app.Application
import com.chatbridge.kakao.data.ChatRepository
import com.chatbridge.kakao.data.finance.FinanceRepository
import com.chatbridge.kakao.data.news.NewsRepository
import com.chatbridge.kakao.data.sns.SnsRepository
import com.chatbridge.kakao.data.youtube.YoutubeRepository
import com.chatbridge.kakao.work.Schedulers

class ChatBridgeApp : Application() {
    lateinit var repository: ChatRepository
        private set
    lateinit var youtubeRepository: YoutubeRepository
        private set
    lateinit var newsRepository: NewsRepository
        private set
    lateinit var snsRepository: SnsRepository
        private set
    lateinit var financeRepository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ChatRepository(this)
        youtubeRepository = YoutubeRepository(this)
        newsRepository = NewsRepository(this)
        snsRepository = SnsRepository(this)
        financeRepository = FinanceRepository(this)
        Schedulers.scheduleAll(this)
    }
}
