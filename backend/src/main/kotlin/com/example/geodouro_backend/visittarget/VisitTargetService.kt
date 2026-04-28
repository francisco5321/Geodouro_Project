package com.example.geodouro_backend.visittarget

import org.slf4j.LoggerFactory
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class VisitTargetService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun listVisitTargets(userId: Int): List<VisitTargetResponse> {
        val targets = jdbcTemplate.query(
            VISIT_TARGET_SELECT_SQL + """
                WHERE svt.user_id = :userId
                  AND (svt.notes IS NULL OR svt.notes <> :consumedNote)
                ORDER BY svt.created_at DESC NULLS LAST, svt.saved_visit_target_id DESC
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("consumedNote", CONSUMED_NOTE),
            visitTargetRowMapper
        )
        logger.info("Resolved {} visit targets for userId={}", targets.size, userId)
        return targets
    }

    fun toggleVisitTarget(userId: Int, request: ToggleVisitTargetRequest): ToggleVisitTargetResponse {
        val targetType = normalizeTargetType(request.targetType)
        validateTargetExists(targetType, request.targetId)

        val existingTarget = findExistingTarget(userId, targetType, request.targetId)
        if (existingTarget != null) {
            if (existingTarget.consumed) {
                setVisitTargetConsumed(userId, existingTarget.savedVisitTargetId, consumed = false)
                val target = getVisitTarget(userId, existingTarget.savedVisitTargetId)
                return ToggleVisitTargetResponse(
                    success = true,
                    saved = true,
                    message = addedMessage(targetType),
                    target = target
                )
            }

            deleteVisitTarget(userId, existingTarget.savedVisitTargetId)
            return ToggleVisitTargetResponse(
                success = true,
                saved = false,
                message = removedMessage(targetType),
                target = null
            )
        }

        val savedVisitTargetId = insertVisitTarget(userId, targetType, request.targetId)
        val target = getVisitTarget(userId, savedVisitTargetId)
        return ToggleVisitTargetResponse(
            success = true,
            saved = true,
            message = addedMessage(targetType),
            target = target
        )
    }

    fun deleteVisitTarget(userId: Int, savedVisitTargetId: Int) {
        ensureOwnedVisitTarget(userId, savedVisitTargetId)
        if (isUsedInRoutePlan(savedVisitTargetId)) {
            setVisitTargetConsumed(userId, savedVisitTargetId, consumed = true)
            return
        }

        val affected = jdbcTemplate.update(
            """
                DELETE FROM saved_visit_target
                WHERE user_id = :userId
                  AND saved_visit_target_id = :savedVisitTargetId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("savedVisitTargetId", savedVisitTargetId)
        )

        if (affected == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Alvo de visita não encontrado") //3
        }
    }

    fun getVisitTarget(userId: Int, savedVisitTargetId: Int): VisitTargetResponse {
        return try {
            jdbcTemplate.queryForObject(
                VISIT_TARGET_SELECT_SQL + """
                    WHERE svt.user_id = :userId
                      AND svt.saved_visit_target_id = :savedVisitTargetId
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("savedVisitTargetId", savedVisitTargetId),
                visitTargetRowMapper
            ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Alvo de visita não encontrado") //5
        } catch (_: EmptyResultDataAccessException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Alvo de visita não encontrado") //4
        }
    }

    private fun normalizeTargetType(targetType: String): String {
        return when (targetType.trim().lowercase()) {
            "species", "plant_species", "plant-species" -> "species"
            "publication" -> "publication"
            "observation" -> "observation"
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de alvo inválido") //2
        }
    }

    private fun validateTargetExists(targetType: String, targetId: Int) {
        val (sql, paramName) = when (targetType) {
            "species" -> """
                SELECT COUNT(*)
                FROM plant_species ps
                WHERE ps.plant_species_id = :targetId
                  AND EXISTS (
                      SELECT 1
                      FROM observation o
                      WHERE o.plant_species_id = ps.plant_species_id
                  )
            """.trimIndent() to "targetId"
            "publication" -> "SELECT COUNT(*) FROM publication WHERE publication_id = :targetId" to "targetId"
            "observation" -> """
                SELECT COUNT(*)
                FROM observation
                WHERE observation_id = :targetId
                  AND latitude IS NOT NULL
                  AND longitude IS NOT NULL
            """.trimIndent() to "targetId"
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de alvo inválido")
        }

        val count = jdbcTemplate.queryForObject(
            sql,
            MapSqlParameterSource(paramName, targetId),
            Int::class.java
        ) ?: 0

        if (count == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage(targetType))
        }
    }

    private fun findExistingTarget(userId: Int, targetType: String, targetId: Int): ExistingVisitTarget? {
        val columnName = when (targetType) {
            "species" -> "plant_species_id"
            "publication" -> "publication_id"
            "observation" -> "observation_id"
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de alvo inválido")
        }

        return try {
            jdbcTemplate.queryForObject(
                """
                    SELECT saved_visit_target_id,
                           notes
                    FROM saved_visit_target
                    WHERE user_id = :userId
                      AND $columnName = :targetId
                    LIMIT 1
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("targetId", targetId),
                RowMapper { rs, _ ->
                    ExistingVisitTarget(
                        savedVisitTargetId = rs.getInt("saved_visit_target_id"),
                        consumed = rs.getString("notes") == CONSUMED_NOTE
                    )
                }
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    private fun ensureOwnedVisitTarget(userId: Int, savedVisitTargetId: Int) {
        val count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM saved_visit_target
                WHERE user_id = :userId
                  AND saved_visit_target_id = :savedVisitTargetId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("savedVisitTargetId", savedVisitTargetId),
            Int::class.java
        ) ?: 0

        if (count == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Alvo de visita não encontrado")
        }
    }

    private fun isUsedInRoutePlan(savedVisitTargetId: Int): Boolean {
        val count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM route_plan_point
                WHERE saved_visit_target_id = :savedVisitTargetId
            """.trimIndent(),
            MapSqlParameterSource("savedVisitTargetId", savedVisitTargetId),
            Int::class.java
        ) ?: 0

        return count > 0
    }

    private fun setVisitTargetConsumed(userId: Int, savedVisitTargetId: Int, consumed: Boolean) {
        jdbcTemplate.update(
            """
                UPDATE saved_visit_target
                SET notes = :notes
                WHERE user_id = :userId
                  AND saved_visit_target_id = :savedVisitTargetId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("savedVisitTargetId", savedVisitTargetId)
                .addValue("notes", if (consumed) CONSUMED_NOTE else null)
        )
    }

    private fun insertVisitTarget(userId: Int, targetType: String, targetId: Int): Int {
        val columnName = when (targetType) {
            "species" -> "plant_species_id"
            "publication" -> "publication_id"
            "observation" -> "observation_id"
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de alvo inválido")
        }

        return jdbcTemplate.queryForObject(
            """
                INSERT INTO saved_visit_target (user_id, $columnName)
                VALUES (:userId, :targetId)
                RETURNING saved_visit_target_id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("targetId", targetId),
            Int::class.java
        ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível guardar o alvo")
    }

    private fun addedMessage(targetType: String): String = when (targetType) {
        "species" -> "Espécie adicionada a Quero visitar."
        "publication" -> "Publicação adicionada a Quero visitar."
        "observation" -> "Observação adicionada a Quero visitar."
        else -> "Alvo adicionado a Quero visitar."
    }

    private fun removedMessage(targetType: String): String = when (targetType) {
        "species" -> "Espécie removida da tua lista de visita."
        "publication" -> "Publicação removida da tua lista de visita."
        "observation" -> "Observação removida da tua lista de visita."
        else -> "Alvo removido da tua lista de visita."
    }

    private fun notFoundMessage(targetType: String): String = when (targetType) {
        "species" -> "Espécie não encontrada."
        "publication" -> "Publicação não encontrada."
        "observation" -> "Observação não encontrada."
        else -> "Alvo não encontrado."
    }

    companion object {
        const val CONSUMED_NOTE = "__geodouro_route_consumed__"
        private val logger = LoggerFactory.getLogger(VisitTargetService::class.java)
        private val visitTargetRowMapper = RowMapper { rs, _ ->
            VisitTargetResponse(
                savedVisitTargetId = rs.getInt("saved_visit_target_id"),
                userId = rs.getInt("user_id"),
                targetType = rs.getString("target_type"),
                title = rs.getString("title"),
                subtitle = rs.getString("subtitle"),
                notes = rs.getString("notes"),
                observationId = rs.getObject("observation_id", java.lang.Integer::class.java)?.toInt(),
                plantSpeciesId = rs.getObject("plant_species_id", java.lang.Integer::class.java)?.toInt(),
                publicationId = rs.getObject("publication_id", java.lang.Integer::class.java)?.toInt(),
                latitude = rs.getBigDecimal("latitude")?.toDouble(),
                longitude = rs.getBigDecimal("longitude")?.toDouble(),
                createdAt = rs.getString("created_at")
            )
        }

        private const val VISIT_TARGET_SELECT_SQL = """
            SELECT svt.saved_visit_target_id,
                   svt.user_id,
                   CASE
                       WHEN svt.observation_id IS NOT NULL THEN 'observation'
                       WHEN svt.publication_id IS NOT NULL THEN 'publication'
                       WHEN svt.plant_species_id IS NOT NULL THEN 'species'
                       ELSE 'unknown'
                   END AS target_type,
                   CASE
                       WHEN svt.observation_id IS NOT NULL THEN COALESCE(
                           NULLIF(obs_target.enriched_common_name, ''),
                           NULLIF(species_target.common_name, ''),
                           NULLIF(species_target.scientific_name, ''),
                           'Observação botânica'
                       )
                       WHEN svt.publication_id IS NOT NULL THEN COALESCE(
                           NULLIF(publication_target.title, ''),
                           NULLIF(species_target.common_name, ''),
                           NULLIF(publication_observation.enriched_common_name, ''),
                           NULLIF(species_target.scientific_name, ''),
                           'Publicação botânica'
                       )
                       WHEN svt.plant_species_id IS NOT NULL THEN COALESCE(
                           NULLIF(species_target.common_name, ''),
                           NULLIF(species_target.scientific_name, ''),
                           'Espécie selecionada'
                       )
                       ELSE 'Alvo de visita'
                   END AS title,
                   CASE
                       WHEN svt.observation_id IS NOT NULL THEN COALESCE(
                           NULLIF(obs_target.enriched_scientific_name, ''),
                           NULLIF(obs_target.predicted_scientific_name, ''),
                           NULLIF(species_target.scientific_name, ''),
                           'Observação com coordenadas'
                       )
                       WHEN svt.publication_id IS NOT NULL THEN COALESCE(
                           NULLIF(species_target.scientific_name, ''),
                           NULLIF(publication_observation.enriched_scientific_name, ''),
                           NULLIF(publication_observation.predicted_scientific_name, ''),
                           'Publicação associada a observação'
                       )
                       WHEN svt.plant_species_id IS NOT NULL THEN COALESCE(
                           NULLIF(species_target.scientific_name, ''),
                           'Sem classificação científ ica'
                       )
                       ELSE NULLIF(svt.notes, '')
                   END AS subtitle,
                   svt.notes,
                   svt.observation_id,
                   COALESCE(svt.plant_species_id, obs_target.plant_species_id, publication_target.plant_species_id, publication_observation.plant_species_id) AS plant_species_id,
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
                   ) AS longitude,
                   svt.created_at
            FROM saved_visit_target svt
            LEFT JOIN observation obs_target ON obs_target.observation_id = svt.observation_id
            LEFT JOIN publication publication_target ON publication_target.publication_id = svt.publication_id
            LEFT JOIN observation publication_observation ON publication_observation.observation_id = publication_target.observation_id
            LEFT JOIN plant_species species_target ON species_target.plant_species_id = COALESCE(
                svt.plant_species_id,
                obs_target.plant_species_id,
                publication_target.plant_species_id,
                publication_observation.plant_species_id
            )
            LEFT JOIN LATERAL (
                SELECT o.latitude,
                       o.longitude
                FROM observation o
                WHERE o.plant_species_id = svt.plant_species_id
                  AND o.latitude IS NOT NULL
                  AND o.longitude IS NOT NULL
                ORDER BY o.observed_at DESC NULLS LAST, o.observation_id DESC
                LIMIT 1
            ) species_observation ON TRUE
        """
    }
}

private data class ExistingVisitTarget(
    val savedVisitTargetId: Int,
    val consumed: Boolean
)
