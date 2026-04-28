package com.example.geodouro_backend.publication

import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class PublicationService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun publishObservation(request: PublishObservationRequest, authenticatedUserId: Int? = null): PublicationResponse {
        if (authenticatedUserId == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação obrigatória para publicar observações")
        }
        val observation = jdbcTemplate.query(
            OBSERVATION_FOR_PUBLICATION_SQL,
            MapSqlParameterSource("deviceObservationId", request.deviceObservationId),
            observationForPublicationRowMapper
        ).firstOrNull() ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Observation not found for deviceObservationId=${request.deviceObservationId}"
        )

        if (authenticatedUserId != observation.userId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Não tens permissão para publicar esta observação")
        }

        val publicationId = jdbcTemplate.queryForObject(
            UPSERT_PUBLICATION_SQL,
            MapSqlParameterSource()
                .addValue("observationId", observation.observationId)
                .addValue("userId", observation.userId)
                .addValue("plantSpeciesId", observation.plantSpeciesId)
                .addValue("title", request.title?.trim()?.takeIf { it.isNotBlank() })
                .addValue("description", request.description?.trim()?.takeIf { it.isNotBlank() }),
            Int::class.java
        ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not publish observation")

        jdbcTemplate.update(
            "UPDATE observation SET is_published = TRUE, updated_at = NOW() WHERE observation_id = :observationId",
            MapSqlParameterSource("observationId", observation.observationId)
        )

        jdbcTemplate.update(
            "DELETE FROM publication_image WHERE publication_id = :publicationId",
            MapSqlParameterSource("publicationId", publicationId)
        )

        val copiedRows = jdbcTemplate.update(
            COPY_PUBLICATION_IMAGES_SQL,
            MapSqlParameterSource()
                .addValue("publicationId", publicationId)
                .addValue("observationId", observation.observationId)
        )

        if (copiedRows == 0 && !observation.imagePath.isNullOrBlank()) {
            jdbcTemplate.update(
                "INSERT INTO publication_image (publication_id, image_path) VALUES (:publicationId, :imagePath)",
                MapSqlParameterSource()
                    .addValue("publicationId", publicationId)
                    .addValue("imagePath", observation.imagePath)
            )
        }

        return getPublicationByDeviceObservationId(request.deviceObservationId)
    }

    fun listPublications(): List<PublicationResponse> {
        return jdbcTemplate.query(LIST_PUBLICATIONS_SQL, publicationRowMapper)
    }

    fun getPublicationByDeviceObservationId(deviceObservationId: UUID): PublicationResponse {
        return jdbcTemplate.query(
            PUBLICATION_BY_DEVICE_ID_SQL,
            MapSqlParameterSource("deviceObservationId", deviceObservationId),
            publicationRowMapper
        ).firstOrNull() ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Publication not found for deviceObservationId=$deviceObservationId"
        )
    }

    fun getPublicationById(publicationId: Int): PublicationResponse {
        return jdbcTemplate.query(
            PUBLICATION_BY_ID_SQL,
            MapSqlParameterSource("publicationId", publicationId),
            publicationRowMapper
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Publication not found for publicationId=$publicationId")
    }

    fun updatePublication(publicationId: Int, request: SavePublicationRequest, authenticatedUserId: Int?): PublicationResponse {
        requireManageAccess(publicationId, authenticatedUserId)
        val status = request.status?.trim()?.lowercase()
        if (status != null && status !in setOf("draft", "published")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado editorial inválido")
        }

        jdbcTemplate.update(
            """
            UPDATE publication
            SET title = :title,
                description = :description,
                status = COALESCE(:status, status),
                published_at = CASE WHEN :status = 'published' THEN CURRENT_TIMESTAMP ELSE published_at END,
                updated_at = CURRENT_TIMESTAMP
            WHERE publication_id = :publicationId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("publicationId", publicationId)
                .addValue("title", request.title?.trim()?.takeIf { it.isNotBlank() })
                .addValue("description", request.description?.trim()?.takeIf { it.isNotBlank() })
                .addValue("status", status)
        )
        syncObservationPublicationState(publicationId)
        return getPublicationById(publicationId)
    }

    fun deletePublication(publicationId: Int, authenticatedUserId: Int?) {
        requireManageAccess(publicationId, authenticatedUserId)
        val observationId = jdbcTemplate.queryForObject(
            "SELECT observation_id FROM publication WHERE publication_id = :publicationId",
            MapSqlParameterSource("publicationId", publicationId),
            Int::class.java
        )
        jdbcTemplate.update(
            "DELETE FROM publication_image WHERE publication_id = :publicationId",
            MapSqlParameterSource("publicationId", publicationId)
        )
        jdbcTemplate.update(
            "DELETE FROM publication WHERE publication_id = :publicationId",
            MapSqlParameterSource("publicationId", publicationId)
        )
        if (observationId != null) {
            jdbcTemplate.update(
                "UPDATE observation SET is_published = FALSE, updated_at = NOW() WHERE observation_id = :observationId",
                MapSqlParameterSource("observationId", observationId)
            )
        }
    }

    private fun requireManageAccess(publicationId: Int, authenticatedUserId: Int?) {
        if (authenticatedUserId == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação obrigatória") //
        }
        val canManage = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM publication p
            JOIN app_user requester ON requester.user_id = :requesterId
            WHERE p.publication_id = :publicationId
              AND (p.user_id = :requesterId OR COALESCE(requester.role, 'user') = 'admin')
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("publicationId", publicationId)
                .addValue("requesterId", authenticatedUserId),
            Int::class.java
        ) ?: 0
        if (canManage <= 0) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Não tens permissão para gerir esta publicação")
        }
    }

    private fun syncObservationPublicationState(publicationId: Int) {
        jdbcTemplate.update(
            """
            UPDATE observation o
            SET is_published = EXISTS (
                SELECT 1
                FROM publication p
                WHERE p.observation_id = o.observation_id
                  AND p.status = 'published'
            ),
            updated_at = NOW()
            WHERE o.observation_id = (
                SELECT observation_id FROM publication WHERE publication_id = :publicationId
            )
            """.trimIndent(),
            MapSqlParameterSource("publicationId", publicationId)
        )
    }

    private data class ObservationForPublication(
        val observationId: Int,
        val userId: Int,
        val plantSpeciesId: Int?,
        val imagePath: String?
    )

    companion object {
        private val observationForPublicationRowMapper = RowMapper { rs, _ ->
            ObservationForPublication(
                observationId = rs.getInt("observation_id"),
                userId = rs.getInt("user_id"),
                plantSpeciesId = rs.getObject("plant_species_id") as? Int,
                imagePath = rs.getString("image_uri")
            )
        }

        private val publicationRowMapper = RowMapper { rs, _ ->
            PublicationResponse(
                publicationId = rs.getInt("publication_id"),
                observationId = rs.getInt("observation_id"),
                deviceObservationId = UUID.fromString(rs.getString("device_observation_id")),
                userId = rs.getInt("user_id"),
                plantSpeciesId = rs.getObject("plant_species_id", java.lang.Integer::class.java)?.toInt(),
                userDisplayName = rs.getString("user_display_name") ?: "Utilizador",
                title = rs.getString("title"),
                description = rs.getString("description"),
                scientificName = rs.getString("scientific_name"),
                commonName = rs.getString("common_name"),
                imagePath = rs.getString("image_path"),
                latitude = rs.getBigDecimal("latitude")?.toDouble(),
                longitude = rs.getBigDecimal("longitude")?.toDouble(),
                publishedAt = rs.getTimestamp("published_at")?.toInstant() ?: Instant.now()
            )
        }

        private const val OBSERVATION_FOR_PUBLICATION_SQL = """
            SELECT observation_id,
                   user_id,
                   plant_species_id,
                   image_uri
            FROM observation
            WHERE device_observation_id = :deviceObservationId
        """

        private const val UPSERT_PUBLICATION_SQL = """
            INSERT INTO publication (observation_id, user_id, plant_species_id, title, description)
            VALUES (:observationId, :userId, :plantSpeciesId, :title, :description)
            ON CONFLICT (observation_id) DO UPDATE
            SET user_id = EXCLUDED.user_id,
                plant_species_id = EXCLUDED.plant_species_id,
                title = COALESCE(EXCLUDED.title, publication.title),
                description = COALESCE(EXCLUDED.description, publication.description)
            RETURNING publication_id
        """

        private const val COPY_PUBLICATION_IMAGES_SQL = """
            INSERT INTO publication_image (publication_id, image_path, thumbnail_path, mime_type, file_size_bytes, width_px, height_px)
            SELECT :publicationId,
                   image_path,
                   thumbnail_path,
                   mime_type,
                   file_size_bytes,
                   width_px,
                   height_px
            FROM observation_image
            WHERE observation_id = :observationId
        """

        private const val PUBLICATION_SELECT_SQL = """
            SELECT p.publication_id,
                   p.observation_id,
                   p.user_id,
                   p.plant_species_id,
                   COALESCE(o.device_observation_id, synthetic_device_observation_id(o.observation_id)) AS device_observation_id,
                   p.title,
                   p.description,
                   COALESCE(
                       NULLIF(TRIM(CONCAT(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))), ''),
                       NULLIF(u.first_name, ''),
                       NULLIF(u.last_name, ''),
                       NULLIF(u.username, ''),
                       u.guest_label,
                       'Utilizador'
                   ) AS user_display_name,
                   COALESCE(o.enriched_scientific_name, o.predicted_scientific_name) AS scientific_name,
                   COALESCE(o.enriched_common_name, ps.common_name) AS common_name,
                   COALESCE(pub_image.image_path, o.image_uri) AS image_path,
                   o.latitude,
                   o.longitude,
                   p.published_at
            FROM publication p
            JOIN observation o ON o.observation_id = p.observation_id
            JOIN app_user u ON u.user_id = p.user_id
            LEFT JOIN plant_species ps ON ps.plant_species_id = p.plant_species_id
            LEFT JOIN LATERAL (
                SELECT image_path
                FROM publication_image
                WHERE publication_id = p.publication_id
                ORDER BY publication_image_id ASC
                LIMIT 1
            ) pub_image ON TRUE
        """

        private const val LIST_PUBLICATIONS_SQL = PUBLICATION_SELECT_SQL + """
            ORDER BY p.published_at DESC
        """

        private const val PUBLICATION_BY_DEVICE_ID_SQL = PUBLICATION_SELECT_SQL + """
            WHERE o.device_observation_id = :deviceObservationId
               OR synthetic_device_observation_id(o.observation_id) = :deviceObservationId
            ORDER BY p.published_at DESC
        """

        private const val PUBLICATION_BY_ID_SQL = PUBLICATION_SELECT_SQL + """
            WHERE p.publication_id = :publicationId
            LIMIT 1
        """
    }
}

