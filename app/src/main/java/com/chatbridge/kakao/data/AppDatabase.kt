package com.chatbridge.kakao.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chatbridge.kakao.data.finance.FinanceDao
import com.chatbridge.kakao.data.finance.FinanceSnapshot
import com.chatbridge.kakao.data.news.NewsArticle
import com.chatbridge.kakao.data.news.NewsDao
import com.chatbridge.kakao.data.scrape.ScrapeDao
import com.chatbridge.kakao.data.scrape.ScrapeJob
import com.chatbridge.kakao.data.sns.SnsAccount
import com.chatbridge.kakao.data.sns.SnsDao
import com.chatbridge.kakao.data.sns.SnsPost
import com.chatbridge.kakao.data.youtube.YoutubeChannel
import com.chatbridge.kakao.data.youtube.YoutubeDao
import com.chatbridge.kakao.data.youtube.YoutubeVideo

@Database(
    entities = [
        MonitoredChat::class, ChatMessage::class,
        YoutubeChannel::class, YoutubeVideo::class,
        NewsArticle::class,
        SnsAccount::class, SnsPost::class,
        FinanceSnapshot::class,
        ScrapeJob::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun youtubeDao(): YoutubeDao
    abstract fun newsDao(): NewsDao
    abstract fun snsDao(): SnsDao
    abstract fun financeDao(): FinanceDao
    abstract fun scrapeDao(): ScrapeDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chatbridge.db"
                )
                    // Pre-release app with no installed users yet, so a destructive migration
                    // (rather than a hand-written one) is the simplest way to evolve the schema.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
