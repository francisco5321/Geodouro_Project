package com.example.geodouro_project.data.remote

import android.util.Log
import com.example.geodouro_project.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteRoutePlanService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val config: RemoteDbConfig
) {
    fun isConfigured(): Boolean = config.isConfigured()

    fun fetchRoutePlans(userId: Int?, authToken: String?): List<RemoteRoutePlanSummary> {
        if (!isConfigured()) return emptyList()

        val requestBuilder = Request.Builder()
            .url(buildRoutePlansUrl(userId))
            .get()

        authToken?.takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fetch route plans failed code=${response.code} body=$body")
                    return@use emptyList()
                }

                gson.fromJson(
                    body,
                    Array<RemoteRoutePlanSummaryResponse>::class.java
                )?.map { it.toDomain() }.orEmpty()
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to fetch route plans", error)
        }.getOrDefault(emptyList())
    }

    fun fetchRoutePlanDetail(routePlanId: Int, userId: Int?, authToken: String?): RemoteRoutePlanDetail? {
        if (!isConfigured()) return null

        val requestBuilder = Request.Builder()
            .url(buildRoutePlanDetailUrl(routePlanId, userId))
            .get()

        authToken?.takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fetch route plan detail failed code=${response.code} routePlanId=$routePlanId body=$body")
                    return@use null
                }

                gson.fromJson(
                    body,
                    RemoteRoutePlanDetailResponse::class.java
                )?.toDomain()
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to fetch route plan detail $routePlanId", error)
        }.getOrNull()
    }

    private fun buildRoutePlansUrl(userId: Int?): String {
        val base = config.baseUrl.trimEnd('/') + "/api/route-plans"
        return if (userId != null) "$base?userId=$userId" else base
    }

    private fun buildRoutePlanDetailUrl(routePlanId: Int, userId: Int?): String {
        val base = config.baseUrl.trimEnd('/') + "/api/route-plans/$routePlanId"
        return if (userId != null) "$base?userId=$userId" else base
    }

    companion object {
        private const val TAG = "RemoteRoutePlan"
    }
}

data class RemoteRoutePlanSummary(
    val routePlanId: Int,
    val name: String,
    val description: String?,
    val startLabel: String?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val stopCount: Int
)

data class RemoteRoutePlanDetail(
    val routePlanId: Int,
    val name: String,
    val description: String?,
    val startLabel: String?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val stopCount: Int,
    val stops: List<RemoteRoutePlanStop>,
    val routeGeometry: List<RemoteRoutePlanCoordinate>
)

data class RemoteRoutePlanStop(
    val routePlanPointId: Int,
    val visitOrder: Int,
    val savedVisitTargetId: Int,
    val targetType: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val observationId: Int?,
    val plantSpeciesId: Int?,
    val publicationId: Int?,
    val latitude: Double?,
    val longitude: Double?
)

data class RemoteRoutePlanCoordinate(
    val latitude: Double,
    val longitude: Double
)

private data class RemoteRoutePlanSummaryResponse(
    @SerializedName("routePlanId")
    val routePlanId: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("startLabel")
    val startLabel: String?,
    @SerializedName("startLatitude")
    val startLatitude: Double?,
    @SerializedName("startLongitude")
    val startLongitude: Double?,
    @SerializedName("stopCount")
    val stopCount: Int
) {
    fun toDomain(): RemoteRoutePlanSummary {
        return RemoteRoutePlanSummary(
            routePlanId = routePlanId,
            name = name,
            description = description,
            startLabel = startLabel,
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            stopCount = stopCount
        )
    }
}

private data class RemoteRoutePlanDetailResponse(
    @SerializedName("routePlanId")
    val routePlanId: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("startLabel")
    val startLabel: String?,
    @SerializedName("startLatitude")
    val startLatitude: Double?,
    @SerializedName("startLongitude")
    val startLongitude: Double?,
    @SerializedName("stopCount")
    val stopCount: Int,
    @SerializedName("stops")
    val stops: List<RemoteRoutePlanStopResponse>,
    @SerializedName("routeGeometry")
    val routeGeometry: List<RemoteRoutePlanCoordinateResponse>
) {
    fun toDomain(): RemoteRoutePlanDetail {
        return RemoteRoutePlanDetail(
            routePlanId = routePlanId,
            name = name,
            description = description,
            startLabel = startLabel,
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            stopCount = stopCount,
            stops = stops.map { it.toDomain(baseUrl = BuildConfig.BACKEND_BASE_URL) },
            routeGeometry = routeGeometry.map { it.toDomain() }
        )
    }
}

private data class RemoteRoutePlanStopResponse(
    @SerializedName("routePlanPointId")
    val routePlanPointId: Int,
    @SerializedName("visitOrder")
    val visitOrder: Int,
    @SerializedName("savedVisitTargetId")
    val savedVisitTargetId: Int,
    @SerializedName("targetType")
    val targetType: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("subtitle")
    val subtitle: String?,
    @SerializedName("imagePath")
    val imagePath: String?,
    @SerializedName("observationId")
    val observationId: Int?,
    @SerializedName("plantSpeciesId")
    val plantSpeciesId: Int?,
    @SerializedName("publicationId")
    val publicationId: Int?,
    @SerializedName("latitude")
    val latitude: Double?,
    @SerializedName("longitude")
    val longitude: Double?
) {
    fun toDomain(baseUrl: String): RemoteRoutePlanStop {
        return RemoteRoutePlanStop(
            routePlanPointId = routePlanPointId,
            visitOrder = visitOrder,
            savedVisitTargetId = savedVisitTargetId,
            targetType = targetType,
            title = title,
            subtitle = subtitle,
            imageUrl = imagePath?.takeIf { it.isNotBlank() }?.let { resolveRoutePlanImageUrl(baseUrl, it) },
            observationId = observationId,
            plantSpeciesId = plantSpeciesId,
            publicationId = publicationId,
            latitude = latitude,
            longitude = longitude
        )
    }
}

private data class RemoteRoutePlanCoordinateResponse(
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double
) {
    fun toDomain(): RemoteRoutePlanCoordinate {
        return RemoteRoutePlanCoordinate(
            latitude = latitude,
            longitude = longitude
        )
    }
}

private fun resolveRoutePlanImageUrl(baseUrl: String, path: String): String {
    val normalizedPath = path.trim()
    return if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
        normalizedPath
    } else {
        baseUrl.trimEnd('/') + "/uploads/" + normalizedPath.trimStart('/')
    }
}
