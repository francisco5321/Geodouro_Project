package com.example.geodouro_project.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiImageAggregatorTest {

    private val aggregator = MultiImageAggregator(nonPlantLabel = "Nao e uma planta")

    @Test
    fun `aggregate returns majority species and average confidence`() {
        val result = aggregator.aggregate(
            imageResults = listOf(
                imageResult("Quercus suber", 0.90f),
                imageResult("Quercus suber", 0.70f),
                imageResult("Pinus pinea", 0.80f)
            )
        )

        assertEquals("Quercus suber", result.finalPredictedSpecies)
        assertEquals(0.80f, result.aggregatedConfidence, 0.0001f)
        assertEquals(2, result.speciesVotes["Quercus suber"])
        assertEquals("Pinus pinea", result.topAlternative)
    }

    @Test
    fun `aggregate returns non-plant when all successful results are rejected`() {
        val result = aggregator.aggregate(
            imageResults = listOf(
                imageResult("Nao e uma planta", 0.65f),
                imageResult("Nao e uma planta", 0.75f)
            )
        )

        assertEquals("Nao e uma planta", result.finalPredictedSpecies)
        assertTrue(result.processedImages.all { it.predictedSpecies == "Nao e uma planta" })
        assertTrue(result.speciesVotes.isEmpty())
    }

    @Test
    fun `aggregate marks ambiguous when consensus threshold is not met`() {
        val result = aggregator.aggregate(
            imageResults = listOf(
                imageResult("Quercus suber", 0.80f),
                imageResult("Pinus pinea", 0.79f),
                imageResult("Quercus suber", 0.50f),
                imageResult("Pinus pinea", 0.51f)
            ),
            config = MultiImageAggregationConfig(
                requireConsensus = true,
                minConsensusScore = 0.75f
            )
        )

        assertTrue(result.confidenceState is ConfidenceState.AMBIGUOUS)
        assertEquals(0.5f, result.consensusScore, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `aggregate rejects empty image list`() {
        aggregator.aggregate(emptyList())
    }

    private fun imageResult(
        species: String,
        confidence: Float
    ) = ImageInferenceResult(
        imageUri = "file:///tmp/${species.hashCode()}-$confidence.jpg",
        predictedSpecies = species,
        confidence = confidence,
        candidatePredictions = emptyList()
    )
}
