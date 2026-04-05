package com.fitnesslink.fit.model

import com.fitnesslink.fit.model.api.AuditFields
import com.fitnesslink.fit.model.api.EntityID

data class Workout(
    val id: String = "",
    val imageUrl: String = "",
    val name: String = "",
    val time: String = "",
    val location: String = "",
    val trainingLevel: String = "",
    val description: String = "",
    val phases: List<WorkoutPhase> = emptyList(),

    // API-aligned fields
    val statusId: EntityID? = null,
    val contributorId: EntityID? = null,
    val imageId: EntityID? = null,
    val thumbnailId: EntityID? = null,
    val estimatedTime: Int? = null,
    val showInLibrary: Boolean = true,
    val preBuiltTemplateId: EntityID? = null,
    val audit: AuditFields? = null
)

typealias WorkoutProgram = Program
