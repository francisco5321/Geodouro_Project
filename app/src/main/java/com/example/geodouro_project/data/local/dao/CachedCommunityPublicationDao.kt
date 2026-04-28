package com.example.geodouro_project.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.geodouro_project.data.local.entity.CachedCommunityPublicationEntity

@Dao
interface CachedCommunityPublicationDao {

    @Upsert
    suspend fun upsertAll(entities: List<CachedCommunityPublicationEntity>)

    @Query("DELETE FROM cached_community_publication")
    suspend fun clearAll()

    @Query("SELECT * FROM cached_community_publication ORDER BY publishedAt DESC")
    suspend fun getAll(): List<CachedCommunityPublicationEntity>

    @Transaction
    suspend fun replaceAll(entities: List<CachedCommunityPublicationEntity>) {
        clearAll()
        if (entities.isNotEmpty()) {
            upsertAll(entities)
        }
    }
}
