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

data class InferencePrediction(
    val label: String,
    val confidence: Float,
    val fromModel: Boolean,
    val candidates: List<InferenceCandidate> = emptyList()
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
        val probabilities = runModelProbabilities(bitmap)
            ?: return@withContext InferencePrediction(
                label = FALLBACK_LABEL,
                confidence = 0f,
                fromModel = false,
                candidates = emptyList()
            )

        if (probabilities.isEmpty()) {
            return@withContext InferencePrediction(
                label = FALLBACK_LABEL,
                confidence = 0f,
                fromModel = false,
                candidates = emptyList()
            )
        }

        val rankedCandidates = probabilities.indices
            .map { index ->
                InferenceCandidate(
                    label = labels.getOrNull(index) ?: "classe_$index",
                    confidence = probabilities[index]
                )
            }
            .sortedByDescending { it.confidence }

        val bestPrediction = rankedCandidates.firstOrNull()
            ?: return@withContext InferencePrediction(
                label = FALLBACK_LABEL,
                confidence = 0f,
                fromModel = false,
                candidates = emptyList()
            )

        val confidentCandidates = rankedCandidates
            .filter { it.confidence >= MIN_DISPLAY_CONFIDENCE }
            .take(MAX_DISPLAY_CANDIDATES)

        val exportCandidates = if (confidentCandidates.isNotEmpty()) {
            confidentCandidates
        } else {
            rankedCandidates.take(MAX_DISPLAY_CANDIDATES)
        }

        InferencePrediction(
            label = bestPrediction.label,
            confidence = bestPrediction.confidence,
            fromModel = true,
            candidates = exportCandidates
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
            modelLoadDiagnostic = "Asset do modelo nao encontrado em assets/$resolvedModelFileName"
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
            return "Arquivo invalido para PyTorch Android. Exporte para TorchScript (.pt ou .ptl)."
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
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        return TensorImageUtils.bitmapToFloat32Tensor(scaled, TORCHVISION_MEAN_RGB, TORCHVISION_STD_RGB)
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
        const val MIN_DISPLAY_CONFIDENCE = 0.15f

        private const val MAX_DISPLAY_CANDIDATES = 5

        private val TORCHVISION_MEAN_RGB = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val TORCHVISION_STD_RGB = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
