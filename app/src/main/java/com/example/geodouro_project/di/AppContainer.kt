package com.example.geodouro_project.di

import android.content.Context
import com.example.geodouro_project.core.network.ConnectivityChecker
import com.example.geodouro_project.data.local.GeodouroDatabase
import com.example.geodouro_project.data.remote.api.INaturalistApiService
import com.example.geodouro_project.data.repository.PlantRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AppContainer {

    @Volatile
    private var repositoryInstance: PlantRepository? = null

    fun providePlantRepository(context: Context): PlantRepository {
        return repositoryInstance ?: synchronized(this) {
            repositoryInstance ?: buildRepository(context.applicationContext)
                .also { repositoryInstance = it }
        }
    }

    private fun buildRepository(appContext: Context): PlantRepository {
        val database = GeodouroDatabase.getInstance(appContext)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.inaturalist.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(INaturalistApiService::class.java)

        return PlantRepository(
            taxonCacheDao = database.taxonCacheDao(),
            observationDao = database.observationDao(),
            apiService = apiService,
            connectivityChecker = ConnectivityChecker(appContext)
        )
    }
}
