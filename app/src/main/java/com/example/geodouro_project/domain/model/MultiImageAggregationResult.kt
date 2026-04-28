package com.example.geodouro_project.domain.model

/** Resultado de uma unica imagem no contexto de analise multi-imagem. */
data class ImageInferenceResult(
    val imageUri: String,
    val predictedSpecies: String,
    val confidence: Float,
    val candidatePredictions: List<LocalPredictionCandidate>,
    val embedding: FloatArray? = null,
    val capturedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageInferenceResult

        if (imageUri != other.imageUri) return false
        if (predictedSpecies != other.predictedSpecies) return false
        if (confidence != other.confidence) return false
        if (candidatePredictions != other.candidatePredictions) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (capturedAt != other.capturedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = imageUri.hashCode()
        result = 31 * result + predictedSpecies.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + candidatePredictions.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + capturedAt.hashCode()
        return result
    }
}

/**
 * Resultado agregado de multiplas imagens.
 * Combina votação por espécie e média de confiança.
 */
data class MultiImageAggregationResult(
    val finalPredictedSpecies: String,
    val aggregatedConfidence: Float,
    val confidenceState: ConfidenceState,
    val speciesVotes: Map<String, Int>,
    val confidencePerSpecies: Map<String, Float>,
    val processedImages: List<ImageInferenceResult>,
    val totalImagesAnalyzed: Int,
    val topAlternative: String? = null,
    val topAlternativeConfidence: Float? = null,
    val aggregatedAt: Long = System.currentTimeMillis(),
    val processingTimeMs: Long = 0
) {
    val uniqueSpeciesCount: Int
        get() = speciesVotes.size

    val consensusScore: Float
        get() {
            val maxVotes = speciesVotes.values.maxOrNull() ?: 0
            return if (totalImagesAnalyzed > 0) maxVotes.toFloat() / totalImagesAnalyzed else 0f
        }

    /** Unanimidade: todas as imagens predizem a mesma espécie. */
    val isUnanimous: Boolean
        get() = speciesVotes.size == 1 && speciesVotes.values.first() == totalImagesAnalyzed
}

/** Configuracao para agregacao multi-imagem. */
data class MultiImageAggregationConfig(
    val minImagesRequired: Int = 1,
    val maxImagesRequired: Int = 5,
    val confidenceWeightedVoting: Boolean = true,
    val requireConsensus: Boolean = false,
    val minConsensusScore: Float = 0.6f
)
