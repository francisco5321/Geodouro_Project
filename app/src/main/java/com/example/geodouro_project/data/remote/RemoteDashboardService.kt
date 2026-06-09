package com.example.geodouro_project.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteDashboardService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val config: RemoteDbConfig
) {

    fun isConfigured(): Boolean = config.isConfigured()

    fun fetchStats(): RemoteDashboardStats? {
        if (!isConfigured()) {
            return null
        }

        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/api/dashboard/stats")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fetch dashboard stats failed code=${response.code}")
                    return@use null
                }

                gson.fromJson(response.body?.string().orEmpty(), RemoteDashboardStatsResponse::class.java)
                    ?.toDomain()
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to fetch dashboard stats", error)
        }.getOrNull()
    }

    private fun RemoteDashboardStatsResponse.toDomain(): RemoteDashboardStats {
        return RemoteDashboardStats(
            speciesCount = speciesCount,
            observationCount = observationCount,
            publicationCount = publicationCount
        )
    }

    companion object {
        private const val TAG = "RemoteDashboard"
    }
}

data class RemoteDashboardStats(
    val speciesCount: Int,
    val observationCount: Int,
    val publicationCount: Int
)

private data class RemoteDashboardStatsResponse(
    @SerializedName("speciesCount")
    val speciesCount: Int,
    @SerializedName("observationCount")
    val observationCount: Int,
    @SerializedName("publicationCount")
    val publicationCount: Int
)
