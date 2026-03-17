package com.example.geodouro_project.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.geodouro_project.data.local.dao.ObservationDao
import com.example.geodouro_project.data.local.dao.TaxonCacheDao
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.local.entity.TaxonCacheEntity

@Database(
    entities = [TaxonCacheEntity::class, ObservationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GeodouroDatabase : RoomDatabase() {

    abstract fun taxonCacheDao(): TaxonCacheDao

    abstract fun observationDao(): ObservationDao

    companion object {
        @Volatile
        private var INSTANCE: GeodouroDatabase? = null

        fun getInstance(context: Context): GeodouroDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GeodouroDatabase::class.java,
                    "geodouro.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
