package com.chatbridge.kakao.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDirection(value: MessageDirection): String = value.name

    @TypeConverter
    fun toDirection(value: String): MessageDirection = MessageDirection.valueOf(value)

    @TypeConverter
    fun fromStatus(value: SendStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): SendStatus = SendStatus.valueOf(value)
}
