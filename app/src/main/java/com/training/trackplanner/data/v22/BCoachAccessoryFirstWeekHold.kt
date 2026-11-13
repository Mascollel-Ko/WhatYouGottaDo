package com.training.trackplanner.data.v22

import androidx.room.withTransaction
import com.training.trackplanner.data.BCoachUncalibratedLinkPolicy
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.ProgressionDirection
import com.training.trackplanner.data.ProgressionMode
import com.training.trackplanner.data.ProgressionResolution
import com.training.trackplanner.data.ProgressionSuggestion
import com.training.trackplanner.data.TrainingDatabase
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate
import kotlin.math.abs

/**
 * Accessory tracks remain linked for history/visibility, but are not subject to
 * any automatic RPE threshold or e1RM progression. Once the *first* actual B
 * occurrence establishes a uniform external kg, carry that kg to all unstarted,
 * unchanged later occurrences in the SAME application and track only.
 */
internal object BCoachAccessoryFirstWeekHold {
    private const val EPS = 1e-8

    fun initialLoad(source: WorkoutEntryWithSets): Double? {
        val sets = source.sets
        if (sets.isEmpty() || sets.any {
            !it.confirmed || it.loadState != ProgramLoadState.EXPLICIT_LOAD ||
                !it.weightKg.isFinite() || it.weightKg <= 0.0 ||
                it.secondaryWeightKg != null
            }) return null
        val load = sets.first().weightKg
        return load.takeIf { kg -> sets.all { abs(it.weightKg - kg) <= EPS } }
    }

    fun futureTargets(
        sourceLink: ProgramWorkoutLink,
        allLinks: List<ProgramWorkoutLink>,
        entries: List<WorkoutEntryWithSets>,
        prescriptions: List<ProgramPrescriptionSet>,
        asOf: LocalDate
    ): List<Pair<WorkoutEntryWithSets, List<ProgramPrescriptionSet>>> {
        if (sourceLink.mode != ProgressionMode.APP || sourceLink.trackId == "untracked") return emptyList()
        val chain = allLinks.filter { it.applicationId == sourceLink.applicationId &&
            it.sourceProgramStableKey == sourceLink.sourceProgramStableKey &&
            it.trackId == sourceLink.trackId
        }
        if (chain.minOfOrNull { it.sequence } != sourceLink.sequence) return emptyList()
        val byEntry = entries.associateBy { it.entry.id }
        return chain.filter { it.sequence > sourceLink.sequence && it.mode == ProgressionMode.APP }
            .sortedBy { it.sequence }.mapNotNull { link ->
                val entry = byEntry[link.entryId] ?: return@mapNotNull null
                if (entry.entry.exerciseStableKey != byEntry[sourceLink.entryId]?.entry?.exerciseStableKey ||
                    entry.entry.completedAt != null || entry.entry.firstConfirmedAt != null ||
                    entry.sets.isEmpty() || entry.sets.any { it.confirmed }
                ) return@mapNotNull null
                val date = runCatching { LocalDate.parse(entry.entry.date) }.getOrNull()
                    ?: return@mapNotNull null
                if (date < asOf) return@mapNotNull null
                val targets = prescriptions.filter { it.entryId == link.entryId }
                if (targets.size != entry.sets.size ||
                    targets.any { !it.originalExists || it.plannedSetIndex == null ||
                        it.originalReps != it.plannedReps ||
                        abs(it.originalKg - it.plannedKg) > EPS ||
                        it.originalSeconds != it.plannedSeconds ||
                        it.originalLoadState != it.plannedLoadState ||
                        // Do not flatten an explicitly periodized or user-entered target.
                        (it.originalKg > EPS &&
                            it.originalLoadState != ProgramLoadState.USER_CALIBRATION_REQUIRED)
                    }
                ) return@mapNotNull null
                if (targets.any { target ->
                    entry.sets.singleOrNull { it.setIndex == target.plannedSetIndex }?.let { set ->
                        set.reps != target.plannedReps ||
                            abs(set.weightKg - target.plannedKg) > EPS ||
                            set.seconds != target.plannedSeconds ||
                            set.loadState != target.plannedLoadState ||
                            set.secondaryWeightKg != null
                    } != false
                }) return@mapNotNull null
                entry to targets
            }
    }
}

internal class BCoachAccessoryFirstWeekHoldService(private val db: TrainingDatabase) {
    private val workouts = db.workoutDao()
    private val dao = db.programProgressionDao()

