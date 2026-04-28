package com.example.geodouro_project.ai

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils

data class InferenceCandidate(
    val label: String,
    val confidence: Float
)

enum class RejectionReason {
    UNKNOWN_PLANT,
    NON_PLANT
}

data class InferencePrediction(
    val label: String,
    val confidence: Float,
    val fromModel: Boolean,
    val candidates: List<InferenceCandidate> = emptyList(),
    val rejectionReason: RejectionReason? = null
)

data class InferenceAnalysis(
    val prediction: InferencePrediction,
    val embedding: FloatArray?
)

class MobileNetV3Classifier(
    private val context: Context,
    private val modelFileName: String = DEFAULT_MODEL_FILE,
    private val labelsFileName: String = DEFAULT_LABELS_FILE,
    private val inputSize: Int = DEFAULT_INPUT_SIZE
) {

    private val labels: List<String> by lazy { loadLabels() }
    private val resolvedModelFileName: String by lazy { resolveModelFileName() }
    private val module: Module? by lazy { createModuleOrNull() }
    @Volatile
    private var modelLoadDiagnostic: String? = null

    fun isModelAvailable(): Boolean = module != null
    fun getModelLoadDiagnostic(): String? = modelLoadDiagnostic

    suspend fun extractEmbedding(bitmap: Bitmap): FloatArray? = withContext(Dispatchers.Default) {
        runModelProbabilities(bitmap)
    }

    suspend fun classify(bitmap: Bitmap): InferencePrediction = withContext(Dispatchers.Default) {
        predictionFromProbabilities(runModelProbabilities(bitmap))
    }

    suspend fun analyze(bitmap: Bitmap): InferenceAnalysis = withContext(Dispatchers.Default) {
        val probabilities = runModelProbabilities(bitmap)
        InferenceAnalysis(
            prediction = predictionFromProbabilities(probabilities),
            embedding = probabilities
        )
    }

    private fun shouldRejectAsNonPlant(rankedCandidates: List<InferenceCandidate>): Boolean {
        val bestPrediction = rankedCandidates.firstOrNull() ?: return true
        val secondPrediction = rankedCandidates.getOrNull(1)
        val margin = bestPrediction.confidence - (secondPrediction?.confidence ?: 0f)
        val normalizedLabel = normalizeLabel(bestPrediction.label)

        if (normalizedLabel in KNOWN_NON_PLANT_LABELS) {
            return true
        }

        if (bestPrediction.confidence < MIN_PLANT_CONFIDENCE) {
            return true
        }

        return bestPrediction.confidence < LOW_CONFIDENCE_PLANT_THRESHOLD && margin < MIN_CONFIDENCE_MARGIN
    }

    private fun getRejectionReason(rankedCandidates: List<InferenceCandidate>): RejectionReason {
        val bestPrediction = rankedCandidates.firstOrNull() ?: return RejectionReason.UNKNOWN_PLANT
        val secondPrediction = rankedCandidates.getOrNull(1)
        val margin = bestPrediction.confidence - (secondPrediction?.confidence ?: 0f)
        val normalizedLabel = normalizeLabel(bestPrediction.label)

        // É um label conhecido que não é uma planta
        if (normalizedLabel in KNOWN_NON_PLANT_LABELS) {
            return RejectionReason.NON_PLANT
        }

        // Confiança muito baixa para uma planta = planta desconhecida
        if (bestPrediction.confidence < MIN_PLANT_CONFIDENCE) {
            return RejectionReason.UNKNOWN_PLANT
        }

        // Baixa confiança sem margem clara = planta desconhecida
        if (bestPrediction.confidence < LOW_CONFIDENCE_PLANT_THRESHOLD && margin < MIN_CONFIDENCE_MARGIN) {
            return RejectionReason.UNKNOWN_PLANT
        }

        return RejectionReason.UNKNOWN_PLANT
    }

    private fun rejectionConfidence(bestPrediction: InferenceCandidate): Float {
        val inverted = 1f - bestPrediction.confidence
        return inverted.coerceIn(MIN_NON_PLANT_CONFIDENCE, MAX_NON_PLANT_CONFIDENCE)
    }

    private fun normalizeLabel(label: String): String {
        return label.trim().lowercase().replace('_', ' ')
    }

    private fun predictionFromProbabilities(probabilities: FloatArray?): InferencePrediction {
        if (probabilities == null || probabilities.isEmpty()) {
            return fallbackPrediction()
        }

        val rankedCandidates = probabilities.indices
            .map { index ->
                InferenceCandidate(
                    label = labels.getOrNull(index) ?: "classe_$index",
                    confidence = probabilities[index]
                )
            }
            .sortedByDescending { it.confidence }

        val bestPrediction = rankedCandidates.firstOrNull() ?: return fallbackPrediction()

        if (shouldRejectAsNonPlant(rankedCandidates)) {
            val rejectionReason = getRejectionReason(rankedCandidates)
            val label = when (rejectionReason) {
                RejectionReason.NON_PLANT -> NON_PLANT_LABEL
                RejectionReason.UNKNOWN_PLANT -> UNKNOWN_PLANT_LABEL
            }
            return InferencePrediction(
                label = label,
                confidence = rejectionConfidence(bestPrediction),
                fromModel = true,
                candidates = emptyList(),
                rejectionReason = rejectionReason
            )
        }

        val confidentCandidates = rankedCandidates
            .filter { it.confidence >= MIN_DISPLAY_CONFIDENCE }
            .take(MAX_DISPLAY_CANDIDATES)

        val exportCandidates = if (confidentCandidates.isNotEmpty()) {
            confidentCandidates
        } else {
            rankedCandidates.take(MAX_DISPLAY_CANDIDATES)
        }

        return InferencePrediction(
            label = bestPrediction.label,
            confidence = bestPrediction.confidence,
            fromModel = true,
            candidates = exportCandidates
        )
    }

    private fun fallbackPrediction(): InferencePrediction {
        return InferencePrediction(
            label = FALLBACK_LABEL,
            confidence = 0f,
            fromModel = false,
            candidates = emptyList()
        )
    }

    private fun runModelProbabilities(bitmap: Bitmap): FloatArray? {
        val localModule = module ?: return null
        val inputTensor = preprocessBitmap(bitmap)
        val outputTensor = localModule
            .forward(IValue.from(inputTensor))
            .toTensor()
        val rawOutput = outputTensor.dataAsFloatArray

        if (rawOutput.isEmpty()) {
            modelLoadDiagnostic = "Saida vazia do modelo PyTorch."
            return null
        }

        if (!validateOutputAgainstLabels(rawOutput.size)) {
            return null
        }

        modelLoadDiagnostic = null
        return toProbabilities(rawOutput)
    }

    private fun validateOutputAgainstLabels(outputSize: Int): Boolean {
        if (labels.isEmpty()) {
            modelLoadDiagnostic =
                "Ficheiro de labels vazio ou ausente (assets/$labelsFileName)."
            return false
        }

        if (labels.size != outputSize) {
            modelLoadDiagnostic =
                "Incompatibilidade modelo/labels: output=$outputSize labels=${labels.size} (assets/$labelsFileName)."
            return false
        }

        return true
    }

    private fun createModuleOrNull(): Module? {
        val modelPath = try {
            copyAssetToFilesDir(resolvedModelFileName)
        } catch (exception: Exception) {
            modelLoadDiagnostic = "Asset do modelo não encontrado em assets/$resolvedModelFileName"
            return null
        }

        return try {
            Module.load(modelPath).also {
                modelLoadDiagnostic = null
            }
        } catch (loaderException: Exception) {
            modelLoadDiagnostic = buildLoadDiagnostic(loaderException)
            null
        }
    }

    private fun buildLoadDiagnostic(loaderException: Exception): String {
        val errorMessage = loaderException.message?.trim().orEmpty()

        val probablyNotTorchScript =
            errorMessage.contains("PytorchStreamReader", ignoreCase = true) ||
                errorMessage.contains("constants.pkl", ignoreCase = true)

        if (probablyNotTorchScript) {
            return "Arquivo inválido para PyTorch Android. Exporte para TorchScript (.pt ou .ptl)."
        }

        if (errorMessage.isNotBlank()) {
            return "Falha ao abrir o modelo: $errorMessage"
        }

        return "Falha ao abrir o modelo no Android (Module.load)."
    }

    private fun resolveModelFileName(): String {
        val assetNames = context.assets.list("")?.toSet().orEmpty()

        return when {
            assetNames.contains(modelFileName) -> modelFileName
            assetNames.contains(DEFAULT_MODEL_FILE) -> DEFAULT_MODEL_FILE
            assetNames.contains(LEGACY_MODEL_FILE) -> LEGACY_MODEL_FILE
            else -> modelFileName
        }
    }

    private fun copyAssetToFilesDir(assetName: String): String {
        val destinationFile = File(context.filesDir, assetName)
        val assetLength = runCatching {
            context.assets.openFd(assetName).use { descriptor -> descriptor.length }
        }.getOrNull()

        if (
            destinationFile.exists() &&
            destinationFile.length() > 0 &&
            (assetLength == null || destinationFile.length() == assetLength)
        ) {
            return destinationFile.absolutePath
        }

        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(destinationFile, false).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return destinationFile.absolutePath
    }

    private fun loadLabels(): List<String> {
        return try {
            context.assets.open(labelsFileName).bufferedReader().useLines { lines ->
                lines
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): Tensor {
        val scaled = if (bitmap.width == inputSize && bitmap.height == inputSize) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        }

        return try {
            TensorImageUtils.bitmapToFloat32Tensor(scaled, TORCHVISION_MEAN_RGB, TORCHVISION_STD_RGB)
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }
    }

    private fun toProbabilities(rawOutput: FloatArray): FloatArray {
        if (rawOutput.isEmpty()) {
            return rawOutput
        }

        val appearsProbability = rawOutput.all { it in 0f..1f }
        val probabilitySum = rawOutput.sum()
        if (appearsProbability && probabilitySum in 0.99f..1.01f) {
            return rawOutput
        }

        val maxLogit = rawOutput.maxOrNull() ?: 0f
        val expValues = FloatArray(rawOutput.size)
        var sum = 0f

        rawOutput.indices.forEach { index ->
            val value = exp(rawOutput[index] - maxLogit)
            expValues[index] = value
            sum += value
        }

        if (sum <= 0f) {
            return FloatArray(rawOutput.size)
        }

        return FloatArray(rawOutput.size) { index -> expValues[index] / sum }
    }

    companion object {
        const val DEFAULT_MODEL_FILE = "mobilenetv3_small_best_android.pt"
        const val LEGACY_MODEL_FILE = "mobilenetv3_small_best.pt"
        const val DEFAULT_LABELS_FILE = "species_labels.txt"
        const val DEFAULT_INPUT_SIZE = 224
        const val MODEL_DISPLAY_NAME = "MobileNetV3-Small (PyTorch)"
        const val FALLBACK_LABEL = "Modelo PyTorch ainda indisponivel"
        const val NON_PLANT_LABEL = "Não é uma planta"
        const val UNKNOWN_PLANT_LABEL = "Não conhecemos essa planta"
        const val MIN_DISPLAY_CONFIDENCE = 0.15f

        private const val MAX_DISPLAY_CANDIDATES = 5
        private const val MIN_PLANT_CONFIDENCE = 0.30f
        private const val LOW_CONFIDENCE_PLANT_THRESHOLD = 0.50f
        private const val MIN_CONFIDENCE_MARGIN = 0.12f
        private const val MIN_NON_PLANT_CONFIDENCE = 0.55f
        private const val MAX_NON_PLANT_CONFIDENCE = 0.95f

        private val KNOWN_NON_PLANT_LABELS = setOf(
            "actinia equina",
            "chamaeleon gummifer",
            "podarcis bocagei",
            "pluvialis squatarola"
        )

        private val TORCHVISION_MEAN_RGB = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val TORCHVISION_STD_RGB = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
