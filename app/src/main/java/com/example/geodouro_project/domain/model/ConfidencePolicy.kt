package com.example.geodouro_project.domain.model

/**
 * Estados de confianca para decisoes de classificacao.
 */
sealed class ConfidenceState {
    object CONFIDENT : ConfidenceState()
    object AMBIGUOUS : ConfidenceState()
    object ABSTAIN : ConfidenceState()
    object NEEDS_ONLINE_FALLBACK : ConfidenceState()
}

/**
 * Avalia a confianca de um resultado de classificacao
 * e decide quando pedir confirmacao ou fallback.
 */
class ConfidencePolicy {

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.80f
        const val AMBIGUITY_MARGIN = 0.10f
        const val ABSTENTION_THRESHOLD = 0.30f
        const val FALLBACK_THRESHOLD = 0.70f
    }

    /**
     * Avalia o estado de confianca com base na previsao principal e alternativa.
     */
    fun evaluate(
        confidence: Float,
        topAlternative: Float? = null
    ): ConfidenceState {
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

    fun shouldRequestUserConfirmation(state: ConfidenceState): Boolean {
        return state is ConfidenceState.AMBIGUOUS || state is ConfidenceState.ABSTAIN
    }
}
