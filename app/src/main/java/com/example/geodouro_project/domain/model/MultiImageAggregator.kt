package com.example.geodouro_project.domain.model

class MultiImageAggregator(
    private val confidencePolicy: ConfidencePolicy = ConfidencePolicy(),
    private val nonPlantLabel: String
) {

    fun aggregate(
        imageResults: List<ImageInferenceResult>,
        config: MultiImageAggregationConfig = MultiImageAggregationConfig(),
        processingTimeMs: Long = 0
    ): MultiImageAggregationResult {
        require(imageResults.isNotEmpty()) {
            "Lista de imagens não pode estar vazia"
        }

        val successfulResults = imageResults.filter {
            it.predictedSpecies != UNKNOWN_LABEL && it.predictedSpecies != ERROR_LABEL
        }
        val validResults = successfulResults.filterNot { isNonPlantPrediction(it.predictedSpecies) }

        require(successfulResults.isNotEmpty()) {
            "Nenhuma imagem foi classificada com sucesso"
        }

        if (validResults.isEmpty()) {
            val nonPlantConfidence = successfulResults
                .filter { isNonPlantPrediction(it.predictedSpecies) }
                .map { it.confidence }
                .average()
                .takeIf { !it.isNaN() }
                ?.toFloat()
                ?: DEFAULT_NON_PLANT_CONFIDENCE

            return MultiImageAggregationResult(
                finalPredictedSpecies = nonPlantLabel,
                aggregatedConfidence = nonPlantConfidence,
                confidenceState = ConfidenceState.ABSTAIN,
                speciesVotes = emptyMap(),
                confidencePerSpecies = emptyMap(),
                processedImages = successfulResults,
                totalImagesAnalyzed = imageResults.size,
                processingTimeMs = processingTimeMs
            )
        }

        val speciesVotes = mutableMapOf<String, Int>()
        val confidencePerSpecies = mutableMapOf<String, MutableList<Float>>()

        validResults.forEach { result ->
            val species = result.predictedSpecies
            speciesVotes[species] = (speciesVotes[species] ?: 0) + 1
            confidencePerSpecies.getOrPut(species) { mutableListOf() }.add(result.confidence)
        }

        val sortedSpecies = speciesVotes.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { confidencePerSpecies[it.key]?.average() ?: 0.0 }
            )

        val finalSpecies = sortedSpecies.firstOrNull()?.key ?: UNKNOWN_LABEL
        val finalConfidences = confidencePerSpecies[finalSpecies].orEmpty()
        val aggregatedConfidence = finalConfidences.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f

        val topAlternative = sortedSpecies.getOrNull(1)?.key
        val topAlternativeConfidence = topAlternative
            ?.let { confidencePerSpecies[it] }
            .orEmpty()
            .average()
            .takeIf { !it.isNaN() }
            ?.toFloat()

        val confidenceState = confidencePolicy.evaluate(aggregatedConfidence, topAlternativeConfidence)
        val consensusScore = if (speciesVotes.isNotEmpty()) {
            (speciesVotes[finalSpecies] ?: 0).toFloat() / validResults.size
        } else {
            0f
        }

        if (config.requireConsensus && consensusScore < config.minConsensusScore) {
            return MultiImageAggregationResult(
                finalPredictedSpecies = finalSpecies,
                aggregatedConfidence = aggregatedConfidence,
                confidenceState = ConfidenceState.AMBIGUOUS,
                speciesVotes = speciesVotes,
                confidencePerSpecies = confidencePerSpecies.mapValues { it.value.average().toFloat() },
                processedImages = validResults,
                totalImagesAnalyzed = imageResults.size,
                topAlternative = topAlternative,
                topAlternativeConfidence = topAlternativeConfidence,
                processingTimeMs = processingTimeMs
            )
        }

        return MultiImageAggregationResult(
            finalPredictedSpecies = finalSpecies,
            aggregatedConfidence = aggregatedConfidence,
            confidenceState = confidenceState,
            speciesVotes = speciesVotes,
            confidencePerSpecies = confidencePerSpecies.mapValues { it.value.average().toFloat() },
            processedImages = validResults,
            totalImagesAnalyzed = imageResults.size,
            topAlternative = topAlternative,
            topAlternativeConfidence = topAlternativeConfidence,
            processingTimeMs = processingTimeMs
        )
    }

    private fun isNonPlantPrediction(speciesName: String): Boolean {
        return speciesName.trim().equals(nonPlantLabel, ignoreCase = true)
    }

    companion object {
        private const val DEFAULT_NON_PLANT_CONFIDENCE = 0.6f
        private const val UNKNOWN_LABEL = "UNKNOWN"
        private const val ERROR_LABEL = "ERROR"
    }
}
