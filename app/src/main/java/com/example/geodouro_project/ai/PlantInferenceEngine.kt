package com.example.geodouro_project.ai

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlantInferenceEngine(
    private val classifier: MobileNetV3Classifier,
    private val detector: YoloPlantDetector
) {

    fun isModelAvailable(): Boolean = classifier.isModelAvailable()

    fun getModelStatusLabel(): String {
        val classifierStatus = classifier.getModelLoadDiagnostic()?.let {
            "Classificador ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} indisponivel: $it"
        } ?: "Classificador ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} pronto"

        if (detector.isModelAvailable()) {
            return "$classifierStatus. Detector YOLO pronto (conf=${detector.getConfidenceThreshold()})."
        }

        val detectorDiagnostic = detector.getModelLoadDiagnostic()
            ?: "sem asset ou falha ao carregar"
        return "$classifierStatus. Detector YOLO indisponível: $detectorDiagnostic. A usar classificação direta."
    }

    suspend fun classify(bitmap: Bitmap): InferencePrediction = analyze(bitmap).prediction

    suspend fun analyze(bitmap: Bitmap): InferenceAnalysis = withContext(Dispatchers.Default) {
        val detectionResult = detector.detect(bitmap)
        val croppedBitmap = detectionResult.croppedBitmap

        if (!detectionResult.detectorApplied) {
            return@withContext classifier.analyze(bitmap)
        }

        if (croppedBitmap == null) {
            return@withContext nonPlantAnalysis()
        }

        try {
            val fullImageAnalysis = classifier.analyze(bitmap)
            val croppedAnalysis = classifier.analyze(croppedBitmap)
            selectBestAnalysis(
                croppedAnalysis = croppedAnalysis,
                fullImageAnalysis = fullImageAnalysis,
                detectorConfidence = detectionResult.detectorConfidence
            )
        } finally {
            croppedBitmap.recycle()
        }
    }

    private fun selectBestAnalysis(
        croppedAnalysis: InferenceAnalysis,
        fullImageAnalysis: InferenceAnalysis,
        detectorConfidence: Float
    ): InferenceAnalysis {
        val croppedPrediction = croppedAnalysis.prediction
        val fullPrediction = fullImageAnalysis.prediction

        val croppedLooksRejected = croppedPrediction.rejectionReason != null
        val fullLooksRejected = fullPrediction.rejectionReason != null

        if (croppedLooksRejected && !fullLooksRejected) {
            return fullImageAnalysis
        }

        if (!croppedLooksRejected && fullLooksRejected) {
            return croppedAnalysis
        }

        if (
            detectorConfidence < MIN_TRUSTED_DETECTOR_CONFIDENCE &&
            fullPrediction.confidence >= croppedPrediction.confidence
        ) {
            return fullImageAnalysis
        }

        if (
            !fullLooksRejected &&
            fullPrediction.confidence >= croppedPrediction.confidence + FULL_IMAGE_CONFIDENCE_MARGIN
        ) {
            return fullImageAnalysis
        }

        return croppedAnalysis
    }

    private fun nonPlantAnalysis(): InferenceAnalysis {
        return InferenceAnalysis(
            prediction = InferencePrediction(
                label = MobileNetV3Classifier.NON_PLANT_LABEL,
                confidence = NO_PLANT_CONFIDENCE,
                fromModel = true,
                candidates = emptyList(),
                rejectionReason = RejectionReason.NON_PLANT
            ),
            embedding = null
        )
    }

    companion object {
        private const val NO_PLANT_CONFIDENCE = 0.85f
        private const val MIN_TRUSTED_DETECTOR_CONFIDENCE = 0.55f
        private const val FULL_IMAGE_CONFIDENCE_MARGIN = 0.08f
    }
}
