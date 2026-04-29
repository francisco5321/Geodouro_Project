package com.example.geodouro_backend.routeplan

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@Service
class RoutePlanService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${app.routing.osrm-base-url:https://router.project-osrm.org}")
    private val osrmBaseUrl: String
) {
    private val httpClient: HttpClient = HttpClient.newHttpClient()

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


    @Transactional
    fun createRoutePlan(userId: Int, request: SaveRoutePlanRequest): RoutePlanMutationResponse {
        validateRoutePlanRequest(request)
        val routePlanId = jdbcTemplate.queryForObject(
            """
                INSERT INTO route_plan (user_id, name, description, start_label, start_latitude, start_longitude)
                VALUES (:userId, :name, :description, :startLabel, :startLatitude, :startLongitude)
                RETURNING route_plan_id
            """.trimIndent(),
            routePlanParams(userId, request),
            Int::class.java
        ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível criar o percurso")

        return RoutePlanMutationResponse(true, "Percurso criado com sucesso.", routePlanId)
    }

    @Transactional
    fun updateRoutePlan(routePlanId: Int, userId: Int, request: SaveRoutePlanRequest): RoutePlanMutationResponse {
        validateRoutePlanRequest(request)
        ensureOwnedRoutePlan(routePlanId, userId)
        jdbcTemplate.update(
            """
                UPDATE route_plan
                SET name = :name,
                    description = :description,
                    start_label = :startLabel,
                    start_latitude = :startLatitude,
                    start_longitude = :startLongitude,
                    updated_at = NOW()
                WHERE route_plan_id = :routePlanId
                  AND user_id = :userId
            """.trimIndent(),
            routePlanParams(userId, request).addValue("routePlanId", routePlanId)
        )
        return RoutePlanMutationResponse(true, "Percurso atualizado com sucesso.", routePlanId)
    }

    @Transactional
    fun deleteRoutePlan(routePlanId: Int, userId: Int) {
        val affected = jdbcTemplate.update(
            """
                DELETE FROM route_plan
                WHERE route_plan_id = :routePlanId
                  AND user_id = :userId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("routePlanId", routePlanId)
                .addValue("userId", userId)
        )
        if (affected == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Percurso não encontrado")
        }
    }

    @Transactional
    fun addSavedTargetToRoutePlan(routePlanId: Int, userId: Int, savedVisitTargetId: Int): RoutePlanStopMutationResponse {
        ensureOwnedRoutePlan(routePlanId, userId)
        ensureOwnedVisitTarget(savedVisitTargetId, userId)
        val existingPointId = findRoutePlanPointId(routePlanId, savedVisitTargetId)
        if (existingPointId != null) {
            return RoutePlanStopMutationResponse(true, true, "Esse alvo ja esta neste percurso.", existingPointId, savedVisitTargetId)
        }
        val pointId = insertRoutePlanPoint(routePlanId, savedVisitTargetId)
        return RoutePlanStopMutationResponse(true, true, "Alvo adicionado ao percurso.", pointId, savedVisitTargetId)
    }

    @Transactional
    fun addSpeciesToRoutePlan(routePlanId: Int, userId: Int, plantSpeciesId: Int): RoutePlanStopMutationResponse {
        ensureOwnedRoutePlan(routePlanId, userId)
        ensureSpeciesExists(plantSpeciesId)
        val savedVisitTargetId = findOrCreateVisitTarget(userId, "plant_species_id", plantSpeciesId)
        return addSavedTargetToRoutePlan(routePlanId, userId, savedVisitTargetId)
    }

    @Transactional
    fun toggleObservationPoint(routePlanId: Int, userId: Int, observationId: Int): RoutePlanStopMutationResponse {
        ensureOwnedRoutePlan(routePlanId, userId)
        ensureObservationWithCoordinatesExists(observationId)
        val savedVisitTargetId = findOrCreateVisitTarget(userId, "observation_id", observationId)
        val existingPointId = findRoutePlanPointId(routePlanId, savedVisitTargetId)
        if (existingPointId != null) {
            deleteRoutePlanPoint(existingPointId, userId)
            resequenceRoutePlan(routePlanId)
            return RoutePlanStopMutationResponse(true, false, "Observação removida do percurso.", null, savedVisitTargetId)
        }
        val pointId = insertRoutePlanPoint(routePlanId, savedVisitTargetId)
        return RoutePlanStopMutationResponse(true, true, "Observação adicionada ao percurso.", pointId, savedVisitTargetId)
    }

    @Transactional
    fun removeRoutePlanPoint(routePlanPointId: Int, userId: Int): Int {
        val routePlanId = findRoutePlanIdForPoint(routePlanPointId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ponto do percurso não encontrado")
        deleteRoutePlanPoint(routePlanPointId, userId)
        resequenceRoutePlan(routePlanId)
        return routePlanId
    }

    private fun validateRoutePlanRequest(request: SaveRoutePlanRequest) {
        if (request.name.trim().isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do percurso e obrigatorio")
        }
        validateCoordinate("startLatitude", request.startLatitude, -90.0, 90.0)
        validateCoordinate("startLongitude", request.startLongitude, -180.0, 180.0)
        if ((request.startLatitude == null) xor (request.startLongitude == null)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Define latitude e longitude para o ponto de partida")
        }
    }

    private fun validateCoordinate(name: String, value: Double?, min: Double, max: Double) {
        if (value != null && (value < min || value > max)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$name invalida")
        }
    }

    private fun routePlanParams(userId: Int, request: SaveRoutePlanRequest): MapSqlParameterSource {
        return MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("name", request.name.trim())
            .addValue("description", request.description?.trim()?.takeIf { it.isNotBlank() })
            .addValue("startLabel", request.startLabel?.trim()?.takeIf { it.isNotBlank() })
            .addValue("startLatitude", request.startLatitude)
            .addValue("startLongitude", request.startLongitude)
    }

    private fun ensureOwnedRoutePlan(routePlanId: Int, userId: Int) {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM route_plan WHERE route_plan_id = :routePlanId AND user_id = :userId",
            MapSqlParameterSource().addValue("routePlanId", routePlanId).addValue("userId", userId),
            Int::class.java
        ) ?: 0
        if (count == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Percurso não encontrado") //2
    }

    private fun ensureOwnedVisitTarget(savedVisitTargetId: Int, userId: Int) {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM saved_visit_target WHERE saved_visit_target_id = :savedVisitTargetId AND user_id = :userId",
            MapSqlParameterSource().addValue("savedVisitTargetId", savedVisitTargetId).addValue("userId", userId),
            Int::class.java
        ) ?: 0
        if (count == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Alvo de visita não encontrado")
    }

    private fun ensureSpeciesExists(plantSpeciesId: Int) {
        val count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM plant_species ps
                WHERE ps.plant_species_id = :plantSpeciesId
                  AND EXISTS (
                      SELECT 1
                      FROM observation o
                      WHERE o.plant_species_id = ps.plant_species_id
                  )
            """.trimIndent(),
            MapSqlParameterSource("plantSpeciesId", plantSpeciesId),
            Int::class.java
        ) ?: 0
        if (count == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Espécie não encontrada")
    }

    private fun ensureObservationWithCoordinatesExists(observationId: Int) {
        val count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM observation
                WHERE observation_id = :observationId
                  AND latitude IS NOT NULL
                  AND longitude IS NOT NULL
            """.trimIndent(),
            MapSqlParameterSource("observationId", observationId),
            Int::class.java
        ) ?: 0
        if (count == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Observação não encontrada")
    }

    private fun findOrCreateVisitTarget(userId: Int, columnName: String, targetId: Int): Int {
        val existing = try {
            jdbcTemplate.queryForObject(
                """
                    SELECT saved_visit_target_id
                    FROM saved_visit_target
                    WHERE user_id = :userId
                      AND $columnName = :targetId
                    LIMIT 1
                """.trimIndent(),
                MapSqlParameterSource().addValue("userId", userId).addValue("targetId", targetId),
                Int::class.java
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
        if (existing != null) return existing

        return jdbcTemplate.queryForObject(
            """
                INSERT INTO saved_visit_target (user_id, $columnName)
                VALUES (:userId, :targetId)
                RETURNING saved_visit_target_id
            """.trimIndent(),
            MapSqlParameterSource().addValue("userId", userId).addValue("targetId", targetId),
            Int::class.java
        ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível guardar o alvo")
    }

    private fun findRoutePlanPointId(routePlanId: Int, savedVisitTargetId: Int): Int? {
        return try {
            jdbcTemplate.queryForObject(
                """
                    SELECT route_plan_point_id
                    FROM route_plan_point
                    WHERE route_plan_id = :routePlanId
                      AND saved_visit_target_id = :savedVisitTargetId
                    LIMIT 1
                """.trimIndent(),
                MapSqlParameterSource().addValue("routePlanId", routePlanId).addValue("savedVisitTargetId", savedVisitTargetId),
                Int::class.java
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    private fun insertRoutePlanPoint(routePlanId: Int, savedVisitTargetId: Int): Int {
        return jdbcTemplate.queryForObject(
            """
                INSERT INTO route_plan_point (route_plan_id, saved_visit_target_id, visit_order)
                VALUES (:routePlanId, :savedVisitTargetId, :visitOrder)
                RETURNING route_plan_point_id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("routePlanId", routePlanId)
                .addValue("savedVisitTargetId", savedVisitTargetId)
                .addValue("visitOrder", nextVisitOrder(routePlanId)),
            Int::class.java
        ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível adicionar o ponto")
    }

    private fun nextVisitOrder(routePlanId: Int): Int {
        return (jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(visit_order), 0) + 1 FROM route_plan_point WHERE route_plan_id = :routePlanId",
            MapSqlParameterSource("routePlanId", routePlanId),
            Int::class.java
        ) ?: 1)
    }

    private fun findRoutePlanIdForPoint(routePlanPointId: Int, userId: Int): Int? {
        return try {
            jdbcTemplate.queryForObject(
                """
                    SELECT rpp.route_plan_id
                    FROM route_plan_point rpp
                    JOIN route_plan rp ON rp.route_plan_id = rpp.route_plan_id
                    WHERE rpp.route_plan_point_id = :routePlanPointId
                      AND rp.user_id = :userId
                """.trimIndent(),
                MapSqlParameterSource().addValue("routePlanPointId", routePlanPointId).addValue("userId", userId),
                Int::class.java
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    private fun deleteRoutePlanPoint(routePlanPointId: Int, userId: Int) {
        val affected = jdbcTemplate.update(
            """
                DELETE FROM route_plan_point rpp
                USING route_plan rp
                WHERE rp.route_plan_id = rpp.route_plan_id
                  AND rp.user_id = :userId
                  AND rpp.route_plan_point_id = :routePlanPointId
            """.trimIndent(),
            MapSqlParameterSource().addValue("routePlanPointId", routePlanPointId).addValue("userId", userId)
        )
        if (affected == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ponto do percurso não encontrado") //2
    }

    private fun resequenceRoutePlan(routePlanId: Int) {
        val ids = jdbcTemplate.queryForList(
            """
                SELECT route_plan_point_id
                FROM route_plan_point
                WHERE route_plan_id = :routePlanId
                ORDER BY visit_order ASC, route_plan_point_id ASC
            """.trimIndent(),
            MapSqlParameterSource("routePlanId", routePlanId),
            Int::class.java
        )
        ids.forEachIndexed { index, pointId ->
            jdbcTemplate.update(
                "UPDATE route_plan_point SET visit_order = :visitOrder WHERE route_plan_point_id = :pointId",
                MapSqlParameterSource().addValue("visitOrder", index + 1).addValue("pointId", pointId)
            )
        }
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

        val waypointCoordinates = buildList {
            add(startCoordinate)
            addAll(stopCoordinates)
            if (stopCoordinates.isNotEmpty() && stopCoordinates.last() != startCoordinate) {
                add(startCoordinate)
            }
        }

        if (waypointCoordinates.size < 2) {
            return waypointCoordinates
        }

        return fetchRoutedGeometry(waypointCoordinates) ?: waypointCoordinates
    }

    private fun fetchRoutedGeometry(
        waypoints: List<RoutePlanCoordinateResponse>
    ): List<RoutePlanCoordinateResponse>? {
        return runCatching {
            val coordinates = waypoints.joinToString(";") { point ->
                "${point.longitude},${point.latitude}"
            }
            val encodedCoordinates = URLEncoder.encode(coordinates, StandardCharsets.UTF_8)
            val url = buildString {
                append(osrmBaseUrl.trimEnd('/'))
                append("/route/v1/foot/")
                append(encodedCoordinates)
                append("?overview=full&geometries=geojson")
            }

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                return@runCatching null
            }

            val root = objectMapper.readTree(response.body())
            if (root.path("code").asText() != "Ok") {
                return@runCatching null
            }

            root.path("routes")
                .firstOrNull()
                ?.path("geometry")
                ?.path("coordinates")
                ?.mapNotNull(::toRouteCoordinate)
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun toRouteCoordinate(node: JsonNode): RoutePlanCoordinateResponse? {
        if (!node.isArray || node.size() < 2) {
            return null
        }

        val longitude = node[0]?.asDouble()
        val latitude = node[1]?.asDouble()
        if (latitude == null || longitude == null) {
            return null
        }

        return RoutePlanCoordinateResponse(
            latitude = latitude,
            longitude = longitude
        )
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
                imagePath = rs.getString("image_path"),
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
                           'Publicação botânica'
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
                           'Publicação associada a observação'
                       )
                       WHEN COALESCE(
                           svt.plant_species_id,
                           obs_target.plant_species_id,
                           publication_target.plant_species_id
                       ) IS NOT NULL THEN COALESCE(
                           NULLIF(species_target.scientific_name, ''),
                           'Sem classificação científ ica'
                       )
                      ELSE NULLIF(svt.notes, '')
                     END AS subtitle,
                     COALESCE(
                         obs_target.image_uri,
                         publication_observation.image_uri,
                         species_observation.image_uri,
                         obs_target.enriched_photo_url,
                         publication_observation.enriched_photo_url,
                         species_observation.enriched_photo_url
                     ) AS image_path,
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
                         o.longitude,
                         o.image_uri,
                         o.enriched_photo_url
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
