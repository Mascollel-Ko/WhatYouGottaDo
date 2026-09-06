package com.training.trackplanner.data

import kotlin.math.abs
import kotlin.math.round

object ProgressionTrackInference {
    fun signature(
        key: String, sets: List<ProgramSetPrescription>, oneRmKg: Double? = null,
        style: String = "", variant: String = "", slot: String = "", intensity: String = "",
        anchor: Int? = null
    ): ProgressionSignature {
        val weighted = sets.isNotEmpty() && sets.all { it.weightKg.isFinite() && it.weightKg > 0 && it.reps > 0 && it.seconds == 0 }
        val uniform = weighted && sets.map { it.weightKg }.distinct().size == 1
        val policy = when { !weighted -> ProgressionBase.REVIEW; anchor != null -> ProgressionBase.ANCHOR; uniform -> ProgressionBase.UNIFORM; else -> ProgressionBase.REVIEW }
        val base = when (policy) {
            ProgressionBase.UNIFORM -> sets.first().weightKg
            ProgressionBase.ANCHOR -> sets.singleOrNull { it.setIndex == anchor }?.weightKg
            ProgressionBase.REVIEW -> null
        }
        val oneRm = oneRmKg?.takeIf { it.isFinite() && it > 0 }
        return ProgressionSignature(key, sets.size, sets.joinToString("|") { it.reps.toString() }, base, oneRm,
            if (base != null && oneRm != null) base / oneRm else null, style, variant, slot, intensity, policy, anchor)
    }

    fun sameTrack(a: ProgressionSignature, b: ProgressionSignature): Boolean {
        if (a.exerciseStableKey != b.exerciseStableKey || a.basePolicy != b.basePolicy || a.basePolicy == ProgressionBase.REVIEW) return false
        if (a.style.isNotBlank() || b.style.isNotBlank()) {
            // Explicit planner intent, never the prescription text or exercise name.
            return a.style == b.style && a.variant == b.variant && a.trainingSlot == b.trainingSlot
        }
        if (a.setCount != b.setCount || a.repsPattern != b.repsPattern || a.dayIntensity != b.dayIntensity || a.trainingSlot != b.trainingSlot) return false
        if (a.relativeIntensity != null && b.relativeIntensity != null) return abs(a.relativeIntensity - b.relativeIntensity) <= 0.04
        val x = a.baseKg ?: return false
        val y = b.baseKg ?: return false
        return abs(x - y) / maxOf(x, y) <= 0.04
    }
}

data class ProgressionSession(
    val link: ProgramWorkoutLink,
    val actual: List<WorkoutSet>,
    val targets: List<ProgramPrescriptionSet>
) {
    // Partial sessions cannot become a synthetic success; a removed core set remains a miss.
    val completed: Boolean get() = actual.isNotEmpty() && actual.all { it.confirmed }
    val targetCompleted: Boolean get() = completed && targets.any { it.plannedSetIndex != null } && targets.filter { it.plannedSetIndex != null }.all { target ->
        actual.singleOrNull { it.setIndex == target.plannedSetIndex }?.let {
            it.reps >= target.plannedReps && it.weightKg >= target.plannedKg && it.seconds >= target.plannedSeconds
        } == true
    }
    fun base(): Double? = ProgressionEngine.base(actual.filter { it.confirmed }, link.basePolicy, link.anchorSetIndex)
    fun judgmentRpe(rule: ProgressionRule): Double? {
        val sets = if (link.basePolicy == ProgressionBase.ANCHOR || rule.rpePolicy == ProgressionRpePolicy.ANCHOR)
            actual.filter { it.setIndex == link.anchorSetIndex } else actual
        if (sets.isEmpty() || sets.any { !it.confirmed || it.rpe == null || !it.rpe.isFinite() || it.rpe !in 1.0..10.0 }) return null
        return sets.maxOf { it.rpe!! }
    }
}

data class ProgressionVerdict(val direction: ProgressionDirection, val kg: Double?, val rpe: Double?, val reason: String)

/** No OFI input: global fatigue is deliberately not a progression direction switch. */
object ProgressionEngine {
    fun base(sets: List<WorkoutSet>, policy: ProgressionBase, anchor: Int?): Double? {
        val value = when (policy) {
            ProgressionBase.UNIFORM -> sets.map { it.weightKg }.distinct().singleOrNull()
            ProgressionBase.ANCHOR -> sets.singleOrNull { it.setIndex == anchor }?.weightKg
            ProgressionBase.REVIEW -> null
        }
        return value?.takeIf { it.isFinite() && it > 0 }
    }

