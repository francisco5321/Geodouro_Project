package com.example.geodouro_backend.species

import com.example.geodouro_backend.auth.AuthTokenService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/species")
class SpeciesController(
    private val speciesService: SpeciesService,
    private val authTokenService: AuthTokenService
) {

    @GetMapping
    fun listSpecies(): List<PlantSpeciesResponse> {
        logger.info("GET /api/species")
        return speciesService.listSpecies()
    }

    @GetMapping("/{speciesId}")
    fun getSpeciesDetail(@PathVariable speciesId: String): PlantSpeciesDetailResponse {
        logger.info("GET /api/species/{}", speciesId)
        return speciesService.getSpeciesDetail(speciesId)
    }

    @PatchMapping("/{speciesId}")
    fun updateSpecies(
        @PathVariable speciesId: String,
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @RequestBody request: UpdatePlantSpeciesRequest
    ): PlantSpeciesDetailResponse {
        val requesterId = authTokenService.resolveUserId(authorizationHeader)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria")
        logger.info("PATCH /api/species/{} requesterId={}", speciesId, requesterId)
        return speciesService.updateSpecies(speciesId, requesterId, request)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SpeciesController::class.java)
    }
}
