package com.training.trackplanner.data

internal class ProgramScoringPolicy(
    private val coveragePolicy: CoverageAccountingPolicy = CoverageAccountingPolicy.DEFAULT,
    private val compositionPolicy: ProgramCompositionPolicy = ProgramCompositionPolicy()
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
        val loadedStrengthBoost = loadedStrengthBoost(candidate, role, request)
        val compositionFit = compositionPolicy.strengthAnchorAdjustment(candidate, role, request)
        return candidate.slotFit(slot) * 2.2 +
            candidate.roleFit(role) * 2.0 +
            coverageCredit * 3.0 + exposureBalance +
            candidate.templateSpecificityFit(templateSlot.targetSlot) +
            ratioFit * 1.8 +
            intensityFit + phaseFit + loadedStrengthBoost + compositionFit +
            candidate.metadataConfidenceFit - repeatPenalty - rehabLikePenalty
    }

    private fun loadedStrengthBoost(
        candidate: ProgramCandidate,
        role: ProgramExerciseRole,
        request: ProgramSkeletonRequest
    ): Double {
        if (!candidate.isLoadedStrength) return 0.0
        val selected = request.availableEquipment.map(String::uppercase).toSet()
        val loadedAvailable = selected.isEmpty() || selected.any(LOADED_STRENGTH_EQUIPMENT::contains)
        if (!loadedAvailable) return 0.0
        val roleBoost = when (role) {
            ProgramExerciseRole.ANCHOR -> 1.4
            ProgramExerciseRole.SUPPORT -> 1.1
            ProgramExerciseRole.ACCESSORY -> 0.45
            else -> 0.25
        }
        val strengthShare = (1.0 - request.badmintonTransferRatio).coerceIn(0.10, 0.70)
        return roleBoost * (0.8 + strengthShare)
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
        val LOADED_STRENGTH_EQUIPMENT = setOf(
            "BARBELL",
            "DUMBBELL",
            "MACHINE",
            "CABLE",
            "SMITH_MACHINE",
            "LEG_PRESS_MACHINE",
            "HACK_SQUAT_MACHINE",
            "LEG_CURL_MACHINE",
            "LEG_EXTENSION_MACHINE",
            "바벨",
            "덤벨",
            "머신",
            "케이블",
            "스미스",
            "스미스머신",
            "레그프레스",
            "핵스쿼트",
            "레그컬",
            "레그익스텐션",
            "EZ바"
        )
    }
}
