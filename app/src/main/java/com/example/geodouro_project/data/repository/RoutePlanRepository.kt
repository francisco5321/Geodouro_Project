package com.example.geodouro_project.data.repository

import com.example.geodouro_project.data.remote.RemoteRoutePlanCoordinate
import com.example.geodouro_project.data.remote.RemoteRoutePlanDetail
import com.example.geodouro_project.data.remote.RemoteRouteGeometryService
import com.example.geodouro_project.data.remote.RemoteRoutePlanService
import com.example.geodouro_project.data.remote.RemoteRoutePlanStop
import com.example.geodouro_project.data.remote.RemoteRoutePlanSummary
import com.example.geodouro_project.domain.model.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoutePlanRepository(
    private val remoteRoutePlanService: RemoteRoutePlanService,
    private val remoteRouteGeometryService: RemoteRouteGeometryService
) {
    suspend fun fetchRoutePlans(session: SessionState.Authenticated): List<RoutePlanSummary> = withContext(Dispatchers.IO) {
        remoteRoutePlanService.fetchRoutePlans(
            userId = session.userId,
            authToken = session.authToken
        )
            .map { it.toDomain() }
            .filter { it.stopCount > 0 }
    }

    suspend fun fetchRoutePlanDetail(
        routePlanId: Int,
        session: SessionState.Authenticated
    ): RoutePlanDetail? = withContext(Dispatchers.IO) {
        val detail = remoteRoutePlanService.fetchRoutePlanDetail(
            routePlanId = routePlanId,
            userId = session.userId,
            authToken = session.authToken
        )?.toDomain() ?: return@withContext null

        if (detail.routeGeometry.isNotEmpty()) {
            return@withContext detail
        }

        val routedGeometry = remoteRouteGeometryService.fetchRouteGeometry(
            detail.toRoutingWaypoints()
                .map { point ->
                    RemoteRoutePlanCoordinate(
                        latitude = point.latitude,
                        longitude = point.longitude
                    )
                }
        ).map { coordinate ->
            RoutePlanCoordinate(
                latitude = coordinate.latitude,
                longitude = coordinate.longitude
            )
        }

        if (routedGeometry.isNotEmpty()) {
            detail.copy(routeGeometry = routedGeometry)
        } else {
            detail
        }
    }

    data class RoutePlanSummary(
        val routePlanId: Int,
        val name: String,
        val description: String?,
        val startLabel: String?,
        val startLatitude: Double?,
        val startLongitude: Double?,
        val stopCount: Int
    )

    data class RoutePlanDetail(
        val routePlanId: Int,
        val name: String,
        val description: String?,
        val startLabel: String?,
        val startLatitude: Double?,
        val startLongitude: Double?,
        val stopCount: Int,
        val stops: List<RoutePlanStop>,
        val routeGeometry: List<RoutePlanCoordinate>
    )

    data class RoutePlanStop(
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

    data class RoutePlanCoordinate(
        val latitude: Double,
        val longitude: Double
    )

    private fun RemoteRoutePlanSummary.toDomain(): RoutePlanSummary {
        return RoutePlanSummary(
            routePlanId = routePlanId,
            name = name,
            description = description,
            startLabel = startLabel,
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            stopCount = stopCount
        )
    }

    private fun RemoteRoutePlanDetail.toDomain(): RoutePlanDetail {
        return RoutePlanDetail(
            routePlanId = routePlanId,
            name = name,
            description = description,
            startLabel = startLabel,
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            stopCount = stopCount,
            stops = stops.map { it.toDomain() },
            routeGeometry = routeGeometry.map { it.toDomain() }
        )
    }

    private fun RemoteRoutePlanStop.toDomain(): RoutePlanStop {
        return RoutePlanStop(
            routePlanPointId = routePlanPointId,
            visitOrder = visitOrder,
            savedVisitTargetId = savedVisitTargetId,
            targetType = targetType,
            title = title,
            subtitle = subtitle,
            observationId = observationId,
            plantSpeciesId = plantSpeciesId,
            publicationId = publicationId,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun RemoteRoutePlanCoordinate.toDomain(): RoutePlanCoordinate {
        return RoutePlanCoordinate(
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun RoutePlanDetail.toRoutingWaypoints(): List<RoutePlanCoordinate> {
        val waypoints = mutableListOf<RoutePlanCoordinate>()

        if (startLatitude != null && startLongitude != null) {
            waypoints += RoutePlanCoordinate(
                latitude = startLatitude,
                longitude = startLongitude
            )
        }

        stops.forEach { stop ->
            if (stop.latitude != null && stop.longitude != null) {
                val candidate = RoutePlanCoordinate(
                    latitude = stop.latitude,
                    longitude = stop.longitude
                )
                if (waypoints.lastOrNull() != candidate) {
                    waypoints += candidate
                }
            }
        }

        return waypoints
    }
}
