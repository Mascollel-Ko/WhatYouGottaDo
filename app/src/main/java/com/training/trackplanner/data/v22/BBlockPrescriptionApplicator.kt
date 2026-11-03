package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import java.time.LocalDate

/**
 * Purpose and exercise role decide the adjustment lever; the 0/1/2 pattern steps
 * remain the agreed one-unit increments. Medium broadens *accessory participation*,
 * never multiplies a weight plate, set or repetition step.
 */
internal object BBlockPrescriptionApplicator {
    fun resolveAmplitude(
        block: BBlockDefinition,
        history: List<V22HistoryRow>,
        asOf: LocalDate,
        sessions: List<V22Session>
    ): BChangeAmplitude {
        if (block.amplitude != BChangeAmplitude.AUTO) return block.amplitude
        val keys = sessions.flatMap(V22Session::items).mapTo(linkedSetOf(), V22ExercisePlan::stableKey)
        val recent = history.filter { it.stableKey in keys && it.date in asOf.minusDays(27)..asOf }
        val dates = recent.mapTo(linkedSetOf(), V22HistoryRow::date)
        val recorded = recent.count { (it.reps ?: 0.0) > 0.0 || (it.seconds ?: 0.0) > 0.0 }
        // RPE is deliberately not a hard limit: high RPE is a user-review
        // notice, not an automatic reason to refuse or downscale a plan.
        return if (dates.size >= 2 && recent.size >= 6 &&
            recorded >= recent.size / 2) BChangeAmplitude.MEDIUM else BChangeAmplitude.SMALL
    }

    fun apply(
        session: V22Session,
        stage: Int,
        focus: BBalancedGoal,
        block: BBlockDefinition,
        mainKeys: Set<String>,
        exercises: Map<String, Exercise> = emptyMap(),
        resolvedAmplitude: BChangeAmplitude = BChangeAmplitude.MEDIUM
    ): V22Session {
        val hasMain = session.items.any { it.stableKey in mainKeys }
        var accessoryChanged = false
        val updated = session.items.map { item ->
            val main = item.stableKey in mainKeys
            val rawGoal = when {
                block.goal != BBlockGoal.AUTO -> block.goal
                focus == BBalancedGoal.STRENGTH -> BBlockGoal.STRENGTH
                focus == BBalancedGoal.HYPERTROPHY -> BBlockGoal.HYPERTROPHY
                else -> BBlockGoal.SPORT_SUPPORT
            }
            val goal = BBlockPurposePrescription.purpose(rawGoal, main, exercises[item.stableKey])
            val primary = when (block.adjustment) {
                BVolumeAdjustment.AUTO -> BBlockPeriodization.increasePriority(goal)
                BVolumeAdjustment.REPS_FIRST -> listOf(
                    BProgressionField.REPS, BProgressionField.LOAD, BProgressionField.SETS)
                BVolumeAdjustment.LOAD_FIRST -> listOf(
                    BProgressionField.LOAD, BProgressionField.SETS, BProgressionField.REPS)
                BVolumeAdjustment.SETS_FIRST -> listOf(
                    BProgressionField.SETS, BProgressionField.REPS, BProgressionField.LOAD)
                BVolumeAdjustment.CUSTOM -> block.customPriority
            }
            val ordered = primary.filter { it != BProgressionField.MOVEMENT_QUALITY }
            val defaultPriority = if (ordered.isEmpty())
                listOf(BProgressionField.REPS, BProgressionField.SETS, BProgressionField.LOAD)
                else ordered
            val upper = BBlockPurposePrescription.repRange(goal, main).last
            val lower = BBlockPurposePrescription.repRange(goal, main).first
            var sets = item.sets.map(V22ObservedSet::copy)
            var loadSteps = 0
            val participated = main || resolvedAmplitude == BChangeAmplitude.MEDIUM ||
                (!hasMain && !accessoryChanged)
            val isTimed = sets.isNotEmpty() && sets.all { (it.reps ?: 0) <= 0 && (it.seconds ?: 0) > 0 }
            val loadCompatible = !isTimed && supportsPairedPlateIncrement(item.stableKey, exercises[item.stableKey])
            val amount = if (participated) kotlin.math.abs(stage) else 0
            var applied = 0
            repeat(amount) { unit ->
                val choices = when {
                    stage < 0 -> BBlockPeriodization.decreasePriority()
                    block.adjustment == BVolumeAdjustment.CUSTOM ->
                        defaultPriority.drop(unit % defaultPriority.size) +
                            defaultPriority.take(unit % defaultPriority.size)
                    else -> defaultPriority
                }
                val field = choices.firstOrNull { option ->
                    when (option) {
                        BProgressionField.SETS -> main && (stage > 0 || sets.size > 1)
                        BProgressionField.REPS -> !isTimed && sets.isNotEmpty() && sets.all {
                            (it.reps ?: 0) > 0 &&
                                (if (stage > 0) (it.reps ?: 0) < upper else (it.reps ?: 0) > lower)
                        }
                        BProgressionField.LOAD -> loadCompatible
                        BProgressionField.MOVEMENT_QUALITY -> false
                    }
                }
                when (field) {
                    BProgressionField.SETS -> {
                        sets = if (stage > 0) sets + sets.last().copy()
                        else sets.dropLast(1)
                        applied++
                    }
                    BProgressionField.REPS -> {
                        sets = sets.map { it.copy(reps = (it.reps ?: 0) + if (stage > 0) 1 else -1) }
                        applied++
                    }
                    BProgressionField.LOAD -> {
                        // No baseline kg is known until the user supplies it in the preview.
                        loadSteps += if (stage > 0) 1 else -1
                        applied++
                    }
                    else -> Unit // exhaustion is a visible note, never silently forced
                }
            }
            if (!main && applied > 0) accessoryChanged = true
            item.copy(
                setCount = sets.size,
                sets = sets,
                plannedProgression = "B_BLOCK_VOLUME_STEP=" + stage +
                    ";PURPOSE=" + goal.name + ";LOAD_STEPS=" + loadSteps +
                    ";APPLIED_STEPS=" + applied + ";AMPLITUDE=" + resolvedAmplitude.name +
                    ";PREFERRED=" + defaultPriority.first().name +
                    (if (goal == BBlockGoal.SPORT_SUPPORT) ";MOVEMENT_QUALITY_PRIORITY_USER_REVIEW" else "") +
                    (if (applied < amount) ";LIMITED_BY_REP_SET_OR_EQUIPMENT" else "") +
                    ";START_LOAD_USER_REQUIRED;RPE_USER_REVIEW"
            )
        }
        return session.copy(items = updated, nominalSets = updated.sumOf(V22ExercisePlan::setCount))
    }

    /** Explicit non-barbell equipment cannot use the user's paired-plate increment. */
    private fun supportsPairedPlateIncrement(key: String, exercise: Exercise?): Boolean {
        val tags = listOfNotNull(exercise?.equipment, exercise?.equipmentTags).joinToString(" ").lowercase()
        if ("barbell" in tags || "바벨" in tags || key.startsWith("barbell_")) return true
        // Unknown equipment remains user-calibrated, not silently excluded as impossible.
        return tags.isBlank()
    }
}
