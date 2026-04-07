package com.example.geodouro_backend.species

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/species")
class SpeciesController(
    private val speciesService: SpeciesService
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

    companion object {
        private val logger = LoggerFactory.getLogger(SpeciesController::class.java)
    }
}
