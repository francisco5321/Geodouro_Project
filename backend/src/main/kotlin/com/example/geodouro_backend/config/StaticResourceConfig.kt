package com.example.geodouro_backend.config

import java.nio.file.Files
import java.nio.file.Paths
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class StaticResourceConfig(
    @Value("\${app.storage.images-dir:backend-uploads}") storageRoot: String
) : WebMvcConfigurer {

    private val rootPath = Paths.get(storageRoot).toAbsolutePath().normalize()

    init {
        Files.createDirectories(rootPath)
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(rootPath.toUri().toString())
    }
}
