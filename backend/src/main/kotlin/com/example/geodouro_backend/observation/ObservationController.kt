package com.example.geodouro_backend.observation

import com.example.geodouro_backend.auth.AuthTokenService
import jakarta.validation.Valid
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/observations")
class ObservationController(
    private val observationService: ObservationService,
    private val authTokenService: AuthTokenService
) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upsertObservation(
        @Valid @RequestBody request: UpsertObservationRequest,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ): ObservationResponse {
        val authenticatedUserId = authTokenService.resolveUserId(authorizationHeader)
        logger.info(
            "POST /api/observations deviceObservationId={} predictedScientificName={} guestLabel={} userId={} authenticatedUserId={}",
            request.deviceObservationId,
            request.predictedScientificName,
            request.guestLabel,
            request.userId,
            authenticatedUserId
        )
        return observationService.upsertObservation(request, authenticatedUserId)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upsertObservationWithImages(
        @Valid @RequestPart("metadata") request: UpsertObservationRequest,
        @RequestPart("images", required = false) images: List<MultipartFile>?,
        @RequestPart("image", required = false) image: MultipartFile?,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ): ObservationResponse {
        val uploadedImages = images.orEmpty() + listOfNotNull(image)
        val authenticatedUserId = authTokenService.resolveUserId(authorizationHeader)
        logger.info(
            "POST /api/observations multipart deviceObservationId={} predictedScientificName={} guestLabel={} userId={} authenticatedUserId={} imageCount={}",
            request.deviceObservationId,
            request.predictedScientificName,
            request.guestLabel,
            request.userId,
            authenticatedUserId,
            uploadedImages.size
        )
        return observationService.upsertObservation(request, uploadedImages, authenticatedUserId)
    }

    @GetMapping
    fun listObservations(
        @RequestParam(required = false) userId: Int?,
        @RequestParam(required = false) guestLabel: String?,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ): List<ObservationDetailResponse> {
        val authenticatedUserId = authTokenService.resolveUserId(authorizationHeader)
        logger.info("GET /api/observations userId={} guestLabel={} authenticatedUserId={}", userId, guestLabel, authenticatedUserId)
        return observationService.listObservations(userId, guestLabel, authenticatedUserId)
    }

    @GetMapping("/{deviceObservationId}")
    fun getByDeviceObservationId(
        @PathVariable deviceObservationId: UUID,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ): ObservationDetailResponse {
        val authenticatedUserId = authTokenService.resolveUserId(authorizationHeader)
        logger.info("GET /api/observations/{} authenticatedUserId={}", deviceObservationId, authenticatedUserId)
        return observationService.getObservationDetail(deviceObservationId, authenticatedUserId)
    }

    @PatchMapping("/{deviceObservationId}")
    fun updateObservationMetadata(
        @PathVariable deviceObservationId: UUID,
        @RequestBody request: UpdateObservationMetadataRequest,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ): ObservationDetailResponse {
        val authenticatedUserId = authTokenService.resolveUserId(authorizationHeader)
        logger.info("PATCH /api/observations/{} authenticatedUserId={}", deviceObservationId, authenticatedUserId)
        return observationService.updateObservationMetadata(deviceObservationId, request, authenticatedUserId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ObservationController::class.java)
    }
}
