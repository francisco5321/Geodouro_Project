package com.example.geodouro_project.domain.model

/**
 * Estados de confiança para decisões de classificação
 */
sealed class ConfidenceState {
    object CONFIDENT : ConfidenceState()
    object AMBIGUOUS : ConfidenceState()
    object ABSTAIN : ConfidenceState()
    object NEEDS_ONLINE_FALLBACK : ConfidenceState()
}

/**
 * Avalia a confiança de um resultado de classificação
 * e toma decisões sobre quando pedir confirmação ou fallback
 */
class ConfidencePolicy {

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.80f          // Confiante
        const val AMBIGUITY_MARGIN = 0.10f             // Diferença mínima entre top1 e top2
        const val ABSTENTION_THRESHOLD = 0.30f         // Muito baixa
        const val FALLBACK_THRESHOLD = 0.70f           // Moderada (tenta online)
    }

    /**
     * Avalia o estado de confiança baseado em confidence e alternativa
     * 
     * @param confidence Confiança da predição principal (0-1)
     * @param topAlternative Confiança da segunda melhor predição (opcional)
     * @return Estado de confiança (CONFIDENT, AMBIGUOUS, ABSTAIN, NEEDS_ONLINE_FALLBACK)
     */
    fun evaluate(
        confidence: Float,
        topAlternative: Float? = null
    ): ConfidenceState {
        // Se top1 e top2 são próximas → ambíguo
        if (topAlternative != null && (confidence - topAlternative) < AMBIGUITY_MARGIN) {
            return ConfidenceState.AMBIGUOUS
        }

        return when {
            confidence >= CONFIDENCE_THRESHOLD -> ConfidenceState.CONFIDENT
            confidence < ABSTENTION_THRESHOLD -> ConfidenceState.ABSTAIN
            confidence < FALLBACK_THRESHOLD -> ConfidenceState.NEEDS_ONLINE_FALLBACK
            else -> ConfidenceState.AMBIGUOUS
        }
    }

    /**
     * Determina se deve tentar enriquecimento online
     */
    fun shouldTryOnlineFallback(
        state: ConfidenceState,
        hasInternet: Boolean
    ): Boolean {
        return when (state) {
            ConfidenceState.NEEDS_ONLINE_FALLBACK -> hasInternet
            ConfidenceState.AMBIGUOUS -> hasInternet
            else -> false
        }
    }

    /**
     * Determina se deve pedir confirmação ao utilizador
     */
    fun shouldRequestUserConfirmation(state: ConfidenceState): Boolean {
        return state is ConfidenceState.AMBIGUOUS || state is ConfidenceState.ABSTAIN
    }
}