    suspend fun propagate(sourceEntryId: Long, asOf: LocalDate): BCoachPostConfirmOutcome =
        db.withTransaction {
            fun result(status: String, targetEntryId: Long? = null, kg: Double? = null,
                       changed: Int = 0) = BCoachPostConfirmOutcome(
                sourceEntryId = sourceEntryId, status = status, targetEntryId = targetEntryId,
                nextLoadKg = kg, selectedAction = if (changed > 0) "FIRST_WEEK_HOLD" else null,
                detail = if (changed > 0) "$changed future linked occurrences" else null
            )
            val link = dao.link(sourceEntryId) ?: return@withTransaction result("SOURCE_NOT_PROGRAM_LINKED")
            if (link.mode != ProgressionMode.APP)
                return@withTransaction result("NOT_ACCESSORY")
            val program = db.programDao().findProgramByStableKey(link.sourceProgramStableKey)
            if (!BCoachUncalibratedLinkPolicy.isBProgram(program?.planningMetadataJson))
                return@withTransaction result("NOT_B_COACH")
            val sourceExercise = workouts.findEntryById(sourceEntryId)?.exerciseStableKey
                ?: return@withTransaction result("SOURCE_MISSING")
            if (BCoachMainPolicy.canChangeLoad(link.role, sourceExercise,
                    program?.planningMetadataJson)) return@withTransaction result("NOT_ACCESSORY")
            val entries = workouts.allEntriesWithSets()
            val source = entries.singleOrNull { it.entry.id == sourceEntryId }
                ?: return@withTransaction result("SOURCE_MISSING")
            val sourceDate = runCatching { LocalDate.parse(source.entry.date) }.getOrNull()
                ?: return@withTransaction result("SOURCE_DATE_INVALID")
            if (sourceDate > asOf) return@withTransaction result("SOURCE_IN_FUTURE")
            val kg = BCoachAccessoryFirstWeekHold.initialLoad(source)
                ?: return@withTransaction result("ACCESSORY_FIRST_LOAD_NOT_CALIBRATED")
            val links = dao.links()
            val chain = links.filter { it.applicationId == link.applicationId &&
                it.trackId == link.trackId && it.sourceProgramStableKey == link.sourceProgramStableKey }
            if (chain.minOfOrNull { it.sequence } != link.sequence)
                return@withTransaction result("ACCESSORY_NOT_FIRST_OCCURRENCE")
            val targetRows = BCoachAccessoryFirstWeekHold.futureTargets(
                link, links, entries, dao.prescriptions(), asOf
            )
            val existing = dao.suggestions()
            var changed = 0
            var firstId: Long? = null
            for ((future, targets) in targetRows) {
                val id = future.entry.id
                if (existing.any { it.targetEntryId == id &&
                    it.resolution in setOf(ProgressionResolution.ACCEPTED,
                        ProgressionResolution.KEPT_CURRENT_PLAN, ProgressionResolution.MANUAL_OVERRIDE)
                }) continue
                // First-week calibration is not a new prediction. Preserve each future
                // workout's reps, sets and time; change only the planned displayed load.
                val next = future.sets.map { it.copy(weightKg = kg,
                    loadState = ProgramLoadState.EXPLICIT_LOAD, manualWeight = true) }
                next.forEach { workouts.updateSet(it) }
                targets.forEach { dao.putPrescription(it.copy(
                    plannedKg = kg, plannedLoadState = ProgramLoadState.EXPLICIT_LOAD
                )) }
                dao.putSuggestion(ProgressionSuggestion(
                    applicationId = link.applicationId, trackId = link.trackId,
                    sourceEntryId = sourceEntryId, targetEntryId = id,
                    previousActualKg = kg, currentPlanKg = future.sets.first().weightKg,
                    suggestedKg = kg, judgmentRpe = null,
                    direction = ProgressionDirection.HOLD,
                    reasons = "B_COACH_ACCESSORY_FIRST_WEEK_HOLD",
                    evidenceHash = "FIRST_WEEK_HOLD_${link.applicationId}_${link.trackId}_$sourceEntryId",
                    resolution = ProgressionResolution.ACCEPTED,
                    resolvedAt = System.currentTimeMillis(), resolvedKg = kg,
                    rule = link.rule
                ))
                firstId = firstId ?: id
                changed++
            }
            if (changed == 0) return@withTransaction result("ACCESSORY_NO_UNMODIFIED_FUTURE")
            result("NEXT_LINKED_UPDATED", firstId, kg, changed)
        }
}
