package com.example.geodouro_backend.observation

import jakarta.validation.Valid
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/observations")
class ObservationController(
    private val observationService: ObservationService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun upsertObservation(@Valid @RequestBody request: UpsertObservationRequest): ObservationResponse {
        logger.info(
            "POST /api/observations deviceObservationId={} predictedScientificName={} guestLabel={} userId={}",
            request.deviceObservationId,
            request.predictedScientificName,
            request.guestLabel,
            request.userId
        )

        val response = observationService.upsertObservation(request)

        logger.info(
            "Observation persisted observationId={} deviceObservationId={} syncStatus={}",
            response.observationId,
            response.deviceObservationId,
            response.syncStatus
        )

        return response
    }

    @GetMapping("/{deviceObservationId}")
    fun getByDeviceObservationId(@PathVariable deviceObservationId: UUID): ObservationResponse {
        logger.info("GET /api/observations/{}", deviceObservationId)
        return observationService.getByDeviceObservationId(deviceObservationId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ObservationController::class.java)
    }
}
