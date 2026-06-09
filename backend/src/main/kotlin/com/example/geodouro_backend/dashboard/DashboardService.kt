package com.example.geodouro_backend.dashboard

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class DashboardService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun getStats(): DashboardStatsResponse {
        return DashboardStatsResponse(
            speciesCount = countPublicSpecies(),
            observationCount = count("observation"),
            manualReviewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM observation WHERE requires_manual_identification = TRUE",
                emptyMap<String, Any>(),
                Int::class.java
            ) ?: 0,
            publicationCount = count("publication"),
            userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE is_authenticated = TRUE",
                emptyMap<String, Any>(),
                Int::class.java
            ) ?: 0
        )
    }

    private fun count(tableName: String): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM $tableName",
            emptyMap<String, Any>(),
            Int::class.java
        ) ?: 0
    }

    private fun countPublicObservations(): Int {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM observation
            WHERE requires_manual_identification = FALSE
              AND (
                  is_published = TRUE
                  OR sync_status = 'SYNCED'
              )
            """.trimIndent(),
            emptyMap<String, Any>(),
            Int::class.java
        ) ?: 0
    }

    private fun countPublicSpecies(): Int {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT COALESCE(o.plant_species_id, ps.plant_species_id))
            FROM observation o
            LEFT JOIN plant_species ps
              ON ps.scientific_name = COALESCE(o.enriched_scientific_name, o.predicted_scientific_name)
            WHERE o.requires_manual_identification = FALSE
              AND (
                  o.is_published = TRUE
                  OR o.sync_status = 'SYNCED'
              )
              AND COALESCE(o.plant_species_id, ps.plant_species_id) IS NOT NULL
            """.trimIndent(),
            emptyMap<String, Any>(),
            Int::class.java
        ) ?: 0
    }
}
