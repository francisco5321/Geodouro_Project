package com.example.geodouro_backend.routeplan

data class RoutePlanSummaryResponse(
    val routePlanId: Int,
    val name: String,
    val description: String?,
    val startLabel: String?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val stopCount: Int
)

data class RoutePlanDetailResponse(
    val routePlanId: Int,
    val name: String,
    val description: String?,
    val startLabel: String?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val stopCount: Int,
    val stops: List<RoutePlanStopResponse>,
    val routeGeometry: List<RoutePlanCoordinateResponse>
)

data class RoutePlanStopResponse(
    val routePlanPointId: Int,
    val visitOrder: Int,
    val savedVisitTargetId: Int,
    val targetType: String,
    val title: String,
    val subtitle: String?,
    val observationId: Int?,
    val plantSpeciesId: Int?,
    val publicationId: Int?,
    val latitude: Double?,
    val longitude: Double?
)

data class RoutePlanCoordinateResponse(
    val latitude: Double,
    val longitude: Double
)
