package com.fitnesslink.fit.persistence

import com.fitnesslink.fit.model.FitnessContent
import com.fitnesslink.fit.model.Program
import com.fitnesslink.fit.model.ProgramList
import java.util.Locale

// Program-domain DB helpers that `DatabaseManager` lacks (CONTRACTS: domain
// DAOs are extension functions over `rawDatabase()`). No new tables or
// columns — `programs`, `program_schedules`, `user_programs` and
// `calendar_content` already carry everything the Programs surface needs.

private fun lc(s: String) = s.lowercase(Locale.US)

/** List-row projection of a full program payload (`/programs/list-view` rows are `Program`-shaped). */
fun Program.toListRow(sortIndex: Int? = this.sortIndex): ProgramList = ProgramList(
    id = id,
    name = name,
    time = time,
    isFavorite = isFavorite,
    weeks = weeks,
    trainingLevel = trainingLevel.ifEmpty { trainingLevels.firstOrNull() ?: "" },
    imageId = imageId?.toString(),
    thumbnailId = thumbnailId?.toString(),
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    contributorId = contributorId?.toString(),
    sharedByUserId = sharedByUserId?.toString(),
    createdAt = audit?.timestamp,
    sortIndex = sortIndex
)

/**
 * Drop cached contributor (template) programs the server no longer lists
 * (iOS `reconcileContributorPrograms`, FTNSSLNK-333). User-authored rows
 * (`contributorId` null) are never touched — they may exist only locally
 * while a create is queued.
 */
fun DatabaseManager.reconcileContributorPrograms(serverIds: Collection<String>) {
    val keep = serverIds.map { lc(it) }.toSet()
    val stale = rawDatabase().rawQuery(
        "SELECT id FROM programs WHERE contributorId IS NOT NULL AND contributorId!=''", null
    ).use { c -> buildList { while (c.moveToNext()) { val id = c.getString(0) ?: continue; if (lc(id) !in keep) add(id) } } }
    stale.forEach { deleteProgram(it) }
}

/** Calendar entries grouped by lowercased enrollment id (entries without one are skipped). */
fun DatabaseManager.calendarContentByEnrollment(): Map<String, List<FitnessContent>> =
    calendarContent()
        .filter { !it.programEnrollmentId.isNullOrEmpty() }
        .groupBy { lc(it.programEnrollmentId!!) }

/** Not-yet-completed calendar entries for one workout inside one enrollment, earliest first. */
fun DatabaseManager.openEnrollmentEntries(enrollmentId: String, workoutId: String): List<FitnessContent> =
    calendarContentForEnrollment(enrollmentId)
        .filter { it.workoutId.equals(workoutId, ignoreCase = true) && !it.isCompleted }
        .sortedBy { it.scheduledDate ?: Long.MAX_VALUE }

/** Lowercased ids of every cached program (templates + own). */
fun DatabaseManager.cachedProgramIds(): Set<String> =
    rawDatabase().rawQuery("SELECT id FROM programs", null).use { c ->
        buildSet { while (c.moveToNext()) c.getString(0)?.let { add(lc(it)) } }
    }
