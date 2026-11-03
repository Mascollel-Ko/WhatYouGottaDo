package com.training.trackplanner.data.v22

/**
 * Opt-in block-program contract. The legacy 12-week B plan and saved metadata remain
 * readable while the new generator/UI are migrated in separate audited commits.
 */
enum class BBlockGoal { AUTO, STRENGTH, HYPERTROPHY, SPORT_SUPPORT, BALANCED, PEAK_EVALUATION }
enum class BVolumePattern { STEADY, PROGRESSIVE, WAVE, ACCUMULATE_RECOVER }
enum class BVolumeAdjustment { AUTO, REPS_FIRST, LOAD_FIRST, SETS_FIRST, CUSTOM }
enum class BChangeAmplitude { AUTO, SMALL, MEDIUM }
enum class BProgressionField { LOAD, SETS, REPS, MOVEMENT_QUALITY }

data class BBlockDefinition(
    val weeks: Int = 4,
    val goal: BBlockGoal = BBlockGoal.AUTO,
    val pattern: BVolumePattern = BVolumePattern.STEADY,
    val adjustment: BVolumeAdjustment = BVolumeAdjustment.AUTO,
    val amplitude: BChangeAmplitude = BChangeAmplitude.AUTO,
    val relationToPrevious: BBlockRelation = BBlockRelation.INDEPENDENT
) {
    init {
        require(weeks in 1..6) { "Block duration must be 1..6 weeks" }
        require(weeks != 1 || pattern == BVolumePattern.STEADY) { "One-week blocks are steady only" }
        require(weeks >= 3 || pattern !in setOf(BVolumePattern.WAVE, BVolumePattern.ACCUMULATE_RECOVER)) {
            "Wave and accumulate/recover require at least three weeks"
        }
    }
}

enum class BBlockRelation { INDEPENDENT, COMPLEMENT_PLANNED_GAPS, DEVELOP_PREVIOUS, PREPARE_NEXT }

data class BBlockProgramDefinition(
    val blocks: List<BBlockDefinition> = listOf(BBlockDefinition()),
    /** Independent recovery days *between* blocks, never inside a training block. */
    val recoveryDaysBetween: List<Int> = List((blocks.size - 1).coerceAtLeast(0)) { 0 }
) {
    init {
        require(blocks.size in 1..4) { "Expected 1..4 blocks" }
        require(recoveryDaysBetween.size == blocks.size - 1) { "One recovery entry per boundary" }
        require(recoveryDaysBetween.all { it in 0..7 }) { "Recovery must be 0..7 days" }
        require(blocks.first().relationToPrevious != BBlockRelation.COMPLEMENT_PLANNED_GAPS &&
            blocks.first().relationToPrevious != BBlockRelation.DEVELOP_PREVIOUS) {
            "The first block has no previous block"
        }
        require(blocks.last().relationToPrevious != BBlockRelation.PREPARE_NEXT) {
            "The last block has no next block"
        }
    }

    val trainingWeeks: Int get() = blocks.sumOf(BBlockDefinition::weeks)
    val durationDays: Int get() = trainingWeeks * 7 + recoveryDaysBetween.sum()
}

/**
 * Relative training-volume *steps*, NOT kg, %1RM, RPE or a physiological guarantee.
 * Concrete prescriptions must respect the selected goal and main-exercise metadata.
 */
internal object BBlockPeriodization {
    fun steps(block: BBlockDefinition): List<Int> = when (block.pattern) {
        BVolumePattern.STEADY -> List(block.weeks) { 0 }
        BVolumePattern.PROGRESSIVE -> (0 until block.weeks).toList()
        BVolumePattern.WAVE -> when (block.weeks) {
            3 -> listOf(0, 2, 1) // weak, strong, medium
            4 -> listOf(0, 2, 0, 2)
            5 -> listOf(0, 1, 2, 0, 2)
            6 -> listOf(0, 1, 2, 0, 1, 2)
            else -> error("Invalid wave length (checked by BBlockDefinition)")
        }
        BVolumePattern.ACCUMULATE_RECOVER ->
            (0 until block.weeks - 1).toList() + listOf(-1)
    }

    fun increasePriority(goal: BBlockGoal): List<BProgressionField> = when (goal) {
        BBlockGoal.STRENGTH -> listOf(BProgressionField.LOAD, BProgressionField.SETS, BProgressionField.REPS)
        BBlockGoal.HYPERTROPHY -> listOf(BProgressionField.REPS, BProgressionField.LOAD, BProgressionField.SETS)
        BBlockGoal.SPORT_SUPPORT -> listOf(BProgressionField.MOVEMENT_QUALITY, BProgressionField.REPS,
            BProgressionField.SETS, BProgressionField.LOAD)
        BBlockGoal.PEAK_EVALUATION -> listOf(BProgressionField.LOAD, BProgressionField.SETS, BProgressionField.REPS)
        BBlockGoal.AUTO, BBlockGoal.BALANCED -> emptyList() // resolve per exercise before prescription
    }

    fun decreasePriority(): List<BProgressionField> =
        listOf(BProgressionField.SETS, BProgressionField.REPS, BProgressionField.LOAD)

    /** A pair of plates is required for a symmetrically loaded barbell. */
    fun barbellIncrement(smallestPlateKg: Double): Double {
        require(smallestPlateKg.isFinite() && smallestPlateKg > 0.0)
        return smallestPlateKg * 2.0
    }

    fun proposedLoad(startKg: Double?, step: Int, smallestPlateKg: Double): Double? =
        startKg?.takeIf { it.isFinite() && it > 0.0 }?.let { base ->
            (base + step * barbellIncrement(smallestPlateKg)).coerceAtLeast(0.0)
        }

    /** Increase/decrease by one repetition per set and one set *per main exercise*. */
    fun proposedReps(startReps: Int, step: Int): Int = (startReps + step).coerceAtLeast(1)
    fun proposedMainSets(startSets: Int, step: Int): Int = (startSets + step).coerceAtLeast(1)
}