    fun predecessors(target: ProgramWorkoutLink, sessions: List<ProgressionSession>): List<ProgressionSession> = sessions
        .filter { it.link.applicationId == target.applicationId && it.link.trackId == target.trackId &&
            it.link.sequence < target.sequence && it.completed }
        .sortedByDescending { it.link.sequence }

    fun evaluate(target: ProgramWorkoutLink, history: List<ProgressionSession>, locallyRestricted: Boolean): ProgressionVerdict {
        val recent = predecessors(target, history)
        val previous = recent.firstOrNull() ?: return ProgressionVerdict(ProgressionDirection.REVIEW, null, null, "NO_COMPARABLE_CONFIRMED_SESSION")
        val rule = target.rule.also { it.validate() }
        val base = previous.base()
        val rpe = previous.judgmentRpe(rule)
        if (target.mode == ProgressionMode.DIRECT || target.needsReview || previous.link.needsReview || base == null || target.basePolicy == ProgressionBase.REVIEW ||
            (rule.rpePolicy == ProgressionRpePolicy.ANCHOR && previous.link.anchorSetIndex == null))
            return ProgressionVerdict(ProgressionDirection.REVIEW, null, rpe, "DIRECT_JUDGMENT_OR_STRUCTURE_REVIEW")
        if (target.mode == ProgressionMode.OFF) return ProgressionVerdict(ProgressionDirection.REVIEW, null, rpe, "OFF")
        fun hold(reason: String) = ProgressionVerdict(ProgressionDirection.HOLD, base, rpe, reason)
        if (!previous.targetCompleted) {
            val misses = recent.takeWhile { !it.targetCompleted }.size
            if (misses >= rule.failuresBeforeDecrease) {
                val kg = round(base * (1 - rule.decreasePercent / 100) * 2) / 2
                return if (kg > 0 && kg < base) ProgressionVerdict(ProgressionDirection.DECREASE, kg, rpe, "REPEATED_COMPARABLE_MISS") else hold("NO_EXECUTABLE_DECREASE")
            }
            if (rule.requireCompletion) return if (rule.firstFailure == FirstProgressionFailure.REVIEW)
                ProgressionVerdict(ProgressionDirection.REVIEW, null, rpe, "FIRST_MISS_REVIEW") else hold("FIRST_MISS_HOLD")
        }
        if (locallyRestricted) return hold("RELATED_LOCAL_TISSUE_RESTRICTION")
        if (rpe == null && rule.missingRpe == MissingProgressionRpe.HOLD) return hold("ACTUAL_RPE_MISSING")
        if (rpe != null && rpe > rule.rpeThreshold) return hold("ACTUAL_EFFORT_HIGH")
        val successes = recent.takeWhile {
            (!rule.requireCompletion || it.targetCompleted) &&
                (it.judgmentRpe(rule)?.let { effort -> effort <= rule.rpeThreshold } ?: (rule.missingRpe == MissingProgressionRpe.COMPLETION_ONLY))
        }.size
        if (successes < rule.successesRequired) return hold("MORE_COMPARABLE_SUCCESSES_REQUIRED")
        val step = loadStep(rule.incrementKg, recent.mapNotNull { it.base() }.reversed(), base)
            ?: return ProgressionVerdict(ProgressionDirection.REVIEW, null, rpe, "LOAD_STEP_REQUIRES_INPUT")
        return ProgressionVerdict(ProgressionDirection.INCREASE, base + step, rpe, "TARGET_COMPLETED_MANAGEABLE_EFFORT")
    }

    fun loadStep(customKg: Double?, bases: List<Double>, base: Double): Double? {
        customKg?.takeIf { it.isFinite() && it > 0 }?.let { return it }
        val positive = bases.zipWithNext { a, b -> b - a }.filter { it > 0 && it.isFinite() }
        if (positive.size >= 2) {
            val sorted = positive.sorted()
            val median = sorted[sorted.size / 2]
            if (positive.count { abs(it - median) < 0.01 } >= 2 && median <= base * 0.1) return median
        }
        // 0.5 kg is an editable arithmetic grid, not a claim about available equipment.
        val fallback = round(base * 0.02 * 2) / 2
        return fallback.takeIf { it >= 0.5 && it <= base * 0.05 }
    }
}
