package com.example.geodouro_project.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.geodouro_project.data.local.entity.CachedSpeciesCatalogEntity

@Dao
interface CachedSpeciesCatalogDao {

    @Upsert
    suspend fun upsertAll(entities: List<CachedSpeciesCatalogEntity>)

    @Query("DELETE FROM cached_species_catalog")
    suspend fun clearAll()

    @Query("SELECT * FROM cached_species_catalog ORDER BY updatedAtEpochMs DESC, scientificName ASC")
    suspend fun getAll(): List<CachedSpeciesCatalogEntity>

    @Query("SELECT * FROM cached_species_catalog WHERE id = :speciesId LIMIT 1")
    suspend fun getById(speciesId: String): CachedSpeciesCatalogEntity?

    @Transaction
    suspend fun replaceAll(entities: List<CachedSpeciesCatalogEntity>) {
        clearAll()
        if (entities.isNotEmpty()) {
            upsertAll(entities)
        }
    }
}
