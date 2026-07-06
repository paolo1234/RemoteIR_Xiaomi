package com.irxiaomi.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Type Converters per Room.
 * Gestisce la serializzazione di tipi complessi.
 */
class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? {
        return value?.let { 
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? {
        return date?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }

    @TypeConverter
    fun fromIntArray(value: String?): IntArray? {
        if (value.isNullOrBlank()) return null
        return try {
            value.split(",").map { it.trim().toInt() }.toIntArray()
        } catch (e: NumberFormatException) { null }
    }

    @TypeConverter
    fun intArrayToString(value: IntArray?): String? {
        return value?.joinToString(",")
    }
}
