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
        val bitmapForClassification = when {
            !detectionResult.detectorApplied -> bitmap
            detectionResult.croppedBitmap != null -> detectionResult.croppedBitmap
            else -> {
                return@withContext InferenceAnalysis(
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
        }

        try {
            classifier.analyze(bitmapForClassification)
        } finally {
            if (bitmapForClassification !== bitmap) {
                bitmapForClassification.recycle()
            }
        }
    }

    companion object {
        private const val NO_PLANT_CONFIDENCE = 0.85f
    }
}
