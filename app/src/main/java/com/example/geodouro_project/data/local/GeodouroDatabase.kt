package com.example.geodouro_project.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.geodouro_project.data.local.dao.ObservationDao
import com.example.geodouro_project.data.local.dao.TaxonCacheDao
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.local.entity.TaxonCacheEntity

@Database(
    entities = [TaxonCacheEntity::class, ObservationEntity::class],
    version = 5,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE observations RENAME TO observation")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE observation ADD COLUMN imageUrisSerialized TEXT NOT NULL DEFAULT ''")
                database.execSQL(
                    "UPDATE observation SET imageUrisSerialized = imageUri WHERE imageUrisSerialized = '' AND imageUri IS NOT NULL"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE observation ADD COLUMN isPublished INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE observation ADD COLUMN ownerUserId INTEGER")
                database.execSQL("ALTER TABLE observation ADD COLUMN ownerGuestLabel TEXT")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_observation_ownerUserId ON observation(ownerUserId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_observation_ownerGuestLabel ON observation(ownerGuestLabel)")
            }
        }
    }
}
