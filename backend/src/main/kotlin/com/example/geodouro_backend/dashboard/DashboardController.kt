package com.example.geodouro_backend.dashboard

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
    private val dashboardService: DashboardService
) {
    @GetMapping("/stats")
    fun getStats(): DashboardStatsResponse {
        logger.info("GET /api/dashboard/stats")
        return dashboardService.getStats()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DashboardController::class.java)
    }
}
