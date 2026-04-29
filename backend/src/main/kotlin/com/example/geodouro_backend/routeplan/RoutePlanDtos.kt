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
    val imagePath: String?,
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
data class SaveRoutePlanRequest(
    val name: String,
    val description: String? = null,
    val startLabel: String? = null,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null
)

data class RoutePlanMutationResponse(
    val success: Boolean,
    val message: String,
    val routePlanId: Int
)

data class RoutePlanStopMutationResponse(
    val success: Boolean,
    val inRoute: Boolean,
    val message: String,
    val routePlanPointId: Int? = null,
    val savedVisitTargetId: Int? = null
)
