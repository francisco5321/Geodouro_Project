package com.example.geodouro_project.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.geodouro_project.data.local.entity.ObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {

    @Upsert
    suspend fun upsert(entity: ObservationEntity)

    @Query("SELECT * FROM observations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ObservationEntity?

    @Query("SELECT * FROM observations WHERE syncStatus = :syncStatus ORDER BY capturedAt ASC")
    suspend fun getBySyncStatus(syncStatus: String): List<ObservationEntity>

    @Query(
        "UPDATE observations SET syncStatus = :syncStatus, lastSyncAttemptAt = :syncTimestamp WHERE id = :id"
    )
    suspend fun updateSyncStatus(id: String, syncStatus: String, syncTimestamp: Long)

    @Query("SELECT * FROM observations ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<ObservationEntity>>
}
