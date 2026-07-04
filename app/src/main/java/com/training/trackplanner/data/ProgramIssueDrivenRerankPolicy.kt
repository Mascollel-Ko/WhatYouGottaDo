package com.training.trackplanner.data

internal class ProgramIssueDrivenRerankPolicy(
    private val corePatternPolicy: ProgramCorePatternPolicy = ProgramCorePatternPolicy()
) {
    fun repair(
        skeleton: GeneratedProgramSkeleton,
        evaluation: ProgramEvaluation,
        reservoir: ProgramCandidateReservoir?
    ): ProgramRepairResult {
        if (reservoir == null) return ProgramRepairResult(skeleton, emptyList())
        val issueTypes = evaluation.issues.map(ProgramEvaluationIssue::type).toSet()
        val anchorRepair = if (
            ProgramEvaluationIssueType.LOW_STRENGTH_ANCHOR in issueTypes ||
            ProgramEvaluationIssueType.LOADED_STRENGTH_UNDERUSED in issueTypes
        ) {
            replaceWeakSlotWithFoundation(skeleton, reservoir)
        } else {
            ProgramRepairResult(skeleton, emptyList())
        }
        if (anchorRepair.actions.isNotEmpty()) return anchorRepair

        return if (ProgramEvaluationIssueType.TOO_MUCH_CORE_REPETITION in issueTypes) {
            replaceRepeatedCorePattern(skeleton, reservoir)
        } else {
            ProgramRepairResult(skeleton, emptyList())
        }
    }

    fun replaceRepeatedCorePattern(
        skeleton: GeneratedProgramSkeleton,
        reservoir: ProgramCandidateReservoir
    ): ProgramRepairResult {
        val target = skeleton.items
            .filter { corePatternPolicy.corePattern(it) == ProgramCorePattern.TRUNK_FLEXION_HIP_FLEXION }
            .groupBy { it.stableKey }
            .values
            .firstOrNull { it.size > 1 }
            ?.drop(1)
            ?.firstOrNull()
            ?: return ProgramRepairResult(skeleton, emptyList())
        val replacement = reservoir.candidates.firstOrNull { candidate ->
            val classification = reservoir.classification(candidate)
            classification.corePattern in ROTATION_PATTERNS &&
                candidate.exercise.stableKey != target.stableKey &&
                skeleton.items.none { it.weekNumber == target.weekNumber && it.dayOfWeek == target.dayOfWeek && it.stableKey == candidate.exercise.stableKey }
        } ?: return ProgramRepairResult(skeleton, emptyList())
        return ProgramRepairResult(
            skeleton = skeleton.replace(target, replacement, reservoir.classification(replacement), ProgramExerciseRole.CORE),
            actions = listOf("REOPEN_REPEATED_CORE_SLOT")
        )
    }

    private fun replaceWeakSlotWithFoundation(
        skeleton: GeneratedProgramSkeleton,
        reservoir: ProgramCandidateReservoir
    ): ProgramRepairResult {
        if (skeleton.items.isNotEmpty() && skeleton.items.all { it.dayIntensity == ProgramDayIntensity.LIGHT.name }) {
            return ProgramRepairResult(skeleton, emptyList())
        }
        val target = skeleton.items.firstOrNull { item ->
            item.selectionRole in WEAK_ROLE_NAMES ||
                corePatternPolicy.corePattern(item) != ProgramCorePattern.NONE
        } ?: return ProgramRepairResult(skeleton, emptyList())
        val replacement = reservoir.candidates.firstOrNull { candidate ->
            val classification = reservoir.classification(candidate)
            classification.tier == ProgramCandidateTier.FOUNDATION_MAIN_WORTHY &&
                skeleton.items.none { it.weekNumber == target.weekNumber && it.dayOfWeek == target.dayOfWeek && it.stableKey == candidate.exercise.stableKey }
        } ?: reservoir.candidates.firstOrNull { candidate ->
            val classification = reservoir.classification(candidate)
            classification.tier == ProgramCandidateTier.LOADED_SUPPORT &&
                skeleton.items.none { it.weekNumber == target.weekNumber && it.dayOfWeek == target.dayOfWeek && it.stableKey == candidate.exercise.stableKey }
        } ?: return ProgramRepairResult(skeleton, emptyList())
        return ProgramRepairResult(
            skeleton = skeleton.replace(target, replacement, reservoir.classification(replacement), ProgramExerciseRole.ANCHOR),
            actions = listOf("REOPEN_WEAK_SLOT_FOR_FOUNDATION")
        )
    }

    private fun GeneratedProgramSkeleton.replace(
        target: ProgramSkeletonItem,
        candidate: ProgramCandidate,
        classification: ProgramCandidateClassification,
        role: ProgramExerciseRole
    ): GeneratedProgramSkeleton = copy(
        items = items.map { item ->
            if (item.localId == target.localId) item.replacedWith(candidate, classification, role) else item
        }
    )

    private fun ProgramSkeletonItem.replacedWith(
        candidate: ProgramCandidate,
        classification: ProgramCandidateClassification,
        role: ProgramExerciseRole
    ): ProgramSkeletonItem {
        val requestedSlot = candidate.resolvedSlotForRole(role)
            ?: classification.foundationPatterns.firstOrNull()?.slot
            ?: requestedTemplateSlot.takeIf(String::isNotBlank)?.let { name ->
                runCatching { ProgramSlotId.valueOf(name) }.getOrNull()
            }
        return copy(
            exerciseId = candidate.exercise.id,
            exerciseName = candidate.exercise.name,
            category = candidate.exercise.category,
            restSeconds = candidate.exercise.defaultRestSeconds,
            stableKey = candidate.exercise.stableKey,
            selectionRole = role.name,
            movementFamily = candidate.metadata?.movementFamily.orEmpty(),
            movementSubtype = candidate.metadata?.movementSubtype.orEmpty(),
            metadataProgramSlot = candidate.metadata?.programSlot.orEmpty(),
            redundancyGroup = candidate.metadata?.redundancyGroup.orEmpty(),
            strengthProgressionGroup = candidate.metadata?.strengthProgressionGroup.orEmpty(),
            primaryStressProfile = candidate.metadata?.primaryStressProfile.orEmpty(),
            stressMagnitudeHint = candidate.metadata?.stressMagnitudeHint.orEmpty(),
            neuromuscularStressLevel = candidate.metadata?.neuromuscularStressLevel.orEmpty(),
            systemicMuscularStressLevel = candidate.metadata?.systemicMuscularStressLevel.orEmpty(),
            localMuscularStressLevel = candidate.metadata?.localMuscularStressLevel.orEmpty(),
            jointTendonImpactStressLevel = candidate.metadata?.jointTendonImpactStressLevel.orEmpty(),
            movementFocusDemandLevel = candidate.metadata?.movementFocusDemandLevel.orEmpty(),
            recoveryDurationClass = candidate.metadata?.recoveryDurationClass.orEmpty(),
            badmintonTransferLevel = candidate.metadata?.badmintonTransferLevel.orEmpty(),
            directSportSession = candidate.isDirectSportSession,
            rehabLikeActivation = candidate.isRehabLikeActivation,
            scapularStabilityExposure = candidate.isScapularStabilityExposure,
            primarySlotCapabilities = candidate.slotCapabilities.primary.map(ProgramSlotId::name).sorted(),
            secondarySlotCapabilities = candidate.slotCapabilities.secondary.map(ProgramSlotId::name).sorted(),
            weakSlotCapabilities = candidate.slotCapabilities.weakMatches.map(ProgramSlotId::name).sorted(),
            slotCapabilitySource = candidate.slotCapabilities.source.name,
            slotCapabilityConfidence = candidate.slotCapabilities.confidence.name,
            slotCapabilityWarnings = candidate.slotCapabilities.warnings,
            requestedTemplateSlot = requestedSlot?.name.orEmpty()
        )
    }

    private val ProgramFoundationPattern.slot: ProgramSlotId
        get() = when (this) {
            ProgramFoundationPattern.SQUAT -> ProgramSlotId.LOWER_SQUAT_PATTERN
            ProgramFoundationPattern.HINGE -> ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN
            ProgramFoundationPattern.UPPER_PULL -> ProgramSlotId.UPPER_PULL_ANCHOR
            ProgramFoundationPattern.PRESS -> ProgramSlotId.UPPER_PUSH_SUPPORT
        }

    private companion object {
        val WEAK_ROLE_NAMES = setOf(
            ProgramExerciseRole.CORE.name,
            ProgramExerciseRole.PREHAB.name,
            ProgramExerciseRole.ACCESSORY.name
        )
        val ROTATION_PATTERNS = setOf(
            ProgramCorePattern.ANTI_EXTENSION,
            ProgramCorePattern.ANTI_ROTATION,
            ProgramCorePattern.LATERAL_STABILITY,
            ProgramCorePattern.CARRY
        )
    }
}
