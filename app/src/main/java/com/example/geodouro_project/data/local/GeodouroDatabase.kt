package com.example.geodouro_project.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.geodouro_project.data.local.dao.ObservationDao
import com.example.geodouro_project.data.local.dao.TaxonCacheDao
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.local.entity.TaxonCacheEntity

@Database(
    entities = [TaxonCacheEntity::class, ObservationEntity::class],
    version = 2,
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE observations RENAME TO observation")
            }
        }
    }
}
