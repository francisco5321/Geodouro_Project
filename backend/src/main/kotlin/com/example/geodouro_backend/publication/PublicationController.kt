package com.example.geodouro_backend.publication

import com.example.geodouro_backend.auth.AuthTokenService
import jakarta.validation.Valid
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/publications")
class PublicationController(
    private val publicationService: PublicationService,
    private val authTokenService: AuthTokenService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun publishObservation(
        @Valid @RequestBody request: PublishObservationRequest,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ): PublicationResponse {
        val authenticatedUserId = authTokenService.resolveUserId(authorizationHeader)
        logger.info("POST /api/publications deviceObservationId={} authenticatedUserId={}", request.deviceObservationId, authenticatedUserId)
        return publicationService.publishObservation(request, authenticatedUserId)
    }

    @GetMapping
    fun listPublications(): List<PublicationResponse> {
        logger.info("GET /api/publications")
        return publicationService.listPublications()
    }

    @GetMapping("/{deviceObservationId}")
    fun getPublication(@PathVariable deviceObservationId: UUID): PublicationResponse {
        logger.info("GET /api/publications/{}", deviceObservationId)
        return publicationService.getPublicationByDeviceObservationId(deviceObservationId)
    }

    @GetMapping("/by-id/{publicationId}")
    fun getPublicationById(@PathVariable publicationId: Int): PublicationResponse {
        logger.info("GET /api/publications/by-id/{}", publicationId)
        return publicationService.getPublicationById(publicationId)
    }

    @PatchMapping("/{publicationId}")
    fun updatePublication(
        @PathVariable publicationId: Int,
        @RequestBody request: SavePublicationRequest,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ): PublicationResponse {
        val authenticatedUserId = authTokenService.resolveUserId(authorizationHeader)
        logger.info("PATCH /api/publications/{} authenticatedUserId={}", publicationId, authenticatedUserId)
        return publicationService.updatePublication(publicationId, request, authenticatedUserId)
    }

    @DeleteMapping("/{publicationId}")
    fun deletePublication(
        @PathVariable publicationId: Int,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ) {
        val authenticatedUserId = authTokenService.resolveUserId(authorizationHeader)
        logger.info("DELETE /api/publications/{} authenticatedUserId={}", publicationId, authenticatedUserId)
        publicationService.deletePublication(publicationId, authenticatedUserId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PublicationController::class.java)
    }
}
