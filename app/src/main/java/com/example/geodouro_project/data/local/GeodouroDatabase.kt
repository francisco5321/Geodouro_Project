package com.example.geodouro_project.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.geodouro_project.data.local.dao.CachedCommunityPublicationDao
import com.example.geodouro_project.data.local.dao.CachedSpeciesCatalogDao
import com.example.geodouro_project.data.local.dao.ObservationDao
import com.example.geodouro_project.data.local.dao.TaxonCacheDao
import com.example.geodouro_project.data.local.entity.CachedCommunityPublicationEntity
import com.example.geodouro_project.data.local.entity.CachedSpeciesCatalogEntity
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.local.entity.TaxonCacheEntity

@Database(
    entities = [
        TaxonCacheEntity::class,
        ObservationEntity::class,
        CachedSpeciesCatalogEntity::class,
        CachedCommunityPublicationEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class GeodouroDatabase : RoomDatabase() {

    abstract fun taxonCacheDao(): TaxonCacheDao

    abstract fun observationDao(): ObservationDao

    abstract fun cachedSpeciesCatalogDao(): CachedSpeciesCatalogDao

    abstract fun cachedCommunityPublicationDao(): CachedCommunityPublicationDao

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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE observation ADD COLUMN notes TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_species_catalog (
                        id TEXT NOT NULL PRIMARY KEY,
                        scientificName TEXT NOT NULL,
                        commonName TEXT,
                        family TEXT NOT NULL,
                        genus TEXT NOT NULL,
                        imageCount INTEGER NOT NULL,
                        thumbnailUri TEXT,
                        wikipediaUrl TEXT,
                        updatedAtEpochMs INTEGER NOT NULL,
                        cachedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_community_publication (
                        id TEXT NOT NULL PRIMARY KEY,
                        publicationId INTEGER NOT NULL,
                        scientificName TEXT NOT NULL,
                        commonName TEXT,
                        userDisplayName TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        publishedAt TEXT NOT NULL,
                        imageUrl TEXT,
                        cachedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE observation ADD COLUMN requiresManualIdentification INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
