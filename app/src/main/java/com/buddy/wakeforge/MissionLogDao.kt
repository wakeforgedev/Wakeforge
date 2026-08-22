package com.buddy.wakeforge

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionLogDao {

    @Insert
    suspend fun insert(log: MissionLog)

    @Query("SELECT * FROM mission_logs ORDER BY completedAtMillis DESC")
    fun observeAll(): Flow<List<MissionLog>>
}
