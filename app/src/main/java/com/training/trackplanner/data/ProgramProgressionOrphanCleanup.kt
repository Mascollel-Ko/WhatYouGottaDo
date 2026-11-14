package com.training.trackplanner.data

import android.util.Log

/**
 * Idempotent pruning of execution bookkeeping that no longer points to a live
 * applied workout or a live program item. An old program template does NOT need
 * to exist for a linked workout to remain valid.
 *
 * Caller must own a Room transaction. Do not use this to delete actual workouts,
 * workout sets, strength posterior evidence or valid applied-program links.
 */
internal object ProgramProgressionOrphanCleanup {
    data class Result(
        val links: Int,
        val suggestions: Int,
        val prescriptions: Int,
        val templateBindings: Int,
        val applications: Int,
        val tracks: Int
    ) {
        val removed: Int get() =
            links + suggestions + prescriptions + templateBindings + applications + tracks
    }

    suspend fun pruneInTransaction(db: TrainingDatabase): Result {
        check(db.inTransaction()) { "Orphan cleanup must be transactional" }
        val dao = db.programProgressionDao()
        // Ordered for foreign keys and to ensure a single pass is idempotent.
        val links = dao.pruneInvalidWorkoutLinks()
        val suggestions = dao.pruneInvalidSuggestions()
        val prescriptions = dao.pruneInvalidPrescriptions()
        val templateBindings = dao.pruneInvalidTemplateBindings()
        val applications = dao.pruneUnusedApplications()
        val tracks = dao.pruneUnusedTracks()
        return Result(links, suggestions, prescriptions, templateBindings, applications, tracks)
            .also { if (it.removed > 0) Log.i("ProgressionCleanup", "Removed orphan bookkeeping: $it") }
    }
}
