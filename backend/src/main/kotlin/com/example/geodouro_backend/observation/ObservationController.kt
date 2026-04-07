package com.example.geodouro_backend.observation

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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/observations")
class ObservationController(
    private val observationService: ObservationService
) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upsertObservation(@Valid @RequestBody request: UpsertObservationRequest): ObservationResponse {
        logger.info(
            "POST /api/observations deviceObservationId={} predictedScientificName={} guestLabel={} userId={}",
            request.deviceObservationId,
            request.predictedScientificName,
            request.guestLabel,
            request.userId
        )
        return observationService.upsertObservation(request)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upsertObservationWithImages(
        @Valid @RequestPart("metadata") request: UpsertObservationRequest,
        @RequestPart("images", required = false) images: List<MultipartFile>?,
        @RequestPart("image", required = false) image: MultipartFile?
    ): ObservationResponse {
        val uploadedImages = images.orEmpty() + listOfNotNull(image)
        logger.info(
            "POST /api/observations multipart deviceObservationId={} predictedScientificName={} guestLabel={} userId={} imageCount={}",
            request.deviceObservationId,
            request.predictedScientificName,
            request.guestLabel,
            request.userId,
            uploadedImages.size
        )
        return observationService.upsertObservation(request, uploadedImages)
    }

    @GetMapping
    fun listObservations(
        @RequestParam(required = false) userId: Int?,
        @RequestParam(required = false) guestLabel: String?
    ): List<ObservationDetailResponse> {
        logger.info("GET /api/observations userId={} guestLabel={}", userId, guestLabel)
        return observationService.listObservations(userId, guestLabel)
    }

    @GetMapping("/{deviceObservationId}")
    fun getByDeviceObservationId(@PathVariable deviceObservationId: UUID): ObservationDetailResponse {
        logger.info("GET /api/observations/{}", deviceObservationId)
        return observationService.getObservationDetail(deviceObservationId)
    }

    @PatchMapping("/{deviceObservationId}")
    fun updateObservationMetadata(
        @PathVariable deviceObservationId: UUID,
        @RequestBody request: UpdateObservationMetadataRequest
    ): ObservationDetailResponse {
        logger.info("PATCH /api/observations/{}", deviceObservationId)
        return observationService.updateObservationMetadata(deviceObservationId, request)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ObservationController::class.java)
    }
}
