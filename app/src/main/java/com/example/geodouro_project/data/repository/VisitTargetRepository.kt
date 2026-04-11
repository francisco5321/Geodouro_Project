package com.example.geodouro_project.data.repository

import com.example.geodouro_project.data.remote.RemoteToggleVisitTarget
import com.example.geodouro_project.data.remote.RemoteVisitTarget
import com.example.geodouro_project.data.remote.RemoteVisitTargetService
import com.example.geodouro_project.domain.model.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VisitTargetRepository(
    private val remoteVisitTargetService: RemoteVisitTargetService
) {
    suspend fun fetchVisitTargets(session: SessionState.Authenticated): List<VisitTarget> = withContext(Dispatchers.IO) {
        remoteVisitTargetService.fetchVisitTargets(
            userId = session.userId,
            authToken = session.authToken
        ).map { it.toDomain() }
    }

    suspend fun deleteVisitTarget(
        savedVisitTargetId: Int,
        session: SessionState.Authenticated
    ): Boolean = withContext(Dispatchers.IO) {
        remoteVisitTargetService.deleteVisitTarget(
            savedVisitTargetId = savedVisitTargetId,
            userId = session.userId,
            authToken = session.authToken
        )
    }

    suspend fun toggleVisitTarget(
        targetType: String,
        targetId: Int,
        session: SessionState.Authenticated
    ): ToggleVisitTarget? = withContext(Dispatchers.IO) {
        remoteVisitTargetService.toggleVisitTarget(
            targetType = targetType,
            targetId = targetId,
            userId = session.userId,
            authToken = session.authToken
        )?.toDomain()
    }

    data class VisitTarget(
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

    data class ToggleVisitTarget(
        val success: Boolean,
        val saved: Boolean,
        val message: String,
        val target: VisitTarget?
    )

    private fun RemoteVisitTarget.toDomain(): VisitTarget = VisitTarget(
        savedVisitTargetId = savedVisitTargetId,
        userId = userId,
        targetType = targetType,
        title = title,
        subtitle = subtitle,
        notes = notes,
        observationId = observationId,
        plantSpeciesId = plantSpeciesId,
        publicationId = publicationId,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt
    )

    private fun RemoteToggleVisitTarget.toDomain(): ToggleVisitTarget = ToggleVisitTarget(
        success = success,
        saved = saved,
        message = message,
        target = target?.toDomain()
    )
}
