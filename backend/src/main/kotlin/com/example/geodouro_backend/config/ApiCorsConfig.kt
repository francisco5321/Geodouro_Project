package com.example.geodouro_backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class ApiCorsConfig(
    @Value("\${app.cors.allowed-origin-patterns:*}")
    allowedOriginPatterns: String
) : WebMvcConfigurer {

    private val originPatterns = allowedOriginPatterns
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf("*") }

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(*originPatterns.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600)
    }
}
