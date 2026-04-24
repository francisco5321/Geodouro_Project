package com.example.geodouro_project.di

import android.content.Context
import com.example.geodouro_project.ai.PlantInferenceEngine
import com.example.geodouro_project.BuildConfig
import com.example.geodouro_project.ai.MobileNetV3Classifier
import com.example.geodouro_project.ai.YoloPlantDetector
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
import com.example.geodouro_project.data.remote.RemoteVisitTargetService
import com.example.geodouro_project.data.remote.api.INaturalistApiService
import com.example.geodouro_project.data.repository.AuthRepository
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.data.repository.RoutePlanRepository
import com.example.geodouro_project.data.repository.VisitTargetRepository
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
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
    @Volatile
    private var visitTargetRepositoryInstance: VisitTargetRepository? = null
    @Volatile
    private var classifierInstance: MobileNetV3Classifier? = null
    @Volatile
    private var detectorInstance: YoloPlantDetector? = null
    @Volatile
    private var inferenceEngineInstance: PlantInferenceEngine? = null
    @Volatile
    private var sharedHttpClient: OkHttpClient? = null

    fun provideAuthRepository(context: Context): AuthRepository {
        return authRepositoryInstance ?: synchronized(this) {
            val authHttpClient = provideHttpClient(context.applicationContext)

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

    fun provideMobileNetV3Classifier(context: Context): MobileNetV3Classifier {
        return classifierInstance ?: synchronized(this) {
            classifierInstance ?: MobileNetV3Classifier(context.applicationContext)
                .also { classifierInstance = it }
        }
    }

    fun provideYoloPlantDetector(context: Context): YoloPlantDetector {
        return detectorInstance ?: synchronized(this) {
            detectorInstance ?: YoloPlantDetector(context.applicationContext)
                .also { detectorInstance = it }
        }
    }

    fun providePlantInferenceEngine(context: Context): PlantInferenceEngine {
        return inferenceEngineInstance ?: synchronized(this) {
            inferenceEngineInstance ?: PlantInferenceEngine(
                classifier = provideMobileNetV3Classifier(context.applicationContext),
                detector = provideYoloPlantDetector(context.applicationContext)
            ).also { inferenceEngineInstance = it }
        }
    }

    fun provideRoutePlanRepository(context: Context): RoutePlanRepository {
        return routePlanRepositoryInstance ?: synchronized(this) {
            val httpClient = provideHttpClient(context.applicationContext)

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


    fun provideVisitTargetRepository(context: Context): VisitTargetRepository {
        return visitTargetRepositoryInstance ?: synchronized(this) {
            val httpClient = provideHttpClient(context.applicationContext)

            visitTargetRepositoryInstance ?: VisitTargetRepository(
                remoteVisitTargetService = RemoteVisitTargetService(
                    httpClient = httpClient,
                    gson = Gson(),
                    config = RemoteDbConfig(
                        baseUrl = BuildConfig.BACKEND_BASE_URL,
                        guestLabel = BuildConfig.BACKEND_GUEST_LABEL,
                        defaultUserId = BuildConfig.BACKEND_DEFAULT_USER_ID
                    )
                )
            ).also { visitTargetRepositoryInstance = it }
        }
    }
    private fun buildRepository(appContext: Context): PlantRepository {
        val database = GeodouroDatabase.getInstance(appContext)
        val authRepository = provideAuthRepository(appContext)
        val okHttpClient = provideHttpClient(appContext)

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
            remoteObservationCatalogService = remoteObservationCatalogService,
            inferenceEngine = providePlantInferenceEngine(appContext),
            classifier = provideMobileNetV3Classifier(appContext),
            currentIdentityProvider = authRepository::currentRemoteIdentity
        )
    }

    private fun provideHttpClient(context: Context): OkHttpClient {
        return sharedHttpClient ?: synchronized(this) {
            sharedHttpClient ?: buildHttpClient(context).also { sharedHttpClient = it }
        }
    }

    private fun buildHttpClient(context: Context): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .cache(
                Cache(
                    directory = File(context.cacheDir, "http_cache"),
                    maxSize = 20L * 1024L * 1024L
                )
            )
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

