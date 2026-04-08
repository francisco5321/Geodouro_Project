package com.example.geodouro_backend.routeplan

import com.example.geodouro_backend.auth.AuthTokenService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/route-plans")
class RoutePlanController(
    private val routePlanService: RoutePlanService,
    private val authTokenService: AuthTokenService
) {

    @GetMapping
    fun listRoutePlans(
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?
    ): List<RoutePlanSummaryResponse> {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("GET /api/route-plans userId={}", resolvedUserId)
        return routePlanService.listRoutePlans(resolvedUserId)
    }

    @GetMapping("/{routePlanId}")
    fun getRoutePlanDetail(
        @PathVariable routePlanId: Int,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?
    ): RoutePlanDetailResponse {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("GET /api/route-plans/{} userId={}", routePlanId, resolvedUserId)
        return routePlanService.getRoutePlanDetail(routePlanId, resolvedUserId)
    }

    private fun resolveUserId(authorizationHeader: String?, fallbackUserId: Int?): Int {
        return authTokenService.resolveUserId(authorizationHeader)
            ?: fallbackUserId
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RoutePlanController::class.java)
    }
}
