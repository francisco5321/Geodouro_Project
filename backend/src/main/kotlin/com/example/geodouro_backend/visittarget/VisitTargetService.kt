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
                WHERE rvt.user_id = :userId
                  AND (rvt.notes IS NULL OR rvt.notes <> :consumedNote)
                ORDER BY rvt.created_at DESC NULLS LAST, rvt.saved_visit_target_id DESC
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
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Alvo de visita não encontrado")
        }
    }

    fun getVisitTarget(userId: Int, savedVisitTargetId: Int): VisitTargetResponse {
        return try {
            jdbcTemplate.queryForObject(
                VISIT_TARGET_SELECT_SQL + """
                    WHERE rvt.user_id = :userId
                      AND rvt.saved_visit_target_id = :savedVisitTargetId
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("savedVisitTargetId", savedVisitTargetId),
                visitTargetRowMapper
            ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Alvo de visita não encontrado")
        } catch (_: EmptyResultDataAccessException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Alvo de visita não encontrado")
        }
    }

    private fun normalizeTargetType(targetType: String): String {
        return when (targetType.trim().lowercase()) {
            "species", "plant_species", "plant-species" -> "species"
            "publication" -> "publication"
            "observation" -> "observation"
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de alvo inválido")
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
        "species" -> "Espécie adicionada à lista 'Quero visitar'."
        "publication" -> "Publicação adicionada à lista 'Quero visitar'."
        "observation" -> "Observação adicionada à lista 'Quero visitar'."
        else -> "Alvo adicionado à lista 'Quero visitar'."
    }

    private fun removedMessage(targetType: String): String = when (targetType) {
        "species" -> "Espécie removida da tua lista 'Quero visitar'."
        "publication" -> "Publicação removida da tua lista 'Quero visitar'."
        "observation" -> "Observação removida da tua lista 'Quero visitar'."
        else -> "Alvo removido da tua lista 'Quero visitar'."
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
            SELECT rvt.saved_visit_target_id,
                   rvt.user_id,
                   rvt.target_type,
                   rvt.title,
                   rvt.subtitle,
                   rvt.notes,
                   rvt.observation_id,
                   rvt.plant_species_id,
                   rvt.publication_id,
                   rvt.latitude,
                   rvt.longitude,
                   rvt.created_at
            FROM resolved_visit_target rvt
        """
    }
}

private data class ExistingVisitTarget(
    val savedVisitTargetId: Int,
    val consumed: Boolean
)
