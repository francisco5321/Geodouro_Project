package com.example.geodouro_backend.species

import java.nio.charset.StandardCharsets
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class SpeciesService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun listSpecies(): List<PlantSpeciesResponse> {
        return jdbcTemplate.query(LIST_SPECIES_SQL, speciesRowMapper)
    }

    fun getSpeciesDetail(speciesId: String): PlantSpeciesDetailResponse {
        val normalizedSpeciesId = speciesId.trim().lowercase()
        val plantSpeciesId = findPlantSpeciesIdBySlug(normalizedSpeciesId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Species not found for speciesId=$speciesId"
            )
        return getSpeciesDetailByPlantSpeciesId(plantSpeciesId)
    }

    fun updateSpecies(speciesId: String, requesterId: Int, request: UpdatePlantSpeciesRequest): PlantSpeciesDetailResponse {
        ensureAdmin(requesterId)
        val plantSpeciesId = findPlantSpeciesIdBySlug(speciesId.trim().lowercase())
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Species not found for speciesId=$speciesId")

        val scientificName = request.scientificName.trim()
        val family = request.family.trim()
        val genus = request.genus.trim()
        val specificEpithet = request.species.trim()
        if (scientificName.isBlank() || family.isBlank() || genus.isBlank() || specificEpithet.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "scientificName, family, genus and species are required")
        }

        jdbcTemplate.update(
            UPDATE_SPECIES_SQL,
            MapSqlParameterSource()
                .addValue("plantSpeciesId", plantSpeciesId)
                .addValue("scientificName", scientificName)
                .addValue("commonName", request.commonName?.trim()?.takeIf { it.isNotBlank() })
                .addValue("family", family)
                .addValue("genus", genus)
                .addValue("species", specificEpithet)
                .addValue("description", request.description?.trim()?.takeIf { it.isNotBlank() })
        )
        jdbcTemplate.update(
            SYNC_OBSERVATION_SPECIES_METADATA_SQL,
            MapSqlParameterSource()
                .addValue("plantSpeciesId", plantSpeciesId)
                .addValue("scientificName", scientificName)
                .addValue("commonName", request.commonName?.trim()?.takeIf { it.isNotBlank() })
                .addValue("family", family)
        )

        return getSpeciesDetailByPlantSpeciesId(plantSpeciesId)
    }

    private fun getSpeciesDetailByPlantSpeciesId(plantSpeciesId: Int): PlantSpeciesDetailResponse {
        val summary = jdbcTemplate.query(
            SPECIES_DETAIL_SQL,
            MapSqlParameterSource("plantSpeciesId", plantSpeciesId),
            speciesDetailRowMapper
        ).firstOrNull() ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Species not found for plantSpeciesId=$plantSpeciesId"
        )

        val galleryImagePaths = jdbcTemplate.query(
            SPECIES_GALLERY_SQL,
            MapSqlParameterSource("plantSpeciesId", summary.plantSpeciesId)
        ) { rs, _ ->
            rs.getString("image_path")
        }.filterNotNull()

        val observations = jdbcTemplate.query(
            SPECIES_OBSERVATIONS_SQL,
            MapSqlParameterSource("plantSpeciesId", summary.plantSpeciesId),
            speciesObservationRowMapper
        )

        return summary.copy(
            galleryImagePaths = galleryImagePaths,
            observations = observations
        )
    }

    private fun findPlantSpeciesIdBySlug(speciesId: String): Int? {
        return jdbcTemplate.query(
            FIND_SPECIES_ID_SQL,
            MapSqlParameterSource("speciesId", speciesId)
        ) { rs, _ -> rs.getInt("plant_species_id") }.firstOrNull()
    }

    private fun ensureAdmin(requesterId: Int) {
        val role = jdbcTemplate.query(
            "SELECT COALESCE(role, 'user') AS role FROM app_user WHERE user_id = :userId",
            MapSqlParameterSource("userId", requesterId)
        ) { rs, _ -> rs.getString("role") }.firstOrNull()

        if (!role.equals("admin", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso reservado a administradores")
        }
    }

    companion object {
        private val speciesRowMapper = RowMapper { rs, _ ->
            PlantSpeciesResponse(
                plantSpeciesId = rs.getInt("plant_species_id"),
                speciesId = rs.getString("species_id"),
                scientificName = rs.getString("scientific_name"),
                commonName = rs.getString("common_name"),
                family = rs.getString("family"),
                genus = rs.getString("genus"),
                species = rs.getString("species"),
                imageCount = rs.getInt("image_count"),
                description = rs.getString("description"),
                thumbnailPath = rs.getString("thumbnail_path"),
                wikipediaUrl = rs.getString("wikipedia_url"),
                updatedAt = rs.getTimestamp("updated_at").toInstant()
            )
        }

        private val speciesDetailRowMapper = RowMapper { rs, _ ->
            PlantSpeciesDetailResponse(
                plantSpeciesId = rs.getInt("plant_species_id"),
                speciesId = rs.getString("species_id"),
                scientificName = rs.getString("scientific_name"),
                commonName = rs.getString("common_name"),
                family = rs.getString("family"),
                genus = rs.getString("genus"),
                species = rs.getString("species"),
                imageCount = rs.getInt("image_count"),
                description = rs.getString("description"),
                observationCount = rs.getInt("observation_count"),
                syncedCount = rs.getInt("synced_count"),
                publishedCount = rs.getInt("published_count"),
                wikipediaUrl = rs.getString("wikipedia_url"),
                heroImagePath = rs.getString("hero_image_path"),
                galleryImagePaths = emptyList(),
                locationSummary = buildLocationSummary(
                    observationCount = rs.getInt("observations_with_location"),
                    minLatitude = rs.getBigDecimal("min_latitude")?.toDouble(),
                    minLongitude = rs.getBigDecimal("min_longitude")?.toDouble(),
                    maxLatitude = rs.getBigDecimal("max_latitude")?.toDouble(),
                    maxLongitude = rs.getBigDecimal("max_longitude")?.toDouble()
                ),
                observations = emptyList()
            )
        }

        private val speciesObservationRowMapper = RowMapper { rs, _ ->
            val fallbackObservationUuid = UUID.nameUUIDFromBytes(
                "legacy-observation-${rs.getInt("observation_id")}".toByteArray(StandardCharsets.UTF_8)
            )
            PlantSpeciesObservationResponse(
                deviceObservationId = rs.getString("device_observation_id")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(UUID::fromString)
                    ?: fallbackObservationUuid,
                scientificName = rs.getString("scientific_name"),
                commonName = rs.getString("common_name"),
                userDisplayName = rs.getString("user_display_name"),
                capturedAt = rs.getObject("captured_at", java.lang.Long::class.java)?.toLong(),
                confidence = rs.getObject("confidence")?.toString()?.toFloat(),
                syncStatus = rs.getString("sync_status"),
                isPublished = rs.getBoolean("is_published"),
                imagePath = rs.getString("image_path")
            )
        }

        private fun buildLocationSummary(
            observationCount: Int,
            minLatitude: Double?,
            minLongitude: Double?,
            maxLatitude: Double?,
            maxLongitude: Double?
        ): String {
            if (observationCount == 0 || minLatitude == null || minLongitude == null) {
                return "Sem localizações registadas para esta espécie."
            }

            if (observationCount == 1 || maxLatitude == null || maxLongitude == null) {
                return "1 observação com localização registada em GPS %.5f, %.5f".format(
                    minLatitude,
                    minLongitude
                )
            }

            return "Localizacoes registadas em $observationCount observacoes. Intervalo aproximado: %.5f, %.5f ate %.5f, %.5f".format(
                minLatitude,
                minLongitude,
                maxLatitude,
                maxLongitude
            )
        }

        private const val SPECIES_BASE_SELECT = """
            SELECT ps.plant_species_id,
                   lower(replace(trim(ps.scientific_name), ' ', '_')) AS species_id,
                   ps.scientific_name,
                   ps.common_name,
                   ps.family,
                   ps.genus,
                   ps.species,
                   ps.image_count,
                   ps.description,
                   ps.updated_at,
                   COALESCE(obs_image.image_path, latest_observation.image_uri, latest_observation.enriched_photo_url) AS thumbnail_path,
                   latest_observation.enriched_wikipedia_url AS wikipedia_url
            FROM plant_species ps
            LEFT JOIN LATERAL (
                SELECT o.observation_id,
                       o.image_uri,
                       o.enriched_photo_url,
                       o.enriched_wikipedia_url
                FROM observation o
                WHERE o.plant_species_id = ps.plant_species_id
                ORDER BY o.observed_at DESC, o.observation_id DESC
                LIMIT 1
            ) latest_observation ON TRUE
            LEFT JOIN LATERAL (
                SELECT oi.image_path
                FROM observation_image oi
                WHERE oi.observation_id = latest_observation.observation_id
                ORDER BY oi.observation_image_id ASC
                LIMIT 1
            ) obs_image ON TRUE
            WHERE EXISTS (
                SELECT 1
                FROM observation o
                WHERE o.plant_species_id = ps.plant_species_id
            )
        """

        private const val LIST_SPECIES_SQL = SPECIES_BASE_SELECT + """
            ORDER BY ps.scientific_name ASC
        """

        private const val SPECIES_DETAIL_SQL = """
            SELECT ps.plant_species_id,
                   lower(replace(trim(ps.scientific_name), ' ', '_')) AS species_id,
                   ps.scientific_name,
                   ps.common_name,
                   ps.family,
                   ps.genus,
                   ps.species,
                   ps.image_count,
                   ps.description,
                   ps.updated_at,
                   COALESCE(obs_image.image_path, latest_observation.image_uri, latest_observation.enriched_photo_url) AS hero_image_path,
                   latest_observation.enriched_wikipedia_url AS wikipedia_url,
                   observation_stats.observation_count,
                   observation_stats.synced_count,
                   observation_stats.published_count,
                   observation_stats.observations_with_location,
                   observation_stats.min_latitude,
                   observation_stats.min_longitude,
                   observation_stats.max_latitude,
                   observation_stats.max_longitude
            FROM plant_species ps
            LEFT JOIN LATERAL (
                SELECT o.observation_id,
                       o.image_uri,
                       o.enriched_photo_url,
                       o.enriched_wikipedia_url
                FROM observation o
                WHERE o.plant_species_id = ps.plant_species_id
                ORDER BY o.observed_at DESC, o.observation_id DESC
                LIMIT 1
            ) latest_observation ON TRUE
            LEFT JOIN LATERAL (
                SELECT oi.image_path
                FROM observation_image oi
                WHERE oi.observation_id = latest_observation.observation_id
                ORDER BY oi.observation_image_id ASC
                LIMIT 1
            ) obs_image ON TRUE
            LEFT JOIN LATERAL (
                SELECT COUNT(*) AS observation_count,
                       COUNT(*) FILTER (WHERE o.sync_status = 'SYNCED') AS synced_count,
                       COUNT(*) FILTER (WHERE o.is_published = TRUE) AS published_count,
                       COUNT(*) FILTER (WHERE o.latitude IS NOT NULL AND o.longitude IS NOT NULL) AS observations_with_location,
                       MIN(o.latitude) AS min_latitude,
                       MIN(o.longitude) AS min_longitude,
                       MAX(o.latitude) AS max_latitude,
                       MAX(o.longitude) AS max_longitude
                FROM observation o
                WHERE o.plant_species_id = ps.plant_species_id
            ) observation_stats ON TRUE
            WHERE ps.plant_species_id = :plantSpeciesId
              AND EXISTS (
                  SELECT 1
                  FROM observation o
                  WHERE o.plant_species_id = ps.plant_species_id
              )
        """

        private const val FIND_SPECIES_ID_SQL = """
            SELECT plant_species_id
            FROM plant_species
            WHERE lower(replace(trim(scientific_name), ' ', '_')) = :speciesId
            LIMIT 1
        """

        private const val UPDATE_SPECIES_SQL = """
            UPDATE plant_species
            SET scientific_name = :scientificName,
                common_name = :commonName,
                family = :family,
                genus = :genus,
                species = :species,
                description = :description,
                updated_at = NOW()
            WHERE plant_species_id = :plantSpeciesId
        """

        private const val SYNC_OBSERVATION_SPECIES_METADATA_SQL = """
            UPDATE observation
            SET enriched_scientific_name = :scientificName,
                enriched_common_name = :commonName,
                enriched_family = :family,
                updated_at = NOW()
            WHERE plant_species_id = :plantSpeciesId
        """

        private const val SPECIES_GALLERY_SQL = """
            SELECT DISTINCT oi.image_path
            FROM observation o
            JOIN observation_image oi ON oi.observation_id = o.observation_id
            WHERE o.plant_species_id = :plantSpeciesId
            ORDER BY oi.image_path ASC
            LIMIT 8
        """

        private const val SPECIES_OBSERVATIONS_SQL = """
            SELECT o.observation_id,
                   COALESCE(o.device_observation_id, synthetic_device_observation_id(o.observation_id)) AS device_observation_id,
                   COALESCE(o.enriched_scientific_name, o.predicted_scientific_name, ps.scientific_name) AS scientific_name,
                   COALESCE(o.enriched_common_name, ps.common_name) AS common_name,
                   CASE
                       WHEN o.is_published = FALSE THEN NULL
                       ELSE COALESCE(
                           NULLIF(TRIM(CONCAT(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))), ''),
                           NULLIF(u.first_name, ''),
                           NULLIF(u.last_name, ''),
                           NULLIF(u.username, ''),
                           u.guest_label,
                           'Utilizador'
                       )
                   END AS user_display_name,
                   o.captured_at,
                   o.confidence,
                   o.sync_status,
                   o.is_published,
                   COALESCE(oi.image_path, o.image_uri, o.enriched_photo_url) AS image_path
            FROM observation o
            JOIN plant_species ps ON ps.plant_species_id = o.plant_species_id
            LEFT JOIN publication p ON p.observation_id = o.observation_id
            LEFT JOIN app_user u ON u.user_id = COALESCE(p.user_id, o.user_id)
            LEFT JOIN LATERAL (
                SELECT image_path
                FROM observation_image
                WHERE observation_id = o.observation_id
                ORDER BY observation_image_id ASC
                LIMIT 1
            ) oi ON TRUE
            WHERE o.plant_species_id = :plantSpeciesId
            ORDER BY o.captured_at DESC NULLS LAST, o.observation_id DESC
            LIMIT 12
        """
    }
}

