package com.example.geodouro_backend.visittarget

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class VisitTargetResponse(
    val savedVisitTargetId: Int,
    val userId: Int,
    val targetType: String,
    val title: String,
    val subtitle: String?,
    val notes: String?,
    val observationId: Int?,
    val plantSpeciesId: Int?,
    val publicationId: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: String?
)

data class ToggleVisitTargetRequest(
    @field:NotBlank
    val targetType: String,
    @field:Min(1)
    val targetId: Int
)

data class ToggleVisitTargetResponse(
    val success: Boolean,
    val saved: Boolean,
    val message: String,
    val target: VisitTargetResponse?
)
