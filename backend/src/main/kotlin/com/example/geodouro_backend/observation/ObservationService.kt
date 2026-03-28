package com.example.geodouro_backend.observation

import java.sql.Timestamp
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ObservationService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun upsertObservation(request: UpsertObservationRequest): ObservationResponse {
        validateCoordinates(request.latitude, request.longitude)

        val deviceObservationId = request.deviceObservationId ?: UUID.randomUUID()
        val userId = resolveUserId(request)
        val syncStatus = normalizeSyncStatus(request.syncStatus)
        val observedAt = request.observedAt ?: java.time.Instant.now()
        val observedAtTimestamp = Timestamp.from(observedAt)

        logger.debug(
            "Preparing upsert deviceObservationId={} userId={} capturedAt={} confidence={} lat={} lon={}",
            deviceObservationId,
            userId,
            request.capturedAt,
            request.confidence,
            request.latitude,
            request.longitude
        )

        val parameters = MapSqlParameterSource()
            .addValue("deviceObservationId", deviceObservationId)
            .addValue("userId", userId)
            .addValue("plantSpeciesId", null)
            .addValue("imageUri", request.imageUri)
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

        val response = jdbcTemplate.query(UPSERT_OBSERVATION_SQL, parameters, observationRowMapper).first()

        logger.info(
            "Upsert finished observationId={} deviceObservationId={} userId={} syncStatus={}",
            response.observationId,
            response.deviceObservationId,
            response.userId,
            response.syncStatus
        )

        return response
    }

    fun getByDeviceObservationId(deviceObservationId: UUID): ObservationResponse {
        return jdbcTemplate.query(
            """
            SELECT observation_id,
                   device_observation_id,
                   user_id,
                   predicted_scientific_name,
                   confidence,
                   sync_status,
                   is_published,
                   observed_at
            FROM observation
            WHERE device_observation_id = :deviceObservationId
            """.trimIndent(),
            MapSqlParameterSource("deviceObservationId", deviceObservationId),
            observationRowMapper
        ).firstOrNull() ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Observation not found for deviceObservationId=$deviceObservationId"
        )
    }

    private fun resolveUserId(request: UpsertObservationRequest): Int {
        request.userId?.let { providedUserId ->
            val existing = jdbcTemplate.query(
                "SELECT user_id FROM app_user WHERE user_id = :userId",
                MapSqlParameterSource("userId", providedUserId)
            ) { rs, _ -> rs.getInt("user_id") }.firstOrNull()

            logger.debug("Requested explicit userId={} exists={}", providedUserId, existing != null)

            return existing ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "User with id=$providedUserId does not exist"
            )
        }

        val guestLabel = request.guestLabel
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "guest-${UUID.randomUUID()}"

        val existingGuestId = jdbcTemplate.query(
            "SELECT user_id FROM app_user WHERE guest_label = :guestLabel",
            MapSqlParameterSource("guestLabel", guestLabel)
        ) { rs, _ -> rs.getInt("user_id") }.firstOrNull()

        if (existingGuestId != null) {
            logger.info("Reusing guest user guestLabel={} userId={}", guestLabel, existingGuestId)
            return existingGuestId
        }

        val createdUserId = jdbcTemplate.queryForObject(
            """
            INSERT INTO app_user (is_authenticated, guest_label)
            VALUES (FALSE, :guestLabel)
            RETURNING user_id
            """.trimIndent(),
            MapSqlParameterSource("guestLabel", guestLabel),
            Int::class.java
        ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create guest user")

        logger.info("Created guest user guestLabel={} userId={}", guestLabel, createdUserId)
        return createdUserId
    }

    private fun normalizeSyncStatus(syncStatus: String?): String {
        val normalized = syncStatus?.trim()?.uppercase() ?: "PENDING"
        if (normalized !in allowedSyncStatuses) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "syncStatus must be one of $allowedSyncStatuses"
            )
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

    private val observationRowMapper = RowMapper { rs, _ ->
        ObservationResponse(
            observationId = rs.getInt("observation_id"),
            deviceObservationId = rs.getObject("device_observation_id", UUID::class.java),
            userId = rs.getInt("user_id"),
            predictedScientificName = rs.getString("predicted_scientific_name"),
            confidence = rs.getObject("confidence")?.toString()?.toFloat(),
            syncStatus = rs.getString("sync_status"),
            isPublished = rs.getBoolean("is_published"),
            observedAt = rs.getTimestamp("observed_at").toInstant()
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ObservationService::class.java)
        private val allowedSyncStatuses = setOf("PENDING", "SYNCED", "FAILED")

        private val UPSERT_OBSERVATION_SQL = """
            INSERT INTO observation (
                device_observation_id,
                user_id,
                plant_species_id,
                image_uri,
                captured_at,
                predicted_scientific_name,
                confidence,
                enriched_scientific_name,
                enriched_common_name,
                enriched_family,
                enriched_wikipedia_url,
                enriched_photo_url,
                latitude,
                longitude,
                observed_at,
                is_published,
                is_synced,
                sync_status,
                last_sync_attempt_at,
                notes
            ) VALUES (
                :deviceObservationId,
                :userId,
                :plantSpeciesId,
                :imageUri,
                :capturedAt,
                :predictedScientificName,
                :confidence,
                :enrichedScientificName,
                :enrichedCommonName,
                :enrichedFamily,
                :enrichedWikipediaUrl,
                :enrichedPhotoUrl,
                :latitude,
                :longitude,
                :observedAt,
                :isPublished,
                :isSynced,
                :syncStatus,
                :lastSyncAttemptAt,
                :notes
            )
            ON CONFLICT (device_observation_id)
            DO UPDATE SET
                user_id = EXCLUDED.user_id,
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
            RETURNING observation_id,
                      device_observation_id,
                      user_id,
                      predicted_scientific_name,
                      confidence,
                      sync_status,
                      is_published,
                      observed_at
        """.trimIndent()
    }
}


