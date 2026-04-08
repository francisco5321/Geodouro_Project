package com.example.geodouro_backend.publication

import com.example.geodouro_backend.auth.AuthTokenService
import jakarta.validation.Valid
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
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

    companion object {
        private val logger = LoggerFactory.getLogger(PublicationController::class.java)
    }
}
