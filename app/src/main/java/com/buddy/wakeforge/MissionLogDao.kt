package com.buddy.wakeforge

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionLogDao {

    @Insert
    suspend fun insert(log: MissionLog): Long

    @Update
    suspend fun update(log: MissionLog)

    @Query("SELECT * FROM mission_logs ORDER BY scheduledAtMillis DESC")
    fun observeAll(): Flow<List<MissionLog>>

    /** The most recently rung, not-yet-completed log for this alarm — used to mark it done when the mission is solved. */
    @Query("SELECT * FROM mission_logs WHERE alarmEventId = :alarmEventId AND completed = 0 ORDER BY scheduledAtMillis DESC LIMIT 1")
    suspend fun getLatestOpenLog(alarmEventId: Int): MissionLog?

    /** Everything that rang within a time range (inclusive), for the History screen's month/day views. */
    @Query("SELECT * FROM mission_logs WHERE scheduledAtMillis BETWEEN :startMillis AND :endMillis ORDER BY scheduledAtMillis ASC")
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<MissionLog>>
}
