package com.example.geodouro_project.domain.model

/**
 * Resultado de uma única imagem no contexto de análise multi-imagem
 */
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
 * Resultado agregado de múltiplas imagens
 * Combina votação por espécie + média de confiança
 */
data class MultiImageAggregationResult(
    // Resultado agregado
    val finalPredictedSpecies: String,
    val aggregatedConfidence: Float,  // Média ponderada
    val confidenceState: ConfidenceState,
    
    // Votos
    val speciesVotes: Map<String, Int>,  // Espécie -> número de imagens que votaram
    val confidencePerSpecies: Map<String, Float>,  // Espécie -> confiança média
    
    // Imagens processadas
    val processedImages: List<ImageInferenceResult>,
    val totalImagesAnalyzed: Int,
    
    // Confiança individual
    val topAlternative: String? = null,
    val topAlternativeConfidence: Float? = null,
    
    // Metadados
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

    /**
     * Unanimidade: todas as imagens predizem a mesma espécie?
     */
    val isUnanimous: Boolean
        get() = speciesVotes.size == 1 && speciesVotes.values.first() == totalImagesAnalyzed
}

/**
 * Configuração para agregação multi-imagem
 */
data class MultiImageAggregationConfig(
    val minImagesRequired: Int = 1,
    val maxImagesRequired: Int = 5,
    val confidenceWeightedVoting: Boolean = true,  // Votação ponderada por confiança
    val requireConsensus: Boolean = false,         // Exigir unanimidade
    val minConsensusScore: Float = 0.6f            // Mínimo 60% de votos
)
