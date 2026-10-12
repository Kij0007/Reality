package com.reality.android.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RealityDao {
    @Query("SELECT * FROM activities ORDER BY id") suspend fun activities(): List<LocalActivityEntity>
    @Query("SELECT * FROM sessions ORDER BY id") suspend fun sessions(): List<LocalSessionEntity>
    @Query("SELECT * FROM session_breaks ORDER BY id") suspend fun breaks(): List<LocalBreakEntity>
    @Query("SELECT * FROM activities WHERE id = :id") suspend fun activity(id: Long): LocalActivityEntity?
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: Long): LocalSessionEntity?
    @Query("SELECT * FROM session_breaks WHERE sessionId = :sessionId ORDER BY id") suspend fun sessionBreaks(sessionId: Long): List<LocalBreakEntity>
    @Insert suspend fun insertActivity(activity: LocalActivityEntity): Long
    @Insert suspend fun insertSession(session: LocalSessionEntity): Long
    @Insert suspend fun insertBreak(sessionBreak: LocalBreakEntity): Long
    @Insert suspend fun insertActivities(activities: List<LocalActivityEntity>)
    @Insert suspend fun insertSessions(sessions: List<LocalSessionEntity>)
    @Insert suspend fun insertBreaks(breaks: List<LocalBreakEntity>)
    @Update suspend fun updateActivity(activity: LocalActivityEntity)
    @Update suspend fun updateSession(session: LocalSessionEntity)
    @Update suspend fun updateBreak(sessionBreak: LocalBreakEntity)
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun deleteSession(id: Long)
    @Query("DELETE FROM session_breaks") suspend fun clearBreaks()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM activities") suspend fun clearActivities()
    @Query("SELECT * FROM local_metadata WHERE id = 1") suspend fun metadata(): LocalMetadataEntity?
    @Query("SELECT timezone FROM local_metadata WHERE id = 1") fun observeTimezone(): Flow<String?>
    @Query("SELECT importGeneration FROM local_metadata WHERE id = 1") fun observeGeneration(): Flow<Long?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMetadata(metadata: LocalMetadataEntity)
    @Transaction suspend fun setTimezone(zone: String) {
        java.time.ZoneId.of(zone)
        putMetadata((metadata() ?: LocalMetadataEntity()).copy(timezone = zone))
    }
}

@Database(entities = [LocalActivityEntity::class, LocalSessionEntity::class, LocalBreakEntity::class, LocalMetadataEntity::class], version = 1, exportSchema = true)
@TypeConverters(LocalConverters::class)
abstract class RealityDatabase : RoomDatabase() {
    abstract fun realityDao(): RealityDao
}
