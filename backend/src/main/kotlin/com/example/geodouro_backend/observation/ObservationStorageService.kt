package com.example.geodouro_backend.observation

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class ObservationStorageService(
    @Value("\${app.storage.images-dir:backend-uploads}") storageRoot: String
) {
    private val rootPath: Path = Paths.get(storageRoot).toAbsolutePath().normalize()

    init {
        Files.createDirectories(rootPath)
    }

    fun storeObservationImage(deviceObservationId: UUID, image: MultipartFile): String {
        require(!image.isEmpty) { "Uploaded image is empty" }

        val extension = resolveExtension(image)
        val observationDirectory = rootPath.resolve(deviceObservationId.toString())
        Files.createDirectories(observationDirectory)

        val targetFile = observationDirectory.resolve("original$extension")
        image.inputStream.use { inputStream ->
            copyReplacing(inputStream, targetFile)
        }

        return rootPath.relativize(targetFile).toString().replace('\\', '/')
    }

    private fun resolveExtension(image: MultipartFile): String {
        val originalName = image.originalFilename.orEmpty()
        val filenameExtension = originalName.substringAfterLast('.', "")
            .trim()
            .lowercase()
            .takeIf { it.isNotBlank() }

        if (filenameExtension != null) {
            return ".${filenameExtension}"
        }

        return when (image.contentType?.lowercase()) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".bin"
        }
    }

    private fun copyReplacing(inputStream: InputStream, targetFile: Path) {
        Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING)
    }
}
