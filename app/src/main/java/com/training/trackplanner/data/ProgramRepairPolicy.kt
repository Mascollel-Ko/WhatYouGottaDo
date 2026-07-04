package com.training.trackplanner.data

internal class ProgramRepairPolicy(
    private val issueDrivenRerankPolicy: ProgramIssueDrivenRerankPolicy = ProgramIssueDrivenRerankPolicy()
) {
    fun repair(
        skeleton: GeneratedProgramSkeleton,
        evaluation: ProgramEvaluation,
        reservoir: ProgramCandidateReservoir? = null
    ): ProgramRepairResult {
        val issueDrivenRepair = issueDrivenRerankPolicy.repair(skeleton, evaluation, reservoir)
        if (issueDrivenRepair.actions.isNotEmpty()) return issueDrivenRepair

        if (evaluation.issues.none {
                it.type == ProgramEvaluationIssueType.HIGH_LOWER_BODY_FATIGUE_CLUSTER ||
                    it.type == ProgramEvaluationIssueType.NO_RECOVERY_AFTER_HIGH_FATIGUE_WEEK
            }
        ) {
            return ProgramRepairResult(skeleton, emptyList())
        }
        val repaired = softenFirstAdjacentHighLowerDay(skeleton)
        return if (repaired == skeleton) {
            ProgramRepairResult(skeleton, emptyList())
        } else {
            ProgramRepairResult(
                skeleton = repaired,
                actions = listOf("SOFTEN_ADJACENT_HIGH_LOWER_DAY")
            )
        }
    }

    private fun softenFirstAdjacentHighLowerDay(skeleton: GeneratedProgramSkeleton): GeneratedProgramSkeleton {
        val highLowerDays = skeleton.items
            .filter(::isHighLowerStress)
            .map { it.weekNumber to it.dayOfWeek }
            .distinct()
            .groupBy(Pair<Int, Int>::first)
            .values
            .firstOrNull { days -> days.map(Pair<Int, Int>::second).sorted().zipWithNext().any { (a, b) -> b - a <= 1 } }
            ?: return skeleton
        val targetDay = highLowerDays.map(Pair<Int, Int>::second).sorted().zipWithNext()
            .first { (a, b) -> b - a <= 1 }
            .second
        val targetWeek = highLowerDays.first().first
        return skeleton.copy(
            items = skeleton.items.map { item ->
                if (item.weekNumber == targetWeek && item.dayOfWeek == targetDay && isHighLowerStress(item)) {
                    item.copy(
                        trainingSlot = ProgramTrainingSlot.RECOVERY_WEAKPOINT.name,
                        dayIntensity = ProgramDayIntensity.LIGHT.name,
                        requestedTemplateSlot = ProgramSlotId.RECOVERY_PREHAB_LIGHT.name,
                        setCount = (item.setCount - 1).coerceAtLeast(1),
                        stressMagnitudeHint = "LOW",
                        neuromuscularStressLevel = "LOW",
                        jointTendonImpactStressLevel = "LOW"
                    )
                } else {
                    item
                }
            }
        )
    }

    private fun isHighLowerStress(item: ProgramSkeletonItem): Boolean {
        val lowerOrCod = item.trainingSlot in HIGH_LOWER_TRAINING_SLOTS ||
            item.requestedTemplateSlot in HIGH_LOWER_SLOT_NAMES ||
            item.primarySlotCapabilities.any(HIGH_LOWER_SLOT_NAMES::contains) ||
            item.secondarySlotCapabilities.any(HIGH_LOWER_SLOT_NAMES::contains)
        val highStress = item.dayIntensity == ProgramDayIntensity.HARD.name ||
            item.stressMagnitudeHint in setOf("HIGH", "VERY_HIGH") ||
            item.neuromuscularStressLevel in setOf("HIGH", "VERY_HIGH") ||
            item.jointTendonImpactStressLevel in setOf("HIGH", "VERY_HIGH")
        return lowerOrCod && highStress
    }

    private companion object {
        val HIGH_LOWER_TRAINING_SLOTS = setOf(
            ProgramTrainingSlot.LOWER_STRENGTH_HEAVY.name,
            ProgramTrainingSlot.BADMINTON_COD.name,
            ProgramTrainingSlot.BADMINTON_COD_DECEL.name,
            ProgramTrainingSlot.POWER_REACTIVE.name
        )
        val HIGH_LOWER_SLOT_NAMES = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN.name,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name,
            ProgramSlotId.BADMINTON_DECEL_COD.name,
            ProgramSlotId.BADMINTON_FOOTWORK_REACTION.name,
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME.name
        )
    }
}
