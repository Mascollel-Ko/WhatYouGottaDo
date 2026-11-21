package com.training.trackplanner.data.v22

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramSkeletonItem
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import org.json.JSONObject

/**
 * An explicit user-review bridge for the FIRST planned back-squat set only.
 *
 * Gate F is a short-horizon comparison, not a four-week forecast or validated
 * adaptation model. We never broadcast the selected load/rep to later sets or
 * weeks. A user must independently approve the reviewed B-Coach candidate
 * and opt into this exact set change. Afterwards, the normal linked-record
 * progression service is responsible for later, still-unstarted occurrences.
 */
internal object BCoachSquatShadowProgramBridge {
    private const val SQUAT = "barbell_back_squat"
    private const val EPS = 1e-6
    private const val MAX_RELATIVE_LOAD_STEP = 0.02
    private const val MAX_EVIDENCE_AGE_DAYS = 56L
    const val EVIDENCE_VERSION = "B_COACH_SQUAT_GATE_F_EXPLICIT_USER_APPROVAL_V1"

    data class Review(
        val localId: String,
        val week: Int,
        val day: Int,
        val action: String,
        val previousLoadKg: Double?,
        val previousReps: Int,
        val approvedLoadKg: Double,
        val approvedReps: Int,
        val originalSetCount: Int,
        val evidenceDate: String
    ) {
        val description: String get() =
            "${week}주 ${day}요일 · 백스쿼트 1세트 ${approvedLoadKg}kg × ${approvedReps}회" +
                " (이후 세트·주차는 변경하지 않음)"
    }

    fun inspect(
        candidate: GeneratedProgramSkeleton,
        comparison: BCoachSquatShadowProgramComparison?
    ): Review? {
        val report = comparison ?: return null
        if (report.status !in setOf("STABLE_EXPERIMENT_ONLY", "SENSITIVE_SHADOW")) return null
        if (report.observedReference.isNullOrBlank()) return null
        val ranking = report.curveRanking ?: return null
        if (ranking.status != "EXPERIMENTAL_RANKING_ONLY" ||
            ranking.asOf != report.snapshotDate || ranking.referenceDate == null ||
            ranking.referenceDate.isAfter(ranking.asOf) ||
            ChronoUnit.DAYS.between(ranking.referenceDate, ranking.asOf) > MAX_EVIDENCE_AGE_DAYS
        ) return null
        val chosen = ranking.ranked.singleOrNull {
            it.eligible && it.action == ranking.selectedAction
        } ?: return null
        val hold = ranking.ranked.singleOrNull { it.action == "HOLD" } ?: return null
        if (chosen.action !in setOf("HOLD", "LOAD_UP_ONE_STEP", "REPS_UP_ONE") ||
            !chosen.loadKg.isFinite() || chosen.loadKg <= 0.0 ||
            !hold.loadKg.isFinite() || hold.loadKg <= 0.0 ||
            chosen.reps !in 1..20 || hold.reps !in 1..20 ||
            chosen.sets < 1 || chosen.sets != hold.sets
        ) return null
        if (chosen.loadKg + EPS < hold.loadKg ||
            (chosen.loadKg - hold.loadKg) / hold.loadKg > MAX_RELATIVE_LOAD_STEP + EPS
        ) return null
        if (chosen.action == "HOLD" &&
            (abs(chosen.loadKg - hold.loadKg) > EPS || chosen.reps != hold.reps)
        ) return null
        if (chosen.action == "LOAD_UP_ONE_STEP" &&
            (chosen.loadKg <= hold.loadKg + EPS || chosen.reps != hold.reps)
        ) return null
        if (chosen.action == "REPS_UP_ONE" &&
            (abs(chosen.loadKg - hold.loadKg) > EPS || chosen.reps != hold.reps + 1)
        ) return null

        val item = candidate.items.filter { it.exerciseStableKey == SQUAT }
            .minWithOrNull(compareBy<ProgramSkeletonItem> { it.weekNumber }
                .thenBy { it.dayOfWeek }.thenBy { it.orderIndex }) ?: return null
        val sets = ProgramSetPrescriptionResolver.resolve(item)
        val first = sets.firstOrNull() ?: return null
        if (sets.size != chosen.sets || first.reps != hold.reps ||
            first.seconds != 0 || first.reps !in 1..20 ||
            "PEAK" in item.selectionRole.uppercase() ||
            BCoachV2DoseEvaluator.isExplicitUserExerciseLock(item.selectionReason) ||
            BCoachV2LoadCandidateResolver.isSetUserFixed(first, item.weightSource) ||
            item.directSportSession
        ) return null
        // The model can suggest a first set; the user still needs to review
        // any other sets individually. No automatic set-count or weekly change.
        val previous = first.weightKg.takeIf {
            first.loadState == ProgramLoadState.EXPLICIT_LOAD && it > 0.0
        } ?: first.estimatedLoadKg?.takeIf { it.isFinite() && it > 0.0 }
        return Review(
            localId = item.localId, week = item.weekNumber, day = item.dayOfWeek,
            action = chosen.action, previousLoadKg = previous,
            previousReps = first.reps, approvedLoadKg = chosen.loadKg,
            approvedReps = chosen.reps, originalSetCount = sets.size,
            evidenceDate = ranking.referenceDate.toString()
        )
    }

    /** Returns null if the previously inspected plan or shadow has changed. */
    fun apply(
        candidate: GeneratedProgramSkeleton,
        comparison: BCoachSquatShadowProgramComparison?,
        previouslyReviewed: Review
    ): GeneratedProgramSkeleton? {
        val current = inspect(candidate, comparison) ?: return null
        if (current != previouslyReviewed) return null
        val matching = candidate.items.filter { it.localId == current.localId }
        if (matching.size != 1) return null
        val item = matching.single()
        val original = ProgramSetPrescriptionResolver.resolve(item)
        if (original.size != current.originalSetCount) return null
        val amended: List<ProgramSetPrescription> = original.mapIndexed { index, set ->
            if (index != 0) set else set.copy(
                reps = current.approvedReps,
                weightKg = current.approvedLoadKg,
                seconds = 0,
                loadState = ProgramLoadState.EXPLICIT_LOAD,
                estimatedLoadKg = current.approvedLoadKg,
                loadSource = ProgramLoadSource.USER_FIXED,
                freshRir = null,
                expectedRir = null,
                relativeIntensity = null,
                evidenceVersion = EVIDENCE_VERSION
            )
        }
        val updatedItem = item.copy(
            reps = amended.map { it.reps }.distinct().singleOrNull() ?: 0,
            weightKg = amended.map { it.weightKg }.distinct().singleOrNull() ?: 0.0,
            setPrescriptions = amended,
            weightSource = EVIDENCE_VERSION
        )
        val root = runCatching { JSONObject(candidate.planningMetadataJson ?: "{}") }
            .getOrElse { return null }
        root.put("bCoachSquatShadowApprovedFirstSet", JSONObject()
            .put("authority", EVIDENCE_VERSION)
            .put("userSelected", true)
            .put("week", current.week)
            .put("dayOfWeek", current.day)
            .put("exerciseStableKey", SQUAT)
            .put("localId", current.localId)
            .put("setIndex", 1)
            .put("action", current.action)
            .put("referenceDate", current.evidenceDate)
            .put("newKg", current.approvedLoadKg)
            .put("newReps", current.approvedReps)
            .put("laterSetsOrWeeksModified", false))
        return candidate.copy(
            items = candidate.items.map { if (it.localId == current.localId) updatedItem else it },
            planningMetadataJson = root.toString()
        )
    }
}
