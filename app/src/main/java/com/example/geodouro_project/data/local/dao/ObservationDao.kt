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

    @Query("SELECT * FROM observation WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ObservationEntity?

    @Query("SELECT * FROM observation WHERE syncStatus = :syncStatus ORDER BY capturedAt ASC")
    suspend fun getBySyncStatus(syncStatus: String): List<ObservationEntity>

    @Query("SELECT * FROM observation WHERE syncStatus IN (:syncStatuses) ORDER BY capturedAt ASC")
    suspend fun getBySyncStatuses(syncStatuses: List<String>): List<ObservationEntity>

    @Query(
        "UPDATE observation SET syncStatus = :syncStatus, lastSyncAttemptAt = :syncTimestamp WHERE id = :id"
    )
    suspend fun updateSyncStatus(id: String, syncStatus: String, syncTimestamp: Long)

    @Query("UPDATE observation SET isPublished = :isPublished WHERE id = :id")
    suspend fun updatePublicationStatus(id: String, isPublished: Boolean)

    @Query("SELECT * FROM observation ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observation WHERE isPublished = 1 ORDER BY capturedAt DESC")
    fun observePublished(): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observation WHERE isPublished = 1 ORDER BY capturedAt DESC")
    suspend fun getPublished(): List<ObservationEntity>
}
