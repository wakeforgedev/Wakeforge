package com.buddy.wakeforge

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarm_events ORDER BY timeInMillis ASC")
    fun observeAll(): Flow<List<AlarmEvent>>

    @Query("SELECT * FROM alarm_events WHERE isEnabled = 1")
    suspend fun getAllEnabled(): List<AlarmEvent>

    @Query("SELECT * FROM alarm_events WHERE id = :id")
    suspend fun getById(id: Int): AlarmEvent?

    @Insert
    suspend fun insert(event: AlarmEvent): Long

    @Update
    suspend fun update(event: AlarmEvent)

    @Delete
    suspend fun delete(event: AlarmEvent)
}
