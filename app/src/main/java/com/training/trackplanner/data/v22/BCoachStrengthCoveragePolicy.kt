package com.training.trackplanner.data.v22

import com.training.trackplanner.data.CanonicalStrengthExposureCapability
import com.training.trackplanner.data.CanonicalStrengthExposureCapability.MovementAnchor
import com.training.trackplanner.data.Exercise
import kotlin.math.min
import kotlin.math.max

/** Planning preference only; never an e1RM, fatigue, or physiological conversion. */
internal object BCoachStrengthCoveragePolicy {
    const val VERSION = "STRENGTH_ANCHOR_COVERAGE_EXPERIMENT_V1"
    const val BACK_SQUAT = "barbell_back_squat"
    const val FRONT_SQUAT = "ex_c5043892"
    const val FRONT_SQUAT_PREFERENCE = 0.90
    private const val PREFIX = "STRENGTH:ANCHOR:"

    fun taskId(anchor: MovementAnchor) = PREFIX + anchor.name
    fun anchorForTask(id: String): MovementAnchor? =
        MovementAnchor.entries.firstOrNull { taskId(it) == id }

    fun anchors(keys: Collection<String>): Set<MovementAnchor> =
        keys.mapNotNull(CanonicalStrengthExposureCapability::movementAnchor).toSet()

    fun permits(exercise: Exercise, availableEquipment: Set<String>?): Boolean {
        fun tokens(s: String) = s.split(Regex("[,|;/+&]")).map { it.trim().uppercase() }
            .filter(String::isNotEmpty).toSet()
        val required = tokens(exercise.equipment)
        if (required.isEmpty() || required.all { it in setOf("BODYWEIGHT", "맨몸") }) return true
        val available = availableEquipment?.flatMap(::tokens)?.toSet() ?: return false
        return required.all { it in available }
    }

    fun selectable(exercise: Exercise, equipment: Set<String>?): Boolean =
        CanonicalStrengthExposureCapability.strengthPossible(exercise.stableKey) &&
            exercise.isActive && !exercise.isCustom && !exercise.needsReview &&
            exercise.activityKind != "SPORT_SESSION" &&
            exercise.planningEligibility !in setOf("HISTORY_ONLY", "FATIGUE_ONLY", "ANALYSIS_ONLY") &&
            exercise.metadataConfidence.trim().uppercase() !in
                setOf("", "UNKNOWN", "UNREVIEWED", "REVIEW_REQUIRED") &&
            !BCoachV2Policy.isTestOnlyExercise(exercise) && permits(exercise, equipment)

    /** Freeze the opportunity set independently of the candidate. Existing baseline
     * prescriptions remain evaluable; new exercises must pass metadata/equipment checks. */
    fun targetKeys(exercises: Map<String, Exercise>, equipment: Set<String>?,
        baselineKeys: Collection<String>): Set<String> =
        baselineKeys.filter(CanonicalStrengthExposureCapability::strengthPossible).toSet() +
            exercises.values.filter { selectable(it, equipment) }.map(Exercise::stableKey)

    fun preferredKeys(keys: Collection<String>): List<String> =
        keys.sortedWith(compareBy<String> { if (it == BACK_SQUAT) 0 else if (it == FRONT_SQUAT) 2 else 1 }
            .thenBy { it })

    /** Only deficit credit is capped. Excess and physical workload use raw dose.
     * Front-only work can reach 90% of the target if a back squat is available.
     * More front sets cannot buy off that preference. If back squat is unavailable,
     * no variant preference is charged. */
    fun deficitCredit(raw: Double, front: Double, lower: Double, preferBack: Boolean): Double {
        if (!preferBack || lower <= 0.0 || front <= 0.0) return raw
        val back = max(0.0, raw - front)
        if (back >= lower) return raw
        return min(raw, lower) -
            (1.0 - FRONT_SQUAT_PREFERENCE) * min(front, max(0.0, lower - back))
    }

    fun meanCoverage(keys: Set<String>, doseByKey: Map<String, Double>, target: Double): Double {
        val groups = anchors(keys)
        if (groups.isEmpty() || target <= 0.0) return 0.0
        return groups.map { anchor ->
            val raw = doseByKey.entries.filter {
                CanonicalStrengthExposureCapability.movementAnchor(it.key) == anchor
            }.sumOf { it.value }
            val credit = deficitCredit(raw,
                if (anchor == MovementAnchor.SQUAT) doseByKey[FRONT_SQUAT] ?: 0.0 else 0.0,
                target, anchor == MovementAnchor.SQUAT && BACK_SQUAT in keys)
            (credit / target).coerceIn(0.0, 1.0)
        }.average()
    }
}
