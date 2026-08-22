package com.buddy.riseup

import androidx.room.TypeConverter

/**
 * Room doesn't persist enums natively — these converters store them as their
 * String name(), which is what AppDatabase's @TypeConverters points at.
 */
class Converters {

    @TypeConverter
    fun fromCategory(value: Category): String = value.name

    @TypeConverter
    fun toCategory(value: String): Category = Category.valueOf(value)

    @TypeConverter
    fun fromMissionType(value: MissionType): String = value.name

    @TypeConverter
    fun toMissionType(value: String): MissionType = MissionType.valueOf(value)
}
