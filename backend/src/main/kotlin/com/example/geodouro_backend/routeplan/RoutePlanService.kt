package com.example.geodouro_backend.routeplan

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class RoutePlanService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun listRoutePlans(userId: Int): List<RoutePlanSummaryResponse> {
        return jdbcTemplate.query(
            LIST_ROUTE_PLANS_SQL,
            MapSqlParameterSource("userId", userId),
            routePlanSummaryRowMapper
        )
    }

    fun getRoutePlanDetail(routePlanId: Int, userId: Int): RoutePlanDetailResponse {
        val summary = jdbcTemplate.query(
            ROUTE_PLAN_DETAIL_SQL,
            MapSqlParameterSource()
                .addValue("routePlanId", routePlanId)
                .addValue("userId", userId),
            routePlanDetailRowMapper
        ).firstOrNull() ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Route plan not found for routePlanId=$routePlanId and userId=$userId"
        )

        val stops = jdbcTemplate.query(
            ROUTE_PLAN_STOPS_SQL,
            MapSqlParameterSource()
                .addValue("routePlanId", routePlanId)
                .addValue("userId", userId),
            routePlanStopRowMapper
        )

        return summary.copy(
            stops = stops,
            routeGeometry = buildRouteGeometry(summary, stops)
        )
    }

    private fun buildRouteGeometry(
        summary: RoutePlanDetailResponse,
        stops: List<RoutePlanStopResponse>
    ): List<RoutePlanCoordinateResponse> {
        val stopCoordinates = stops.mapNotNull { stop ->
            if (stop.latitude != null && stop.longitude != null) {
                RoutePlanCoordinateResponse(stop.latitude, stop.longitude)
            } else {
                null
            }
        }

        val startCoordinate = if (summary.startLatitude != null && summary.startLongitude != null) {
            RoutePlanCoordinateResponse(summary.startLatitude, summary.startLongitude)
        } else {
            stopCoordinates.firstOrNull()
        } ?: return emptyList()

        val geometry = mutableListOf<RoutePlanCoordinateResponse>()
        geometry += startCoordinate
        geometry += stopCoordinates

        if (geometry.lastOrNull() != startCoordinate) {
            geometry += startCoordinate
        }

        return geometry
    }

    companion object {
        private val routePlanSummaryRowMapper = RowMapper { rs, _ ->
            RoutePlanSummaryResponse(
                routePlanId = rs.getInt("route_plan_id"),
                name = rs.getString("name"),
                description = rs.getString("description"),
                startLabel = rs.getString("start_label"),
                startLatitude = rs.getBigDecimal("start_latitude")?.toDouble(),
                startLongitude = rs.getBigDecimal("start_longitude")?.toDouble(),
                stopCount = rs.getInt("stop_count")
            )
        }

        private val routePlanDetailRowMapper = RowMapper { rs, _ ->
            RoutePlanDetailResponse(
                routePlanId = rs.getInt("route_plan_id"),
                name = rs.getString("name"),
                description = rs.getString("description"),
                startLabel = rs.getString("start_label"),
                startLatitude = rs.getBigDecimal("start_latitude")?.toDouble(),
                startLongitude = rs.getBigDecimal("start_longitude")?.toDouble(),
                stopCount = rs.getInt("stop_count"),
                stops = emptyList(),
                routeGeometry = emptyList()
            )
        }

        private val routePlanStopRowMapper = RowMapper { rs, _ ->
            RoutePlanStopResponse(
                routePlanPointId = rs.getInt("route_plan_point_id"),
                visitOrder = rs.getInt("visit_order"),
                savedVisitTargetId = rs.getInt("saved_visit_target_id"),
                targetType = rs.getString("target_type"),
                title = rs.getString("title"),
                subtitle = rs.getString("subtitle"),
                observationId = rs.getObject("observation_id", java.lang.Integer::class.java)?.toInt(),
                plantSpeciesId = rs.getObject("plant_species_id", java.lang.Integer::class.java)?.toInt(),
                publicationId = rs.getObject("publication_id", java.lang.Integer::class.java)?.toInt(),
                latitude = rs.getBigDecimal("latitude")?.toDouble(),
                longitude = rs.getBigDecimal("longitude")?.toDouble()
            )
        }

        private const val ROUTE_PLAN_BASE_SELECT = """
            SELECT rp.route_plan_id,
                   rp.name,
                   rp.description,
                   rp.start_label,
                   rp.start_latitude,
                   rp.start_longitude,
                   COUNT(rpp.route_plan_point_id) AS stop_count
            FROM route_plan rp
            LEFT JOIN route_plan_point rpp ON rpp.route_plan_id = rp.route_plan_id
            WHERE rp.user_id = :userId
            GROUP BY rp.route_plan_id, rp.name, rp.description, rp.start_label, rp.start_latitude, rp.start_longitude
        """

        private const val LIST_ROUTE_PLANS_SQL = ROUTE_PLAN_BASE_SELECT + """
            ORDER BY rp.updated_at DESC NULLS LAST, rp.route_plan_id DESC
        """

        private const val ROUTE_PLAN_DETAIL_SQL = """
            SELECT rp.route_plan_id,
                   rp.name,
                   rp.description,
                   rp.start_label,
                   rp.start_latitude,
                   rp.start_longitude,
                   COUNT(rpp.route_plan_point_id) AS stop_count
            FROM route_plan rp
            LEFT JOIN route_plan_point rpp ON rpp.route_plan_id = rp.route_plan_id
            WHERE rp.user_id = :userId
              AND rp.route_plan_id = :routePlanId
            GROUP BY rp.route_plan_id, rp.name, rp.description, rp.start_label, rp.start_latitude, rp.start_longitude
        """

        private const val ROUTE_PLAN_STOPS_SQL = """
            SELECT rpp.route_plan_point_id,
                   rpp.visit_order,
                   rpp.saved_visit_target_id,
                   CASE
                       WHEN svt.observation_id IS NOT NULL THEN 'observation'
                       WHEN svt.publication_id IS NOT NULL THEN 'publication'
                       WHEN COALESCE(
                           svt.plant_species_id,
                           obs_target.plant_species_id,
                           publication_target.plant_species_id
                       ) IS NOT NULL THEN 'species'
                       ELSE 'unknown'
                   END AS target_type,
                   CASE
                       WHEN svt.observation_id IS NOT NULL THEN COALESCE(
                           NULLIF(obs_target.enriched_common_name, ''),
                           NULLIF(species_target.common_name, ''),
                           NULLIF(species_target.scientific_name, ''),
                           'Observacao botanica'
                       )
                       WHEN svt.publication_id IS NOT NULL THEN COALESCE(
                           NULLIF(publication_target.title, ''),
                           NULLIF(species_target.common_name, ''),
                           NULLIF(publication_observation.enriched_common_name, ''),
                           NULLIF(species_target.scientific_name, ''),
                           'Publicacao botanica'
                       )
                       WHEN COALESCE(
                           svt.plant_species_id,
                           obs_target.plant_species_id,
                           publication_target.plant_species_id
                       ) IS NOT NULL THEN COALESCE(
                           NULLIF(species_target.common_name, ''),
                           NULLIF(species_target.scientific_name, ''),
                           'Especie selecionada'
                       )
                       ELSE 'Paragem ' || rpp.visit_order
                   END AS title,
                   CASE
                       WHEN svt.observation_id IS NOT NULL THEN COALESCE(
                           NULLIF(obs_target.enriched_scientific_name, ''),
                           NULLIF(obs_target.predicted_scientific_name, ''),
                           NULLIF(species_target.scientific_name, ''),
                           'Observacao com coordenadas'
                       )
                       WHEN svt.publication_id IS NOT NULL THEN COALESCE(
                           NULLIF(species_target.scientific_name, ''),
                           NULLIF(publication_observation.enriched_scientific_name, ''),
                           NULLIF(publication_observation.predicted_scientific_name, ''),
                           'Publicacao associada a observacao'
                       )
                       WHEN COALESCE(
                           svt.plant_species_id,
                           obs_target.plant_species_id,
                           publication_target.plant_species_id
                       ) IS NOT NULL THEN COALESCE(
                           NULLIF(species_target.scientific_name, ''),
                           'Sem classificacao cientifica'
                       )
                       ELSE COALESCE(
                           NULLIF(svt.notes, ''),
                           NULLIF(rpp.notes, '')
                       )
                   END AS subtitle,
                   svt.observation_id,
                   COALESCE(svt.plant_species_id, obs_target.plant_species_id, publication_target.plant_species_id) AS plant_species_id,
                   svt.publication_id,
                   COALESCE(
                       obs_target.latitude,
                       publication_observation.latitude,
                       species_observation.latitude
                   ) AS latitude,
                   COALESCE(
                       obs_target.longitude,
                       publication_observation.longitude,
                       species_observation.longitude
                   ) AS longitude
            FROM route_plan rp
            JOIN route_plan_point rpp ON rpp.route_plan_id = rp.route_plan_id
            JOIN saved_visit_target svt ON svt.saved_visit_target_id = rpp.saved_visit_target_id
            LEFT JOIN observation obs_target ON obs_target.observation_id = svt.observation_id
            LEFT JOIN publication publication_target ON publication_target.publication_id = svt.publication_id
            LEFT JOIN observation publication_observation ON publication_observation.observation_id = publication_target.observation_id
            LEFT JOIN plant_species species_target ON species_target.plant_species_id = COALESCE(
                svt.plant_species_id,
                obs_target.plant_species_id,
                publication_target.plant_species_id
            )
            LEFT JOIN LATERAL (
                SELECT o.latitude,
                       o.longitude
                FROM observation o
                WHERE o.plant_species_id = COALESCE(
                    svt.plant_species_id,
                    obs_target.plant_species_id,
                    publication_target.plant_species_id
                )
                  AND o.latitude IS NOT NULL
                  AND o.longitude IS NOT NULL
                ORDER BY o.observed_at DESC NULLS LAST, o.observation_id DESC
                LIMIT 1
            ) species_observation ON TRUE
            WHERE rp.user_id = :userId
              AND rp.route_plan_id = :routePlanId
            ORDER BY rpp.visit_order ASC, rpp.route_plan_point_id ASC
        """
    }
}

