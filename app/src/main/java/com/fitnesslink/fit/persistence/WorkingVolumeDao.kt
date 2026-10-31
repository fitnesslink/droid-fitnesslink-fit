package com.fitnesslink.fit.persistence

import com.fitnesslink.fit.model.MovementAggregate
import com.fitnesslink.fit.model.MovementHistoryEntry
import com.fitnesslink.fit.model.MovementSetDetail
import com.fitnesslink.fit.model.VolumeEntry
import com.fitnesslink.fit.session.WorkingSetVolume
import java.util.Date
import java.util.Locale

/**
 * FTNSSLNK-497 working-set volume reads (iOS 23ef55d `DatabaseManager` changes). Total Volume
 * counts only working sets: warm-up sets and Warm-Up / Cool Down phase tasks are excluded via the
 * cached workout's tasks, since the history table has no phase column. Read-only; no schema work.
 * Lives beside `DatabaseManager` rather than in it (CONTRACTS: DatabaseManager.kt is integrator-owned).
 */

/**
 * Per-workout [WorkingSetVolume.Exclusions], loaded from the cached workout once per workout id.
 * An uncached workout excludes nothing.
 */
class VolumeExclusionCache(private val load: (String) -> List<com.fitnesslink.fit.model.WorkoutTask>?) {
    private val byWorkout = mutableMapOf<String, WorkingSetVolume.Exclusions>()

    fun excludes(workoutId: String, taskId: String, setNumber: Int): Boolean {
        if (workoutId.isEmpty() || taskId.isEmpty()) return false
        val key = workoutId.lowercase(Locale.US)
        val exclusions = byWorkout.getOrPut(key) {
            load(workoutId)?.let { WorkingSetVolume.Exclusions(it) } ?: WorkingSetVolume.Exclusions.none
        }
        return exclusions.excludes(taskId, setNumber)
    }

    companion object {
        fun forDatabase(db: DatabaseManager): VolumeExclusionCache =
            VolumeExclusionCache { id -> runCatching { db.workout(id)?.allTasks }.getOrNull() }
    }
}

/**
 * Session Total Volume: Σ(weight × reps) over the session's recorded working sets.
 */
fun DatabaseManager.sessionWorkingVolume(sessionId: String): Double {
    val exclusions = VolumeExclusionCache.forDatabase(this)
    return rawDatabase().rawQuery(
        """
        SELECT COALESCE(workoutTaskId,''), COALESCE(workoutId,''), COALESCE(setNumber,1),
               COALESCE(reps,0), COALESCE(weightLifted,0)
        FROM workout_session_history WHERE LOWER(workoutSessionId)=?
        """.trimIndent(),
        arrayOf(sessionId.lowercase(Locale.US))
    ).use { c ->
        var total = 0.0
        while (c.moveToNext()) {
            if (exclusions.excludes(c.getString(1) ?: "", c.getString(0) ?: "", c.getInt(2))) continue
            total += WorkingSetVolume.setVolume(c.getDouble(4), c.getInt(3))
        }
        total
    }
}

/**
 * Working-set version of `DatabaseManager.volumeEntries`. Also fixes the column read the iOS commit
 * fixed: column 4 is reps (3 is setNumber — the old read made every entry's volume set# × weight).
 */
fun DatabaseManager.workingVolumeEntries(userId: String, since: Long): List<VolumeEntry> {
    val exclusions = VolumeExclusionCache.forDatabase(this)
    return rawDatabase().rawQuery(
        """
        SELECT id, taskName, logDate, COALESCE(setNumber,1), COALESCE(reps,0), COALESCE(weightLifted,0),
               COALESCE(workoutTaskId,''), COALESCE(workoutId,'')
        FROM workout_session_history WHERE userId=? AND logDate>=? AND weightLifted>0 ORDER BY logDate DESC
        """.trimIndent(),
        arrayOf(userId, since.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                if (exclusions.excludes(c.getString(7) ?: "", c.getString(6) ?: "", c.getInt(3))) continue
                add(VolumeEntry(c.getString(0) ?: "", c.getString(1) ?: "", Date(c.getLong(2)), 1, c.getInt(4), c.getDouble(5)))
            }
        }
    }
}

/**
 * Working-set version of `DatabaseManager.movementAggregates`: every set is still listed, but
 * warm-up sets and Warm-Up / Cool Down tasks add no volume.
 */
fun DatabaseManager.workingMovementAggregates(userId: String, since: Long): List<MovementAggregate> {
    data class R(val tn: String, val reps: Int, val sn: Int, val w: Double, val d: Date, val wn: String, val sid: String, val working: Boolean)
    val exclusions = VolumeExclusionCache.forDatabase(this)
    val raw = rawDatabase().rawQuery(
        """
        SELECT h.taskName, COALESCE(h.reps,0), COALESCE(h.setNumber,1), COALESCE(h.weightLifted,0),
               h.logDate, s.workoutName, h.workoutSessionId, COALESCE(h.workoutTaskId,''), COALESCE(h.workoutId,'')
        FROM workout_session_history h JOIN workout_sessions s ON s.id=h.workoutSessionId
        WHERE h.userId=? AND h.logDate>=? AND h.taskName!='' ORDER BY h.taskName, h.logDate DESC, h.setNumber
        """.trimIndent(),
        arrayOf(userId, since.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val sn = c.getInt(2)
                val working = !exclusions.excludes(c.getString(8) ?: "", c.getString(7) ?: "", sn)
                add(R(c.getString(0) ?: "", c.getInt(1), sn, c.getDouble(3), Date(c.getLong(4)), c.getString(5) ?: "", c.getString(6) ?: "", working))
            }
        }
    }
    return raw.groupBy { it.tn }.map { (name, entries) ->
        val bySession = entries.groupBy { it.sid }
        val hist = bySession.map { (sid, sets) ->
            val s = sets.sortedBy { it.sn }
            MovementHistoryEntry(sid, s.first().d, s.first().wn, s.size, s.sumOf { it.reps },
                s.maxOf { it.w }, s.map { MovementSetDetail(it.sn, it.reps, it.w) })
        }.sortedByDescending { it.sessionDate }
        val mw = entries.maxOf { it.w }; val pr = entries.first { it.w == mw }
        MovementAggregate(name, name, bySession.size, entries.size, entries.sumOf { it.reps },
            mw, entries.sumOf { if (it.working) WorkingSetVolume.setVolume(it.w, it.reps) else 0.0 }, mw, pr.reps, pr.d, hist)
    }.sortedByDescending { it.totalVolume }
}
