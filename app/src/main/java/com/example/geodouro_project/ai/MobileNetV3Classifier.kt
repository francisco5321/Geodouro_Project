package com.example.geodouro_project.ai

import android.content.Context
import android.graphics.Bitmap
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter

data class InferencePrediction(
    val label: String,
    val confidence: Float,
    val fromModel: Boolean
)

class MobileNetV3Classifier(
    private val context: Context,
    private val modelFileName: String = DEFAULT_MODEL_FILE,
    private val labelsFileName: String = DEFAULT_LABELS_FILE,
    private val inputSize: Int = DEFAULT_INPUT_SIZE
) {

    private val labels: List<String> by lazy { loadLabels() }
    private val interpreter: Interpreter? by lazy { createInterpreterOrNull() }

    fun isModelAvailable(): Boolean = interpreter != null

    suspend fun classify(bitmap: Bitmap): InferencePrediction = withContext(Dispatchers.Default) {
        val localInterpreter = interpreter
            ?: return@withContext InferencePrediction(
                label = FALLBACK_LABEL,
                confidence = 0f,
                fromModel = false
            )

        val input = preprocessBitmap(bitmap)
        val classCount = localInterpreter.getOutputTensor(0).shape().last()
        val output = Array(1) { FloatArray(classCount) }

        localInterpreter.run(input, output)

        val probabilities = toProbabilities(output[0])
        val bestIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val label = labels.getOrNull(bestIndex) ?: "classe_$bestIndex"

        InferencePrediction(
            label = label,
            confidence = probabilities.getOrNull(bestIndex) ?: 0f,
            fromModel = true
        )
    }

    private fun createInterpreterOrNull(): Interpreter? {
        return try {
            Interpreter(loadModelFile())
        } catch (_: Exception) {
            null
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        FileInputStream(fileDescriptor.fileDescriptor).channel.use { fileChannel ->
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
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

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = ByteBuffer
            .allocateDirect(1 * inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // MobileNetV3 geralmente usa normalizacao em [-1, 1].
            byteBuffer.putFloat((r - 0.5f) / 0.5f)
            byteBuffer.putFloat((g - 0.5f) / 0.5f)
            byteBuffer.putFloat((b - 0.5f) / 0.5f)
        }

        return byteBuffer
    }

    private fun toProbabilities(rawOutput: FloatArray): FloatArray {
        val appearsProbability = rawOutput.all { it in 0f..1f }
        if (appearsProbability) {
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
        const val DEFAULT_MODEL_FILE = "mobilenetv3_small.tflite"
        const val DEFAULT_LABELS_FILE = "species_labels.txt"
        const val DEFAULT_INPUT_SIZE = 224
        const val FALLBACK_LABEL = "Modelo MobileNetV3-Small ainda nao treinado"
    }
}
