package com.example.geodouro_backend.dashboard

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class DashboardService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun getStats(): DashboardStatsResponse {
        return DashboardStatsResponse(
            speciesCount = count("plant_species"),
            observationCount = count("observation"),
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
}
