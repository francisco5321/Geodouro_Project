package com.example.geodouro_project.data.remote.api

import com.example.geodouro_project.data.remote.model.InaturalistTaxaResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface INaturalistApiService {

    @GET("v1/taxa")
    suspend fun searchTaxa(
        @Query("q") speciesName: String
    ): InaturalistTaxaResponse
}
