package com.example.geodouro_backend.routeplan

import com.example.geodouro_backend.auth.AuthTokenService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
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

    @PostMapping
    fun createRoutePlan(
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?,
        @RequestBody request: SaveRoutePlanRequest
    ): RoutePlanMutationResponse {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("POST /api/route-plans userId={}", resolvedUserId)
        return routePlanService.createRoutePlan(resolvedUserId, request)
    }

    @PatchMapping("/{routePlanId}")
    fun updateRoutePlan(
        @PathVariable routePlanId: Int,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?,
        @RequestBody request: SaveRoutePlanRequest
    ): RoutePlanMutationResponse {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("PATCH /api/route-plans/{} userId={}", routePlanId, resolvedUserId)
        return routePlanService.updateRoutePlan(routePlanId, resolvedUserId, request)
    }

    @DeleteMapping("/{routePlanId}")
    fun deleteRoutePlan(
        @PathVariable routePlanId: Int,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?
    ) {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("DELETE /api/route-plans/{} userId={}", routePlanId, resolvedUserId)
        routePlanService.deleteRoutePlan(routePlanId, resolvedUserId)
    }

    @PostMapping("/{routePlanId}/stops/targets/{targetId}")
    fun addTargetToRoutePlan(
        @PathVariable routePlanId: Int,
        @PathVariable targetId: Int,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?
    ): RoutePlanStopMutationResponse {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("POST /api/route-plans/{}/stops/targets/{} userId={}", routePlanId, targetId, resolvedUserId)
        return routePlanService.addSavedTargetToRoutePlan(routePlanId, resolvedUserId, targetId)
    }

    @PostMapping("/{routePlanId}/stops/species/{speciesId}")
    fun addSpeciesToRoutePlan(
        @PathVariable routePlanId: Int,
        @PathVariable speciesId: Int,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?
    ): RoutePlanStopMutationResponse {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("POST /api/route-plans/{}/stops/species/{} userId={}", routePlanId, speciesId, resolvedUserId)
        return routePlanService.addSpeciesToRoutePlan(routePlanId, resolvedUserId, speciesId)
    }

    @PostMapping("/{routePlanId}/stops/observations/{observationId}/toggle")
    fun toggleObservationPoint(
        @PathVariable routePlanId: Int,
        @PathVariable observationId: Int,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?
    ): RoutePlanStopMutationResponse {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("POST /api/route-plans/{}/stops/observations/{}/toggle userId={}", routePlanId, observationId, resolvedUserId)
        return routePlanService.toggleObservationPoint(routePlanId, resolvedUserId, observationId)
    }

    @DeleteMapping("/stops/{routePlanPointId}")
    fun removeRoutePlanPoint(
        @PathVariable routePlanPointId: Int,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?
    ): RoutePlanMutationResponse {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("DELETE /api/route-plans/stops/{} userId={}", routePlanPointId, resolvedUserId)
        val routePlanId = routePlanService.removeRoutePlanPoint(routePlanPointId, resolvedUserId)
        return RoutePlanMutationResponse(true, "Ponto removido do percurso.", routePlanId)
    }

    private fun resolveUserId(authorizationHeader: String?, fallbackUserId: Int?): Int {
        return authTokenService.resolveUserId(authorizationHeader)
            ?: fallbackUserId
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RoutePlanController::class.java)
    }
}
