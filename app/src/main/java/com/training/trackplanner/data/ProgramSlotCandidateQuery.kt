package com.training.trackplanner.data

internal data class ProgramSlotCandidateQueryResult(
    val scored: List<Pair<ProgramCandidate, Double>>,
    val trace: ProgramCandidateTrace
)

internal class ProgramSlotCandidateQuery(
    private val coveragePolicy: CoverageAccountingPolicy = CoverageAccountingPolicy.DEFAULT,
    private val selectedExerciseScorePolicy: ProgramSelectedExerciseScorePolicy = ProgramSelectedExerciseScorePolicy()
) {
    fun query(
        inventory: ProgramCandidateInventoryResult,
        selected: List<ProgramCandidate>,
        plannedSlot: PlannedSlot,
        templateSlot: TemplateExerciseSlot,
        week: ProgramWeekPlan,
        weekNumber: Int,
        absoluteDay: Int,
        repeatAllowed: (ProgramCandidate) -> Boolean,
        fatigueAllowed: (ProgramCandidate) -> Boolean,
        sessionAllowed: (ProgramCandidate) -> Boolean,
        score: (ProgramCandidate) -> Double,
        scoreTrace: ((ProgramCandidate, Double) -> ProgramCandidateScoreTrace)? = null,
        selectionPoolSize: Int,
        selectedCount: Int
    ): ProgramSlotCandidateQueryResult {
        val base = inventory.candidates.filterNot { candidate ->
            selected.any { it.exercise.id == candidate.exercise.id }
        }
        val selectedMainFiltered = templateSlot.selectedMainStableKey
            .takeIf(String::isNotBlank)
            ?.let { stableKey ->
                base.filter { candidate -> selectedExerciseScorePolicy.matchesSelectedMainStableKey(candidate, stableKey) }
            }
            ?: base
        val capabilityMatched = selectedMainFiltered.filter { candidate ->
            templateSlot.targetSlot?.let { candidate.slotCapabilities.hasAny(it) }
                ?: (candidate.allowedForSlot(plannedSlot.slot) && candidate.allowedForRole(plannedSlot.slot, templateSlot.role))
        }
        val repeatAllowedCandidates = capabilityMatched.filter(repeatAllowed)
        val fatigueAllowedCandidates = repeatAllowedCandidates.filter(fatigueAllowed)
        val templateResult = templateAllowed(
            candidates = fatigueAllowedCandidates,
            plannedSlot = plannedSlot,
            templateSlot = templateSlot
        )
        val sessionAllowedCandidates = templateResult.candidates.filter(sessionAllowed)
        val scored = sessionAllowedCandidates
            .map { candidate -> candidate to score(candidate) }
            .sortedByDescending { it.second }
        val pool = adaptiveSelectionPool(scored, selectionPoolSize)
        val poolIds = pool.map { (candidate, _) -> candidate.exercise.id }.toSet()
        val scoreAdjustments = scoreTrace?.let { trace ->
            scored.map { (candidate, finalScore) ->
                trace(candidate, finalScore).copy(
                    selectionWindowIncluded = candidate.exercise.id in poolIds
                )
            }
        }.orEmpty()
        val traceWarnings = buildList {
            addAll(templateResult.warnings)
            if (templateSlot.selectedMainStableKey.isNotBlank()) {
                if (selectedMainFiltered.isEmpty()) {
                    add("SELECTED_MAIN_SLOT_RESERVATION_FAILED: ${templateSlot.selectedMainStableKey}")
                } else {
                    add("SELECTED_MAIN_SLOT_RESERVED: ${templateSlot.selectedMainStableKey}")
                }
            }
            if (inventory.notExcludedByUser > 0 && capabilityMatched.size <= 2) add("PROGRAM_SLOT_LOW_CANDIDATE_COUNT")
            if (templateResult.templateCollapsed) add("PROGRAM_SLOT_TEMPLATE_GATE_COLLAPSED")
            if (repeatAllowedCandidates.isNotEmpty() && fatigueAllowedCandidates.isEmpty()) add("PROGRAM_SLOT_FATIGUE_GATE_COLLAPSED")
            if (pool.size < scored.size) add("PROGRAM_SLOT_SELECTION_POOL_TRIMMED")
        }
        return ProgramSlotCandidateQueryResult(
            scored = pool,
            trace = ProgramCandidateTrace(
                weekNumber = weekNumber,
                dayOfWeek = plannedSlot.dayOfWeek,
                requestedTemplateSlot = templateSlot.targetSlot?.name.orEmpty(),
                selectedMainReservationStableKey = templateSlot.selectedMainStableKey,
                role = templateSlot.role.name,
                allActive = inventory.allActive,
                programSelectable = inventory.programSelectable,
                equipmentMatched = inventory.equipmentMatched,
                notExcludedByUser = inventory.notExcludedByUser,
                capabilityMatched = capabilityMatched.size,
                repeatAllowed = repeatAllowedCandidates.size,
                fatigueAllowed = fatigueAllowedCandidates.size,
                templateAllowed = templateResult.candidates.size,
                sessionAllowed = sessionAllowedCandidates.size,
                scored = scored.size,
                selectionPool = pool.size,
                selected = selectedCount,
                warnings = traceWarnings,
                scoreAdjustments = scoreAdjustments
            )
        )
    }

    private fun templateAllowed(
        candidates: List<ProgramCandidate>,
        plannedSlot: PlannedSlot,
        templateSlot: TemplateExerciseSlot
    ): TemplateAllowedResult {
        val target = templateSlot.targetSlot
        if (target == null) {
            return TemplateAllowedResult(
                candidates = candidates.filter {
                    it.allowedForSlot(plannedSlot.slot) && it.allowedForRole(plannedSlot.slot, templateSlot.role)
                }
            )
        }
        val roleSafeCandidates = candidates.filter {
            it.safeForTemplateRole(templateSlot) && it.safeForTemplateTarget(target)
        }
        val exact = roleSafeCandidates.filter { coveragePolicy.credit(it.slotCapabilities, target) == CoverageCredit.FULL }
        if (exact.isNotEmpty()) return TemplateAllowedResult(exact)
        val partial = roleSafeCandidates.filter { coveragePolicy.credit(it.slotCapabilities, target).value >= CoverageCredit.PARTIAL.value }
        if (partial.isNotEmpty()) {
            return TemplateAllowedResult(
                candidates = partial,
                warnings = if (templateSlot.required) listOf("TEMPLATE_REQUIRED_SLOT_RELAXED: ${templateSlot.targetSlot}") else emptyList(),
                templateCollapsed = true
            )
        }
        val sameRole = roleSafeCandidates.filter {
            it.allowedForSlot(plannedSlot.slot) && it.allowedForRole(plannedSlot.slot, templateSlot.role)
        }
        if (sameRole.isNotEmpty()) {
            return TemplateAllowedResult(
                candidates = sameRole,
                warnings = if (templateSlot.required) listOf("TEMPLATE_REQUIRED_SLOT_FALLBACK_USED: ${templateSlot.targetSlot}") else emptyList(),
                templateCollapsed = true
            )
        }
        val weak = roleSafeCandidates.filter { coveragePolicy.credit(it.slotCapabilities, target) == CoverageCredit.WEAK }
        if (weak.isNotEmpty()) {
            return TemplateAllowedResult(
                candidates = weak,
                warnings = if (templateSlot.required) listOf("TEMPLATE_REQUIRED_SLOT_FALLBACK_USED: ${templateSlot.targetSlot}") else emptyList(),
                templateCollapsed = true
            )
        }
        val fallback = roleSafeCandidates.filter {
            it.slotCapabilities.hasAny(target) &&
                it.slotCapabilities.source in setOf(SlotCapabilitySource.LEGACY_METADATA, SlotCapabilitySource.NAME_FALLBACK)
        }
        if (fallback.isNotEmpty()) {
            return TemplateAllowedResult(
                candidates = fallback,
                warnings = if (templateSlot.required) listOf("TEMPLATE_REQUIRED_SLOT_FALLBACK_USED: ${templateSlot.targetSlot}") else emptyList(),
                templateCollapsed = true
            )
        }
        return TemplateAllowedResult(
            candidates = emptyList(),
            warnings = if (templateSlot.required) listOf("TEMPLATE_REQUIRED_SLOT_UNFILLED: ${templateSlot.targetSlot}") else emptyList(),
            templateCollapsed = true
        )
    }

    private fun ProgramCandidate.safeForTemplateRole(templateSlot: TemplateExerciseSlot): Boolean {
        if (templateSlot.role !in setOf(ProgramExerciseRole.CORE, ProgramExerciseRole.PREHAB)) return true
        return !slotCapabilities.hasAny(
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME,
            ProgramSlotId.BADMINTON_DECEL_COD,
            ProgramSlotId.BADMINTON_FOOTWORK_REACTION
        ) && !hasExplosiveOrCodToken()
    }

    private fun ProgramCandidate.hasExplosiveOrCodToken(): Boolean {
        val values = buildList {
            add(exercise.name)
            metadata?.let { value ->
                add(value.movementFamily)
                add(value.movementSubtype)
                add(value.programSlot)
                add(value.primaryStressProfile)
                addAll(value.secondaryStressTags.values)
                addAll(value.badmintonTransferType.values)
                addAll(value.badmintonPhysicalQualities.values)
            }
        }
        return values.any { value ->
            CORE_PREHAB_BLOCKED_TOKENS.any { token -> value.contains(token, ignoreCase = true) }
        }
    }

    private fun ProgramCandidate.safeForTemplateTarget(target: ProgramSlotId): Boolean {
        if (target != ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT) return true
        val values = buildList {
            add(exercise.name)
            metadata?.let { value ->
                add(value.movementFamily)
                add(value.movementSubtype)
                add(value.programSlot)
                add(value.redundancyGroup)
            }
        }
        if (values.any { value -> ATHLETIC_OVERHEAD_BLOCKED_TOKENS.any { value.contains(it, ignoreCase = true) } }) {
            return false
        }
        return values.any { value -> ATHLETIC_OVERHEAD_ALLOWED_TOKENS.any { value.contains(it, ignoreCase = true) } }
    }

    private companion object {
        val CORE_PREHAB_BLOCKED_TOKENS = setOf(
            "SLAM",
            "THROW",
            "TOSS",
            "PLYOMETRIC",
            "EXPLOSIVE",
            "JUMP",
            "HOP",
            "SPRINT",
            "BOUND",
            "DECELERATION",
            "CHANGE_OF_DIRECTION"
        )
        val ATHLETIC_OVERHEAD_BLOCKED_TOKENS = setOf(
            "BENCH",
            "HORIZONTAL_PRESS",
            "PUSH_UP",
            "PUSH-UP",
            "CHEST_PRESS"
        )
        val ATHLETIC_OVERHEAD_ALLOWED_TOKENS = setOf(
            "OVERHEAD",
            "LANDMINE_PRESS",
            "HALF_KNEELING_PRESS",
            "SHOULDER_PRESS",
            "KETTLEBELL_PRESS",
            "VERTICAL_PUSH",
            "PRESS_SUPPORT"
        )
    }

    private fun adaptiveSelectionPool(
        scored: List<Pair<ProgramCandidate, Double>>,
        selectionPoolSize: Int
    ): List<Pair<ProgramCandidate, Double>> {
        if (scored.size <= selectionPoolSize) return scored
        val best = scored.first().second
        val scoreWindow = scored.filter { best - it.second <= 2.0 }
        return if (scoreWindow.size >= 8) {
            scoreWindow
        } else {
            scored.take(selectionPoolSize)
        }
    }

    private data class TemplateAllowedResult(
        val candidates: List<ProgramCandidate>,
        val warnings: List<String> = emptyList(),
        val templateCollapsed: Boolean = false
    )
}
