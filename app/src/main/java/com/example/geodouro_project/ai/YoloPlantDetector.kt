package com.example.geodouro_project.ai

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor

data class PlantDetection(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float
)

data class PlantDetectionResult(
    val detection: PlantDetection?,
    val croppedBitmap: Bitmap?,
    val detectorApplied: Boolean,
    val detectorConfidence: Float = 0f
)

class YoloPlantDetector(
    private val context: Context,
    private val modelFileName: String = DEFAULT_MODEL_FILE,
    private val inputSize: Int = DEFAULT_INPUT_SIZE,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    private val iouThreshold: Float = DEFAULT_IOU_THRESHOLD,
    private val cropPaddingRatio: Float = DEFAULT_CROP_PADDING_RATIO
) {

    private val module: Module? by lazy { createModuleOrNull() }
    @Volatile
    private var modelLoadDiagnostic: String? = null

    fun isModelAvailable(): Boolean = module != null
    fun getModelLoadDiagnostic(): String? = modelLoadDiagnostic
    fun getConfidenceThreshold(): Float = confidenceThreshold

    suspend fun detect(bitmap: Bitmap): PlantDetectionResult = withContext(Dispatchers.Default) {
        runDetection(bitmap)
    }

    private fun runDetection(bitmap: Bitmap): PlantDetectionResult {
        val localModule = module ?: return PlantDetectionResult(
            detection = null,
            croppedBitmap = null,
            detectorApplied = false,
            detectorConfidence = 0f
        )

        val scaledBitmap = if (bitmap.width == inputSize && bitmap.height == inputSize) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        }

        return try {
            val inputTensor = bitmapToTensor(scaledBitmap)
            val outputTensor = localModule.forward(IValue.from(inputTensor)).toTensor()
            val outputShape = outputTensor.shape()
            val outputData = outputTensor.dataAsFloatArray

            if (outputShape.size < 3 || outputShape[1].toInt() < 5) {
                modelLoadDiagnostic = "Saida YOLO invalida: shape=${outputShape.joinToString(prefix = "[", postfix = "]")}."
                return PlantDetectionResult(
                    detection = null,
                    croppedBitmap = null,
                    detectorApplied = true,
                    detectorConfidence = 0f
                )
            }

            val parsed = parseBestDetection(
                outputData = outputData,
                channels = outputShape[1].toInt(),
                predictions = outputShape[2].toInt(),
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height
            )
            val croppedBitmap = parsed?.let { cropBitmap(bitmap, it) }

            PlantDetectionResult(
                detection = parsed,
                croppedBitmap = croppedBitmap,
                detectorApplied = true,
                detectorConfidence = parsed?.confidence ?: 0f
            )
        } finally {
            if (scaledBitmap !== bitmap) {
                scaledBitmap.recycle()
            }
        }
    }

    private fun parseBestDetection(
        outputData: FloatArray,
        channels: Int,
        predictions: Int,
        sourceWidth: Int,
        sourceHeight: Int
    ): PlantDetection? {
        var bestRawConfidence = 0f
        val candidates = ArrayList<MutableDetection>()

        for (index in 0 until predictions) {
            val confidence = outputData[4 * predictions + index]
            if (confidence > bestRawConfidence) {
                bestRawConfidence = confidence
            }
            if (confidence < confidenceThreshold) {
                continue
            }

            val centerX = outputData[index]
            val centerY = outputData[predictions + index]
            val width = outputData[2 * predictions + index]
            val height = outputData[3 * predictions + index]
            if (width <= 0f || height <= 0f) {
                continue
            }

            val left = ((centerX - width / 2f) * sourceWidth / inputSize.toFloat()).toInt()
            val top = ((centerY - height / 2f) * sourceHeight / inputSize.toFloat()).toInt()
            val right = ((centerX + width / 2f) * sourceWidth / inputSize.toFloat()).toInt()
            val bottom = ((centerY + height / 2f) * sourceHeight / inputSize.toFloat()).toInt()

            val clampedLeft = left.coerceIn(0, sourceWidth - 1)
            val clampedTop = top.coerceIn(0, sourceHeight - 1)
            val clampedRight = right.coerceIn(clampedLeft + 1, sourceWidth)
            val clampedBottom = bottom.coerceIn(clampedTop + 1, sourceHeight)

            if ((clampedRight - clampedLeft) < MIN_BOX_SIZE_PX || (clampedBottom - clampedTop) < MIN_BOX_SIZE_PX) {
                continue
            }

            candidates += MutableDetection(
                left = clampedLeft,
                top = clampedTop,
                right = clampedRight,
                bottom = clampedBottom,
                confidence = confidence
            )
        }

        if (candidates.isEmpty()) {
            return null
        }

        val selected = candidates
            .sortedByDescending { it.confidence }
            .fold(mutableListOf<MutableDetection>()) { kept, candidate ->
                if (kept.none { iou(it, candidate) > iouThreshold }) {
                    kept += candidate
                }
                kept
            }
            .maxByOrNull { it.confidence }
            ?: return null

        return PlantDetection(
            left = selected.left,
            top = selected.top,
            right = selected.right,
            bottom = selected.bottom,
            confidence = selected.confidence.coerceIn(confidenceThreshold, 1f)
        )
    }

    private fun cropBitmap(bitmap: Bitmap, detection: PlantDetection): Bitmap {
        val paddingX = ((detection.right - detection.left) * cropPaddingRatio).toInt()
        val paddingY = ((detection.bottom - detection.top) * cropPaddingRatio).toInt()

        val left = (detection.left - paddingX).coerceAtLeast(0)
        val top = (detection.top - paddingY).coerceAtLeast(0)
        val right = (detection.right + paddingX).coerceAtMost(bitmap.width)
        val bottom = (detection.bottom + paddingY).coerceAtMost(bitmap.height)

        val safeWidth = max(1, right - left)
        val safeHeight = max(1, bottom - top)
        return Bitmap.createBitmap(bitmap, left, top, safeWidth, safeHeight)
    }

    private fun bitmapToTensor(bitmap: Bitmap): Tensor {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val floatValues = FloatArray(3 * inputSize * inputSize)
        val channelSize = inputSize * inputSize
        for (index in pixels.indices) {
            val color = pixels[index]
            floatValues[index] = ((color shr 16) and 0xFF) / 255f
            floatValues[channelSize + index] = ((color shr 8) and 0xFF) / 255f
            floatValues[2 * channelSize + index] = (color and 0xFF) / 255f
        }

        return Tensor.fromBlob(
            floatValues,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        )
    }

    private fun iou(first: MutableDetection, second: MutableDetection): Float {
        val intersectionLeft = max(first.left, second.left)
        val intersectionTop = max(first.top, second.top)
        val intersectionRight = min(first.right, second.right)
        val intersectionBottom = min(first.bottom, second.bottom)

        val intersectionWidth = max(0, intersectionRight - intersectionLeft)
        val intersectionHeight = max(0, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight
        if (intersectionArea <= 0) {
            return 0f
        }

        val firstArea = max(1, first.right - first.left) * max(1, first.bottom - first.top)
        val secondArea = max(1, second.right - second.left) * max(1, second.bottom - second.top)
        val unionArea = firstArea + secondArea - intersectionArea
        if (unionArea <= 0) {
            return 0f
        }

        return intersectionArea.toFloat() / unionArea.toFloat()
    }

    private fun createModuleOrNull(): Module? {
        val modelPath = try {
            copyAssetToFilesDir(modelFileName)
        } catch (_: Exception) {
            modelLoadDiagnostic = "Asset do detector nao encontrado em assets/$modelFileName"
            return null
        }

        return try {
            Module.load(modelPath).also {
                modelLoadDiagnostic = null
            }
        } catch (exception: Exception) {
            val message = exception.message?.trim().orEmpty()
            modelLoadDiagnostic = if (message.isBlank()) {
                "Falha ao abrir o detector YOLO no Android."
            } else {
                "Falha ao abrir o detector YOLO: $message"
            }
            null
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

    private data class MutableDetection(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val confidence: Float
    )

    companion object {
        const val DEFAULT_MODEL_FILE = "yolo_plant_detector_android.torchscript"
        const val DEFAULT_INPUT_SIZE = 640
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.35f

        private const val DEFAULT_IOU_THRESHOLD = 0.45f
        private const val DEFAULT_CROP_PADDING_RATIO = 0.08f
        private const val MIN_BOX_SIZE_PX = 24
    }
}
