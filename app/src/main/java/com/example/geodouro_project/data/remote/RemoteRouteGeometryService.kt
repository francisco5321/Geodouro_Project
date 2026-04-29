package com.example.geodouro_project.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteRouteGeometryService(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    fun fetchRouteGeometry(waypoints: List<RemoteRoutePlanCoordinate>): List<RemoteRoutePlanCoordinate> {
        if (waypoints.size < 2) return emptyList()

        val coordinatesPath = waypoints.joinToString(";") { point ->
            "${point.longitude},${point.latitude}"
        }
        val url = "$ROUTING_BASE_URL/route/v1/driving/$coordinatesPath?overview=full&geometries=geojson"

        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fetch route geometry failed code=${response.code} body=$body")
                    return@use emptyList()
                }

                val route = gson.fromJson(body, OsrmRouteResponse::class.java)
                    ?.routes
                    ?.firstOrNull()
                    ?.geometry
                    ?.coordinates
                    .orEmpty()

                route.mapNotNull { coordinate ->
                    val longitude = coordinate.getOrNull(0) ?: return@mapNotNull null
                    val latitude = coordinate.getOrNull(1) ?: return@mapNotNull null
                    RemoteRoutePlanCoordinate(latitude = latitude, longitude = longitude)
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to fetch route geometry", error)
        }.getOrDefault(emptyList())
    }

    private data class OsrmRouteResponse(
        @SerializedName("routes")
        val routes: List<OsrmRoute>?
    )

    private data class OsrmRoute(
        @SerializedName("geometry")
        val geometry: OsrmGeometry?
    )

    private data class OsrmGeometry(
        @SerializedName("coordinates")
        val coordinates: List<List<Double>>?
    )

    companion object {
        private const val TAG = "RemoteRouteGeometry"
        private const val ROUTING_BASE_URL = "https://router.project-osrm.org"
    }
}
