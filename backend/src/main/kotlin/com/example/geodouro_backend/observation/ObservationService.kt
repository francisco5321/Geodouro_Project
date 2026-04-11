package com.example.geodouro_backend.observation

import java.sql.Timestamp
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@Service
class ObservationService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val observationStorageService: ObservationStorageService
) {

    fun upsertObservation(request: UpsertObservationRequest, authenticatedUserId: Int? = null): ObservationResponse {
        val sanitizedImagePaths = sanitizeImagePaths(request.imageUris)
            .ifEmpty {
                request.imageUri
                    ?.takeUnless { it.startsWith("content://") }
                    ?.let(::listOf)
                    .orEmpty()
            }
        return upsertObservationInternal(request, sanitizedImagePaths, authenticatedUserId = authenticatedUserId)
    }

    fun upsertObservation(
        request: UpsertObservationRequest,
        images: List<MultipartFile>,
        authenticatedUserId: Int? = null
    ): ObservationResponse {
        validateCoordinates(request.latitude, request.longitude)

        val deviceObservationId = request.deviceObservationId ?: UUID.randomUUID()
        val plantSpeciesId = resolvePlantSpeciesId(request)
        val storedImagePaths = observationStorageService.storeObservationImages(
            plantSpeciesId = plantSpeciesId,
            deviceObservationId = deviceObservationId,
            images = images
        )

        return upsertObservationInternal(
            request = request.copy(deviceObservationId = deviceObservationId),
            storedImagePaths = storedImagePaths,
            resolvedPlantSpeciesId = plantSpeciesId,
            authenticatedUserId = authenticatedUserId
        )
    }

    fun listObservations(
        userId: Int?,
        guestLabel: String?,
        authenticatedUserId: Int? = null
    ): List<ObservationDetailResponse> {
        val resolvedUserId = authenticatedUserId ?: resolveLookupUserId(userId, guestLabel)
        return jdbcTemplate.query(
            LIST_OBSERVATIONS_SQL,
            MapSqlParameterSource("userId", resolvedUserId),
            observationDetailSummaryRowMapper
        ).map(::withObservationImages)
    }

    fun getObservationDetail(deviceObservationId: UUID): ObservationDetailResponse {
        return jdbcTemplate.query(
            OBSERVATION_DETAIL_SQL,
            MapSqlParameterSource("deviceObservationId", deviceObservationId),
            observationDetailSummaryRowMapper
        ).firstOrNull()?.let(::withObservationImages)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Observation not found for deviceObservationId=$deviceObservationId"
            )
    }

    fun updateObservationMetadata(
        deviceObservationId: UUID,
        request: UpdateObservationMetadataRequest,
        authenticatedUserId: Int? = null
    ): ObservationDetailResponse {
        val current = getObservationDetail(deviceObservationId)
        if (authenticatedUserId != null && current.userId != authenticatedUserId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Nao tens permissao para editar esta observacao")
        }
        if (current.isPublished) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot update a published observation")
        }

        val scientificName = request.scientificName?.trim().orEmpty().ifBlank { current.scientificName }
        val commonName = request.commonName?.trim()?.takeIf { it.isNotBlank() }
        val family = request.family?.trim()?.takeIf { it.isNotBlank() } ?: current.family ?: "Unknown"
        val plantSpeciesId = upsertPlantSpecies(scientificName, commonName, family)

        jdbcTemplate.update(
            UPDATE_OBSERVATION_METADATA_SQL,
            MapSqlParameterSource()
                .addValue("deviceObservationId", deviceObservationId)
                .addValue("plantSpeciesId", plantSpeciesId)
                .addValue("scientificName", scientificName)
                .addValue("commonName", commonName)
                .addValue("family", family)
        )

        return getObservationDetail(deviceObservationId)
    }

    private fun upsertObservationInternal(
        request: UpsertObservationRequest,
        storedImagePaths: List<String>,
        resolvedPlantSpeciesId: Int? = null,
        authenticatedUserId: Int? = null
    ): ObservationResponse {
        validateCoordinates(request.latitude, request.longitude)

        val deviceObservationId = request.deviceObservationId ?: UUID.randomUUID()
        val userId = authenticatedUserId ?: resolveUserId(request)
        val plantSpeciesId = resolvedPlantSpeciesId ?: resolvePlantSpeciesId(request)
        val syncStatus = normalizeSyncStatus(request.syncStatus)
        val observedAt = request.observedAt ?: java.time.Instant.now()
        val observedAtTimestamp = Timestamp.from(observedAt)
        val primaryImagePath = storedImagePaths.firstOrNull()

        val parameters = MapSqlParameterSource()
            .addValue("deviceObservationId", deviceObservationId)
            .addValue("userId", userId)
            .addValue("plantSpeciesId", plantSpeciesId)
            .addValue("imageUri", primaryImagePath)
            .addValue("capturedAt", request.capturedAt)
            .addValue("predictedScientificName", request.predictedScientificName.trim())
            .addValue("confidence", request.confidence)
            .addValue("enrichedScientificName", request.enrichedScientificName)
            .addValue("enrichedCommonName", request.enrichedCommonName)
            .addValue("enrichedFamily", request.enrichedFamily)
            .addValue("enrichedWikipediaUrl", request.enrichedWikipediaUrl)
            .addValue("enrichedPhotoUrl", request.enrichedPhotoUrl)
            .addValue("latitude", request.latitude)
            .addValue("longitude", request.longitude)
            .addValue("observedAt", observedAtTimestamp)
            .addValue("isPublished", request.isPublished)
            .addValue("isSynced", syncStatus == "SYNCED")
            .addValue("syncStatus", syncStatus)
            .addValue("lastSyncAttemptAt", request.lastSyncAttemptAt)
            .addValue("notes", request.notes)

        val response = jdbcTemplate.query(UPSERT_OBSERVATION_SQL, parameters, observationResponseRowMapper).first()
        replaceObservationImages(response.observationId, storedImagePaths)
        refreshPlantSpeciesImageCount(plantSpeciesId)
        return response
    }

    private fun resolveLookupUserId(userId: Int?, guestLabel: String?): Int {
        userId?.let { return it }
        guestLabel?.trim()?.takeIf { it.isNotBlank() }?.let { label ->
            return jdbcTemplate.query(
                "SELECT user_id FROM app_user WHERE guest_label = :guestLabel",
                MapSqlParameterSource("guestLabel", label)
            ) { rs, _ -> rs.getInt("user_id") }.firstOrNull()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for guestLabel=$label")
        }
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Either userId or guestLabel must be provided")
    }

    private fun resolveUserId(request: UpsertObservationRequest): Int {
        request.userId?.let { providedUserId ->
            val existing = jdbcTemplate.query(
                "SELECT user_id FROM app_user WHERE user_id = :userId",
                MapSqlParameterSource("userId", providedUserId)
            ) { rs, _ -> rs.getInt("user_id") }.firstOrNull()
            return existing ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "User with id=$providedUserId does not exist")
        }

        val guestLabel = request.guestLabel?.trim()?.takeIf { it.isNotBlank() } ?: "guest-${UUID.randomUUID()}"
        val existingGuestId = jdbcTemplate.query(
            "SELECT user_id FROM app_user WHERE guest_label = :guestLabel",
            MapSqlParameterSource("guestLabel", guestLabel)
        ) { rs, _ -> rs.getInt("user_id") }.firstOrNull()
        if (existingGuestId != null) return existingGuestId

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO app_user (is_authenticated, guest_label)
            VALUES (FALSE, :guestLabel)
            RETURNING user_id
            """.trimIndent(),
            MapSqlParameterSource("guestLabel", guestLabel),
            Int::class.java
        ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create guest user")
    }

    private fun resolvePlantSpeciesId(request: UpsertObservationRequest): Int {
        val scientificName = request.enrichedScientificName?.trim()?.takeIf { it.isNotBlank() }
            ?: request.predictedScientificName.trim()
        val commonName = request.enrichedCommonName?.trim()?.takeIf { it.isNotBlank() }
        val family = request.enrichedFamily?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown"
        return upsertPlantSpecies(scientificName, commonName, family)
    }

    private fun upsertPlantSpecies(scientificName: String, commonName: String?, family: String): Int {
        val (genus, species) = splitScientificName(scientificName)
        return jdbcTemplate.queryForObject(
            UPSERT_PLANT_SPECIES_SQL,
            MapSqlParameterSource()
                .addValue("scientificName", scientificName)
                .addValue("commonName", commonName)
                .addValue("family", family)
                .addValue("genus", genus)
                .addValue("species", species),
            Int::class.java
        ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not upsert plant species")
    }

    private fun withObservationImages(summary: ObservationDetailResponse): ObservationDetailResponse {
        val imagePaths = jdbcTemplate.query(
            OBSERVATION_IMAGE_PATHS_SQL,
            MapSqlParameterSource("observationId", summary.observationId)
        ) { rs, _ -> rs.getString("image_path") }.filterNotNull()

        return summary.copy(
            imagePaths = if (imagePaths.isNotEmpty()) imagePaths else summary.imagePaths
        )
    }

    private fun replaceObservationImages(observationId: Int, storedImagePaths: List<String>) {
        jdbcTemplate.update(
            "DELETE FROM observation_image WHERE observation_id = :observationId",
            MapSqlParameterSource("observationId", observationId)
        )
        storedImagePaths.forEach { imagePath ->
            jdbcTemplate.update(
                INSERT_OBSERVATION_IMAGE_SQL,
                MapSqlParameterSource()
                    .addValue("observationId", observationId)
                    .addValue("imagePath", imagePath)
            )
        }
    }

    private fun refreshPlantSpeciesImageCount(plantSpeciesId: Int) {
        jdbcTemplate.update(
            REFRESH_PLANT_SPECIES_IMAGE_COUNT_SQL,
            MapSqlParameterSource("plantSpeciesId", plantSpeciesId)
        )
    }

    private fun sanitizeImagePaths(imageUris: List<String>?): List<String> {
        return imageUris.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.filterNot { it.startsWith("content://") }.distinct()
    }

    private fun splitScientificName(scientificName: String): Pair<String, String> {
        val normalized = scientificName.trim().replace(Regex("\\s+"), " ")
        val parts = normalized.split(' ')
        val genus = parts.firstOrNull().orEmpty().ifBlank { "Unknown" }
        val species = parts.drop(1).joinToString(" ").ifBlank { genus.lowercase() }
        return genus to species
    }

    private fun normalizeSyncStatus(syncStatus: String?): String {
        val normalized = syncStatus?.trim()?.uppercase() ?: "PENDING"
        if (normalized !in allowedSyncStatuses) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "syncStatus must be one of $allowedSyncStatuses")
        }
        return normalized
    }

    private fun validateCoordinates(latitude: Double?, longitude: Double?) {
        if (latitude != null && (latitude < -90.0 || latitude > 90.0)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude must be between -90 and 90")
        }
        if (longitude != null && (longitude < -180.0 || longitude > 180.0)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "longitude must be between -180 and 180")
        }
    }

    private val observationResponseRowMapper = RowMapper { rs, _ ->
        ObservationResponse(
            observationId = rs.getInt("observation_id"),
            deviceObservationId = rs.getObject("device_observation_id", UUID::class.java),
            userId = rs.getInt("user_id"),
            predictedScientificName = rs.getString("predicted_scientific_name"),
            confidence = rs.getObject("confidence")?.toString()?.toFloat(),
            syncStatus = rs.getString("sync_status"),
            isPublished = rs.getBoolean("is_published"),
            observedAt = rs.getTimestamp("observed_at").toInstant(),
            storedImagePath = rs.getString("image_uri")
        )
    }

    private val observationDetailSummaryRowMapper = RowMapper { rs, _ ->
        ObservationDetailResponse(
            observationId = rs.getInt("observation_id"),
            deviceObservationId = rs.getObject("device_observation_id", UUID::class.java),
            userId = rs.getInt("user_id"),
            scientificName = rs.getString("scientific_name"),
            commonName = rs.getString("common_name"),
            family = rs.getString("family"),
            wikipediaUrl = rs.getString("wikipedia_url"),
            photoUrl = rs.getString("photo_url"),
            imagePaths = listOfNotNull(rs.getString("primary_image_path")),
            capturedAt = (rs.getObject("captured_at") as? Number)?.toLong(),
            confidence = rs.getObject("confidence")?.toString()?.toFloat(),
            latitude = rs.getBigDecimal("latitude")?.toDouble(),
            longitude = rs.getBigDecimal("longitude")?.toDouble(),
            syncStatus = rs.getString("sync_status"),
            isPublished = rs.getBoolean("is_published"),
            observedAt = rs.getTimestamp("observed_at").toInstant()
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ObservationService::class.java)
        private val allowedSyncStatuses = setOf("PENDING", "SYNCED", "FAILED")

        private const val OBSERVATION_DETAIL_SELECT = """
            SELECT o.observation_id,
                   COALESCE(o.device_observation_id, synthetic_device_observation_id(o.observation_id)) AS device_observation_id,
                   o.user_id,
                   COALESCE(o.enriched_scientific_name, o.predicted_scientific_name, ps.scientific_name) AS scientific_name,
                   COALESCE(o.enriched_common_name, ps.common_name) AS common_name,
                   COALESCE(o.enriched_family, ps.family) AS family,
                   o.enriched_wikipedia_url AS wikipedia_url,
                   o.enriched_photo_url AS photo_url,
                   COALESCE(oi.image_path, o.image_uri, o.enriched_photo_url) AS primary_image_path,
                   o.captured_at,
                   o.confidence,
                   o.latitude,
                   o.longitude,
                   o.sync_status,
                   o.is_published,
                   o.observed_at
            FROM observation o
            LEFT JOIN plant_species ps ON ps.plant_species_id = o.plant_species_id
            LEFT JOIN LATERAL (
                SELECT image_path
                FROM observation_image
                WHERE observation_id = o.observation_id
                ORDER BY observation_image_id ASC
                LIMIT 1
            ) oi ON TRUE
        """

        private const val LIST_OBSERVATIONS_SQL = OBSERVATION_DETAIL_SELECT + """
            WHERE o.user_id = :userId
            ORDER BY o.captured_at DESC NULLS LAST, o.observation_id DESC
        """

        private const val OBSERVATION_DETAIL_SQL = OBSERVATION_DETAIL_SELECT + """
            WHERE o.device_observation_id = :deviceObservationId
               OR synthetic_device_observation_id(o.observation_id) = :deviceObservationId
        """

        private const val OBSERVATION_IMAGE_PATHS_SQL = """
            SELECT image_path
            FROM observation_image
            WHERE observation_id = :observationId
            ORDER BY observation_image_id ASC
        """

        private val UPSERT_PLANT_SPECIES_SQL = """
            INSERT INTO plant_species (scientific_name, common_name, family, genus, species)
            VALUES (:scientificName, :commonName, :family, :genus, :species)
            ON CONFLICT (scientific_name)
            DO UPDATE SET
                common_name = COALESCE(EXCLUDED.common_name, plant_species.common_name),
                family = COALESCE(EXCLUDED.family, plant_species.family),
                genus = COALESCE(EXCLUDED.genus, plant_species.genus),
                species = COALESCE(EXCLUDED.species, plant_species.species),
                updated_at = NOW()
            RETURNING plant_species_id
        """.trimIndent()

        private val UPDATE_OBSERVATION_METADATA_SQL = """
            UPDATE observation
            SET plant_species_id = :plantSpeciesId,
                enriched_scientific_name = :scientificName,
                enriched_common_name = :commonName,
                enriched_family = :family,
                updated_at = NOW()
            WHERE device_observation_id = :deviceObservationId
        """.trimIndent()

        private val INSERT_OBSERVATION_IMAGE_SQL = """
            INSERT INTO observation_image (observation_id, image_path)
            VALUES (:observationId, :imagePath)
        """.trimIndent()

        private val REFRESH_PLANT_SPECIES_IMAGE_COUNT_SQL = """
            UPDATE plant_species
            SET image_count = (
                SELECT COUNT(*)
                FROM observation_image oi
                JOIN observation o ON o.observation_id = oi.observation_id
                WHERE o.plant_species_id = :plantSpeciesId
            ),
            updated_at = NOW()
            WHERE plant_species_id = :plantSpeciesId
        """.trimIndent()

        private val UPSERT_OBSERVATION_SQL = """
            INSERT INTO observation (
                device_observation_id, user_id, plant_species_id, image_uri, captured_at,
                predicted_scientific_name, confidence, enriched_scientific_name, enriched_common_name,
                enriched_family, enriched_wikipedia_url, enriched_photo_url, latitude, longitude,
                observed_at, is_published, is_synced, sync_status, last_sync_attempt_at, notes
            ) VALUES (
                :deviceObservationId, :userId, :plantSpeciesId, :imageUri, :capturedAt,
                :predictedScientificName, :confidence, :enrichedScientificName, :enrichedCommonName,
                :enrichedFamily, :enrichedWikipediaUrl, :enrichedPhotoUrl, :latitude, :longitude,
                :observedAt, :isPublished, :isSynced, :syncStatus, :lastSyncAttemptAt, :notes
            )
            ON CONFLICT (device_observation_id)
            DO UPDATE SET
                user_id = EXCLUDED.user_id,
                plant_species_id = EXCLUDED.plant_species_id,
                image_uri = EXCLUDED.image_uri,
                captured_at = EXCLUDED.captured_at,
                predicted_scientific_name = EXCLUDED.predicted_scientific_name,
                confidence = EXCLUDED.confidence,
                enriched_scientific_name = EXCLUDED.enriched_scientific_name,
                enriched_common_name = EXCLUDED.enriched_common_name,
                enriched_family = EXCLUDED.enriched_family,
                enriched_wikipedia_url = EXCLUDED.enriched_wikipedia_url,
                enriched_photo_url = EXCLUDED.enriched_photo_url,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                observed_at = EXCLUDED.observed_at,
                is_published = EXCLUDED.is_published,
                is_synced = EXCLUDED.is_synced,
                sync_status = EXCLUDED.sync_status,
                last_sync_attempt_at = EXCLUDED.last_sync_attempt_at,
                notes = EXCLUDED.notes,
                updated_at = NOW()
            RETURNING observation_id, device_observation_id, user_id, predicted_scientific_name,
                      confidence, sync_status, is_published, observed_at, image_uri
        """.trimIndent()
    }
}


