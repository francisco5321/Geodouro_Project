package com.example.geodouro_project.di

import android.content.Context
import com.example.geodouro_project.BuildConfig
import com.example.geodouro_project.core.network.ConnectivityChecker
import com.example.geodouro_project.data.local.AuthSessionStorage
import com.example.geodouro_project.data.local.GeodouroDatabase
import com.example.geodouro_project.data.remote.RemoteAuthService
import com.example.geodouro_project.data.remote.RemoteDbConfig
import com.example.geodouro_project.data.remote.RemoteObservationCatalogService
import com.example.geodouro_project.data.remote.RemoteObservationSyncService
import com.example.geodouro_project.data.remote.RemotePublicationService
import com.example.geodouro_project.data.remote.RemoteRoutePlanService
import com.example.geodouro_project.data.remote.RemoteSpeciesService
import com.example.geodouro_project.data.remote.api.INaturalistApiService
import com.example.geodouro_project.data.repository.AuthRepository
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.data.repository.RoutePlanRepository
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AppContainer {

    @Volatile
    private var repositoryInstance: PlantRepository? = null
    @Volatile
    private var authRepositoryInstance: AuthRepository? = null
    @Volatile
    private var routePlanRepositoryInstance: RoutePlanRepository? = null

    fun provideAuthRepository(context: Context): AuthRepository {
        return authRepositoryInstance ?: synchronized(this) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val authHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()

            authRepositoryInstance ?: AuthRepository(
                sessionStorage = AuthSessionStorage(context.applicationContext),
                remoteAuthService = RemoteAuthService(
                    httpClient = authHttpClient,
                    gson = Gson(),
                    config = RemoteDbConfig(
                        baseUrl = BuildConfig.BACKEND_BASE_URL,
                        guestLabel = BuildConfig.BACKEND_GUEST_LABEL,
                        defaultUserId = BuildConfig.BACKEND_DEFAULT_USER_ID
                    )
                ),
                fallbackAuthenticatedUserId = BuildConfig.BACKEND_DEFAULT_USER_ID,
                guestLabelPrefix = BuildConfig.BACKEND_GUEST_LABEL
            ).also { authRepositoryInstance = it }
        }
    }

    fun providePlantRepository(context: Context): PlantRepository {
        return repositoryInstance ?: synchronized(this) {
            repositoryInstance ?: buildRepository(context.applicationContext)
                .also { repositoryInstance = it }
        }
    }

    fun provideRoutePlanRepository(context: Context): RoutePlanRepository {
        return routePlanRepositoryInstance ?: synchronized(this) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val httpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()

            routePlanRepositoryInstance ?: RoutePlanRepository(
                remoteRoutePlanService = RemoteRoutePlanService(
                    httpClient = httpClient,
                    gson = Gson(),
                    config = RemoteDbConfig(
                        baseUrl = BuildConfig.BACKEND_BASE_URL,
                        guestLabel = BuildConfig.BACKEND_GUEST_LABEL,
                        defaultUserId = BuildConfig.BACKEND_DEFAULT_USER_ID
                    )
                )
            ).also { routePlanRepositoryInstance = it }
        }
    }

    private fun buildRepository(appContext: Context): PlantRepository {
        val database = GeodouroDatabase.getInstance(appContext)
        val authRepository = provideAuthRepository(appContext)

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
        val remoteDbSyncService = RemoteObservationSyncService(
            appContext = appContext,
            httpClient = okHttpClient,
            gson = Gson(),
            config = RemoteDbConfig(
                baseUrl = BuildConfig.BACKEND_BASE_URL,
                guestLabel = BuildConfig.BACKEND_GUEST_LABEL,
                defaultUserId = BuildConfig.BACKEND_DEFAULT_USER_ID
            ),
            currentIdentityProvider = authRepository::currentRemoteIdentity
        )
        val remotePublicationService = RemotePublicationService(
            httpClient = okHttpClient,
            gson = Gson(),
            config = RemoteDbConfig(
                baseUrl = BuildConfig.BACKEND_BASE_URL,
                guestLabel = BuildConfig.BACKEND_GUEST_LABEL,
                defaultUserId = BuildConfig.BACKEND_DEFAULT_USER_ID
            ),
            currentIdentityProvider = authRepository::currentRemoteIdentity
        )
        val remoteSpeciesService = RemoteSpeciesService(
            httpClient = okHttpClient,
            gson = Gson(),
            config = RemoteDbConfig(
                baseUrl = BuildConfig.BACKEND_BASE_URL,
                guestLabel = BuildConfig.BACKEND_GUEST_LABEL,
                defaultUserId = BuildConfig.BACKEND_DEFAULT_USER_ID
            )
        )
        val remoteObservationCatalogService = RemoteObservationCatalogService(
            httpClient = okHttpClient,
            gson = Gson(),
            config = RemoteDbConfig(
                baseUrl = BuildConfig.BACKEND_BASE_URL,
                guestLabel = BuildConfig.BACKEND_GUEST_LABEL,
                defaultUserId = BuildConfig.BACKEND_DEFAULT_USER_ID
            ),
            currentIdentityProvider = authRepository::currentRemoteIdentity
        )

        return PlantRepository(
            appContext = appContext,
            taxonCacheDao = database.taxonCacheDao(),
            observationDao = database.observationDao(),
            apiService = apiService,
            connectivityChecker = ConnectivityChecker(appContext),
            imageHttpClient = okHttpClient,
            remoteObservationSyncService = remoteDbSyncService,
            remotePublicationService = remotePublicationService,
            remoteSpeciesService = remoteSpeciesService,
            remoteObservationCatalogService = remoteObservationCatalogService
        )
    }
}
