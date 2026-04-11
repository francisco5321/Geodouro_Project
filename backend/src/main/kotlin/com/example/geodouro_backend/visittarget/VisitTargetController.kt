package com.example.geodouro_backend.visittarget

import com.example.geodouro_backend.auth.AuthTokenService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/visit-targets")
class VisitTargetController(
    private val visitTargetService: VisitTargetService,
    private val authTokenService: AuthTokenService
) {
    @GetMapping
    fun listVisitTargets(
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?
    ): List<VisitTargetResponse> {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("GET /api/visit-targets userId={}", resolvedUserId)
        return visitTargetService.listVisitTargets(resolvedUserId)
    }

    @PostMapping("/toggle")
    fun toggleVisitTarget(
        @Valid @RequestBody request: ToggleVisitTargetRequest,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?
    ): ToggleVisitTargetResponse {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("POST /api/visit-targets/toggle userId={} targetType={} targetId={}", resolvedUserId, request.targetType, request.targetId)
        return visitTargetService.toggleVisitTarget(resolvedUserId, request)
    }

    @DeleteMapping("/{savedVisitTargetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteVisitTarget(
        @PathVariable savedVisitTargetId: Int,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestParam(required = false) userId: Int?
    ) {
        val resolvedUserId = resolveUserId(authorizationHeader, userId)
        logger.info("DELETE /api/visit-targets/{} userId={}", savedVisitTargetId, resolvedUserId)
        visitTargetService.deleteVisitTarget(resolvedUserId, savedVisitTargetId)
    }

    private fun resolveUserId(authorizationHeader: String?, fallbackUserId: Int?): Int {
        return authTokenService.resolveUserId(authorizationHeader)
            ?: fallbackUserId
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(VisitTargetController::class.java)
    }
}
