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
        val summary = jdbcTemplate.query(
            SPECIES_DETAIL_SQL,
            MapSqlParameterSource("speciesId", normalizedSpeciesId),
            speciesDetailRowMapper
        ).firstOrNull() ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Species not found for speciesId=$speciesId"
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
                return "Sem localizacoes registadas para esta especie."
            }

            if (observationCount == 1 || maxLatitude == null || maxLongitude == null) {
                return "1 observacao com localizacao registada em GPS %.5f, %.5f".format(
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
            WHERE lower(replace(trim(ps.scientific_name), ' ', '_')) = :speciesId
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
                   o.captured_at,
                   o.confidence,
                   o.sync_status,
                   o.is_published,
                   COALESCE(oi.image_path, o.image_uri, o.enriched_photo_url) AS image_path
            FROM observation o
            JOIN plant_species ps ON ps.plant_species_id = o.plant_species_id
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

