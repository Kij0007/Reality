package com.reality.android.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.reality.android.data.remote.dto.ActivityCategory
import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDto
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Entity(tableName = "activities")
data class LocalActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
    val minimumDuration: Int? = null,
    val category: ActivityCategory? = null,
    val active: Boolean? = null,
    val scheduledDays: Set<String>? = null,
    val startDate: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    fun dto() = ActivityDto(id, name, minimumDuration, category, active, scheduledDays, startDate, createdAt, updatedAt)
}

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(entity = LocalActivityEntity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("activityId")],
)
data class LocalSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val startTime: String,
    val endTime: String? = null,
    val duration: Long? = null,
) {
    fun dto() = SessionDto(id, activityId, startTime, endTime, duration)
}

@Entity(
    tableName = "session_breaks",
    foreignKeys = [ForeignKey(entity = LocalSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class LocalBreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startTime: String,
    val endTime: String? = null,
    val duration: Long? = null,
    val description: String? = null,
) {
    fun dto() = BreakDto(id, sessionId, startTime, endTime, duration, description)
}

fun ActivityDto.entity() = LocalActivityEntity(id, name, minimumDuration, category, active, scheduledDays, startDate, createdAt, updatedAt)
fun SessionDto.entity() = LocalSessionEntity(id, activityId, startTime, endTime, duration)
fun BreakDto.entity() = LocalBreakEntity(id, sessionId, startTime, endTime, duration, description)

class LocalConverters {
    @TypeConverter fun categoryToString(value: ActivityCategory?): String? = value?.name
    @TypeConverter fun stringToCategory(value: String?): ActivityCategory? = value?.let(ActivityCategory::valueOf)
    @TypeConverter fun daysToString(value: Set<String>?): String? = value?.let { Json.encodeToString(SetSerializer(String.serializer()), it) }
    @TypeConverter fun stringToDays(value: String?): Set<String>? = value?.let { Json.decodeFromString(SetSerializer(String.serializer()), it) }
}

@Entity(tableName = "local_metadata")
data class LocalMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val timezone: String = "Asia/Kolkata",
    val importGeneration: Long = 0,
)
