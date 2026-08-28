package com.chatbridge.kakao.data

import androidx.room.TypeConverter
import com.chatbridge.kakao.data.news.NewsCategory
import com.chatbridge.kakao.data.scrape.ScrapeStatus
import com.chatbridge.kakao.data.scrape.ScrapeTarget

class Converters {
    @TypeConverter
    fun fromDirection(value: MessageDirection): String = value.name

    @TypeConverter
    fun toDirection(value: String): MessageDirection = MessageDirection.valueOf(value)

    @TypeConverter
    fun fromStatus(value: SendStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): SendStatus = SendStatus.valueOf(value)

    @TypeConverter
    fun fromNewsCategory(value: NewsCategory): String = value.name

    @TypeConverter
    fun toNewsCategory(value: String): NewsCategory = NewsCategory.valueOf(value)

    @TypeConverter
    fun fromScrapeTarget(value: ScrapeTarget): String = value.name

    @TypeConverter
    fun toScrapeTarget(value: String): ScrapeTarget = ScrapeTarget.valueOf(value)

    @TypeConverter
    fun fromScrapeStatus(value: ScrapeStatus): String = value.name

    @TypeConverter
    fun toScrapeStatus(value: String): ScrapeStatus = ScrapeStatus.valueOf(value)
}
