package com.training.trackplanner.data

internal class ProgramScoringPolicy(
    private val coveragePolicy: CoverageAccountingPolicy = CoverageAccountingPolicy.DEFAULT
) {
    fun score(
        candidate: ProgramCandidate,
        role: ProgramExerciseRole,
        slot: ProgramTrainingSlot,
        dayIntensity: ProgramDayIntensity,
        request: ProgramSkeletonRequest,
        week: ProgramWeekPlan,
        fatigueGate: ProgramFatigueGate,
        selectionHistory: ProgramSelectionHistory,
        absoluteDay: Int,
        templateSlot: TemplateExerciseSlot,
        exposureTarget: NumericExposureTarget?,
        totalWeeks: Int
    ): Double {
        val badmintonWeight = request.badmintonTransferRatio
        val strengthWeight = 1.0 - badmintonWeight
        val repeatPenalty = selectionHistory.penalty(candidate, week.weekIndex, absoluteDay)
        val ratioFit = badmintonWeight * candidate.badmintonFit + strengthWeight * candidate.strengthFit
        val intensityFit = when {
            dayIntensity == ProgramDayIntensity.HARD && candidate.highStress -> 1.1
            dayIntensity == ProgramDayIntensity.LIGHT && candidate.highStress -> -2.0
            dayIntensity == ProgramDayIntensity.LIGHT && candidate.isRecovery -> 1.4
            else -> 0.4
        }
        val phaseFit = when {
            week.deloadFlag && candidate.highStress -> -2.5
            week.weekType == ProgramWeekType.INTENSIFY.name && candidate.isAnchor -> 1.0
            week.weekType == ProgramWeekType.ADAPT.name && candidate.isRecovery -> 0.8
            else -> 0.0
        }
        val recoveryContext = slot in EXPANDED_RECOVERY_SLOTS || week.deloadFlag ||
            fatigueGate.band == ProgramFatigueBand.RED
        val rehabLikePenalty = when {
            !candidate.isRehabLikeActivation -> 0.0
            recoveryContext -> 0.35
            role == ProgramExerciseRole.ANCHOR -> 8.0
            week.weekType in PERFORMANCE_WEEK_TYPES -> 4.0
            role == ProgramExerciseRole.PREHAB -> 1.5
            else -> 2.5
        }
        val coverageCredit = templateSlot.targetSlot
            ?.let { requested -> coveragePolicy.credit(candidate.slotCapabilities, requested).value }
            ?: 0.0
        val exposureBalance = exposureTarget?.let { target ->
            exposureBalanceAdjustment(
                target = target,
                currentCredit = selectionHistory.coverage(target.slot),
                weekIndex = week.weekIndex,
                totalWeeks = totalWeeks
            )
        } ?: 0.0
        return candidate.slotFit(slot) * 2.2 +
            candidate.roleFit(role) * 2.0 +
            coverageCredit * 3.0 + exposureBalance +
            candidate.templateSpecificityFit(templateSlot.targetSlot) +
            ratioFit * 1.8 +
            intensityFit + phaseFit +
            candidate.metadataConfidenceFit - repeatPenalty - rehabLikePenalty
    }

    private fun exposureBalanceAdjustment(
        target: NumericExposureTarget,
        currentCredit: Double,
        weekIndex: Int,
        totalWeeks: Int
    ): Double {
        val progress = weekIndex.toDouble() / totalWeeks.coerceAtLeast(1)
        val minimumToDate = target.minimum * progress
        val preferredToDate = target.preferred * progress
        val maximumToDate = target.maximum * progress
        return when {
            currentCredit < minimumToDate -> 1.25
            currentCredit < preferredToDate -> 0.65
            maximumToDate > 0.0 && currentCredit >= maximumToDate -> -1.25
            else -> 0.15
        }
    }

    private companion object {
        val EXPANDED_RECOVERY_SLOTS = setOf(
            ProgramTrainingSlot.RECOVERY_PREHAB,
            ProgramTrainingSlot.MICRO_RECOVERY
        )
        val PERFORMANCE_WEEK_TYPES = setOf(
            ProgramWeekType.BUILD.name,
            ProgramWeekType.HIGH.name,
            ProgramWeekType.BUILD_PLUS.name,
            ProgramWeekType.INTENSIFY.name,
            ProgramWeekType.REALIZATION.name
        )
    }
}
