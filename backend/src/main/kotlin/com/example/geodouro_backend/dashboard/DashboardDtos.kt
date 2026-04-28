package com.example.geodouro_backend.dashboard

data class DashboardStatsResponse(
    val speciesCount: Int,
    val observationCount: Int,
    val manualReviewCount: Int,
    val publicationCount: Int,
    val userCount: Int
)
