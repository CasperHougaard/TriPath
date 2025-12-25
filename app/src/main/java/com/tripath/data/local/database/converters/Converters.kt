package com.tripath.data.local.database.converters

import androidx.room.TypeConverter
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import java.time.LocalDate

/**
 * Room TypeConverters for converting complex types to/from database-compatible types.
 */
class Converters {

    // LocalDate converters - uses epoch days for storage
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? {
        return epochDay?.let { LocalDate.ofEpochDay(it) }
    }

    // WorkoutType converters
    @TypeConverter
    fun fromWorkoutType(type: WorkoutType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toWorkoutType(name: String?): WorkoutType? {
        return name?.let { WorkoutType.valueOf(it) }
    }

    // StrengthFocus converters
    @TypeConverter
    fun fromStrengthFocus(focus: StrengthFocus?): String? {
        return focus?.name
    }

    @TypeConverter
    fun toStrengthFocus(name: String?): StrengthFocus? {
        return name?.let { StrengthFocus.valueOf(it) }
    }

    // Intensity converters
    @TypeConverter
    fun fromIntensity(intensity: Intensity?): String? {
        return intensity?.name
    }

    @TypeConverter
    fun toIntensity(name: String?): Intensity? {
        return name?.let { Intensity.valueOf(it) }
    }
}

