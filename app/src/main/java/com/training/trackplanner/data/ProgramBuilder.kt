package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import java.time.LocalDate
import kotlin.math.roundToInt

class ProgramBuilder internal constructor(
    private val templateCatalog: ProgramTemplateCatalog = ProgramTemplateCatalog.DEFAULT,
    private val slotCapabilityResolver: SlotCapabilityResolver = SlotCapabilityResolver.DEFAULT,
    private val fatigueSlotPolicy: FatigueSlotPolicy = FatigueSlotPolicy.DEFAULT,
    private val coveragePolicy: CoverageAccountingPolicy = CoverageAccountingPolicy.DEFAULT
) {
    private val prescriptionPolicy = ProgramPrescriptionPolicy()
    private val candidateInventory = ProgramCandidateInventory(slotCapabilityResolver)
    private val slotCandidateQuery = ProgramSlotCandidateQuery(coveragePolicy)
    private val scoringPolicy = ProgramScoringPolicy(coveragePolicy)
    private val varietyPolicy = ProgramVarietyPolicy()
    private val sessionConstraintPolicy = ProgramSessionConstraintPolicy()
    private val reasonFormatter = ProgramSelectionReasonFormatter()
    private val compositionPolicy = ProgramCompositionPolicy()
    private val sessionDensityPolicy = ProgramSessionDensityPolicy()
    private val dayIntensityPolicy = ProgramDayIntensityPolicy()
    private val optimizationPolicy = ProgramOptimizationPolicy()
    private val periodizationPolicy = ProgramPeriodizationPlanPolicy()
    private val foundationAnchorPolicy = ProgramFoundationAnchorPolicy()
    private val rerankingPolicy = ProgramCandidateRerankingPolicy()
    private val selectedExerciseScorePolicy = ProgramSelectedExerciseScorePolicy()
    private val beamSelectionPolicy = ProgramBeamSelectionPolicy()
    private val corePatternPolicy = ProgramCorePatternPolicy()

    fun build(
        request: ProgramSkeletonRequest,
        exercises: List<Exercise>,
        history: List<WorkoutEntryWithSets>,
        today: LocalDate = LocalDate.now(),
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
        fatigueState: DailyFatigueState? = null
    ): GeneratedProgramSkeleton {
        val normalized = request.copy(
            durationWeeks = request.durationWeeks.coerceIn(2, 12),
            weeklyTrainingDays = request.availableDaysPerWeek,
            sessionMinutes = request.dailyAvailableMinutes,
            badmintonTransferRatio = request.badmintonTransferRatio.coerceIn(0.0, 0.9)
        )
        val fatigueGate = fatigueSlotPolicy.gate(fatigueState)
        val periodization = periodizationPolicy.resolveType(normalized)
        val baseWeekPlans = templateCatalog.weekPlans(normalized.durationWeeks, fatigueGate)
        val weekPlans = baseWeekPlans
        val templateSelection = templateCatalog.select(normalized)
        val exposureTargets = templateCatalog.exposureTargets(templateSelection, normalized)
            .associateBy(NumericExposureTarget::slot)
        val schedule = templateSelection.sessions.map { planned ->
            fatigueSlotPolicy.adapt(planned, fatigueGate, ProgramFatigueUseCase.PROGRAM_PLANNING)
        }
        val inventory = candidateInventory.collect(
            exercises = exercises,
            runtimeMetadataCatalog = runtimeMetadataCatalog,
            availableEquipment = normalized.availableEquipment,
            excludedExerciseStableKeys = normalized.excludedExerciseStableKeys
        )
        val candidates = inventory.candidates
        val availableSelectedMainStableKeys = inventory.reservoir.candidates
            .filter(selectedExerciseScorePolicy::isSelectedMainExercise)
            .map { it.exercise.stableKey }
            .toSet()

        val weightSuggestionPolicy = ProgramWeightSuggestionPolicy(history, exercises)
        val generated = mutableListOf<ProgramSkeletonItem>()
        val selectionHistory = ProgramSelectionHistory()
        val warnings = mutableListOf<String>()
        val candidateTraces = mutableListOf<ProgramCandidateTrace>()
        var timeBudgetTrimmed = false
        val periodizationWeeks = periodizationPolicy.plan(normalized, periodization, weekPlans, schedule)
        weekPlans.forEach { week ->
            val periodizedWeek = periodizationWeeks.first { it.weekIndex == week.weekIndex }
            val weekSchedule = periodizationPolicy.scheduleForWeek(
                base = schedule,
                periodizedWeek = periodizedWeek
            )
            weekSchedule.forEachIndexed { dayIndex, day ->
                val exerciseSlots = foundationAnchorPolicy.reserveSlots(
                    slots = templateCatalog.exerciseSlots(
                        day,
                        prescriptionPolicy.exerciseCount(normalized.dailyAvailableMinutes)
                    ),
                    request = normalized,
                    week = periodizedWeek,
                    plannedSlot = day,
                    availableSelectedMainStableKeys = availableSelectedMainStableKeys,
                    generatedItems = generated
                )
                val selected = mutableListOf<ProgramCandidate>()
                val sessionBudgetSeconds = normalized.dailyAvailableMinutes * 60
                var estimatedSessionSeconds = prescriptionPolicy.warmupReserveSeconds(normalized.dailyAvailableMinutes)
                exerciseSlots.forEachIndexed { itemIndex, templateSlot ->
                    val role = templateSlot.role
                    val absoluteDay = (week.weekIndex - 1) * 7 + day.dayOfWeek
                    val scoreTraceByExerciseId = mutableMapOf<Long, ProgramCandidateScoreTrace>()
                    val query = slotCandidateQuery.query(
                        inventory = inventory,
                        selected = selected,
                        plannedSlot = day,
                        templateSlot = templateSlot,
                        week = week,
                        weekNumber = week.weekIndex,
                        absoluteDay = absoluteDay,
                        repeatAllowed = { candidate ->
                            varietyPolicy.allowsRepeat(
                                history = selectionHistory,
                                candidate = candidate,
                                absoluteDay = absoluteDay,
                                minimumGapDays = templateSlot.minimumRepeatGapDays
                            )
                        },
                        fatigueAllowed = { candidate ->
                            fatigueSlotPolicy.allows(candidate, fatigueGate, ProgramFatigueUseCase.PROGRAM_PLANNING)
                        },
                        sessionAllowed = { candidate ->
                            sessionConstraintPolicy.sessionAllows(selected, candidate, day.slot, week, fatigueGate)
                        },
                        captainChairAllowed = { candidate ->
                            !selectedExerciseScorePolicy.isCaptainChairLegRaise(candidate) ||
                                captainChairCanBeConsidered(
                                    request = normalized,
                                    availableSelectedMainStableKeys = availableSelectedMainStableKeys,
                                    generatedItems = generated,
                                    weekNumber = week.weekIndex
                                )
                        },
                        score = { candidate ->
                            val baseScore = scoringPolicy.score(
                                candidate = candidate,
                                role = role,
                                slot = day.slot,
                                dayIntensity = day.intensity,
                                request = normalized,
                                week = week,
                                fatigueGate = fatigueGate,
                                selectionHistory = selectionHistory,
                                absoluteDay = absoluteDay,
                                templateSlot = templateSlot,
                                exposureTarget = templateSlot.targetSlot?.let(exposureTargets::get),
                                totalWeeks = normalized.durationWeeks
                            )
                            val contextRerankScore = rerankingPolicy.adjustment(
                                candidate = candidate,
                                classification = inventory.reservoir.classification(candidate),
                                context = ProgramCandidateScoreContext(
                                    request = normalized,
                                    week = week,
                                    periodizedWeek = periodizedWeek,
                                    plannedSlot = day,
                                    templateSlot = templateSlot,
                                    selectedInSession = selected,
                                    generatedItems = generated
                                )
                            )
                            val adjustment = selectedExerciseScorePolicy.adjust(baseScore + contextRerankScore, candidate)
                            scoreTraceByExerciseId[candidate.exercise.id] = ProgramCandidateScoreTrace(
                                exerciseName = candidate.exercise.name,
                                stableKey = candidate.exercise.stableKey,
                                baseScore = baseScore,
                                contextRerankScore = contextRerankScore,
                                selectedMainBoostApplied = adjustment.selectedMainBoostApplied,
                                captainChairPenaltyApplied = adjustment.captainChairPenaltyApplied,
                                finalScore = adjustment.score
                            )
                            adjustment.score
                        },
                        scoreTrace = { candidate, finalScore ->
                            scoreTraceByExerciseId[candidate.exercise.id]
                                ?: ProgramCandidateScoreTrace(
                                    exerciseName = candidate.exercise.name,
                                    stableKey = candidate.exercise.stableKey,
                                    baseScore = finalScore,
                                    contextRerankScore = 0.0,
                                    selectedMainBoostApplied = false,
                                    captainChairPenaltyApplied = false,
                                    finalScore = finalScore
                                )
                        },
                        selectionPoolSize = varietyPolicy.selectionPoolSize(
                            request = normalized,
                            exerciseCount = prescriptionPolicy.exerciseCount(normalized.dailyAvailableMinutes)
                        ),
                        selectedCount = 0
                    )
                    val scoreContext = ProgramCandidateScoreContext(
                        request = normalized,
                        week = week,
                        periodizedWeek = periodizedWeek,
                        plannedSlot = day,
                        templateSlot = templateSlot,
                        selectedInSession = selected,
                        generatedItems = generated
                    )
                    val picked = beamSelectionPolicy.choose(
                        scored = query.scored,
                        context = scoreContext,
                        classification = inventory.reservoir::classification,
                        desiredExerciseCount = prescriptionPolicy.exerciseCount(normalized.dailyAvailableMinutes)
                    )
                    if (picked == null) {
                        candidateTraces += query.trace
                        warnings += query.trace.warnings
                        if (templateSlot.required) {
                            warnings += "TEMPLATE_REQUIRED_SLOT_UNFILLED: ${templateSelection.templateId}/${templateSlot.targetSlot}"
                        }
                        return@forEachIndexed
                    }
                    var prescription = prescriptionPolicy.prescribe(
                        candidate = picked,
                        role = role,
                        week = week,
                        gate = fatigueGate,
                        useCase = ProgramFatigueUseCase.PROGRAM_PLANNING
                    )
                    var itemDurationSeconds = prescriptionPolicy.estimateItemDurationSeconds(picked, prescription)
                    if (estimatedSessionSeconds + itemDurationSeconds > sessionBudgetSeconds) {
                        timeBudgetTrimmed = true
                        if (templateSlot.required) {
                            prescription = prescriptionPolicy.fitRequiredPrescription(
                                candidate = picked,
                                prescription = prescription,
                                remainingSeconds = sessionBudgetSeconds - estimatedSessionSeconds
                            )
                            itemDurationSeconds = prescriptionPolicy.estimateItemDurationSeconds(picked, prescription)
                            if (estimatedSessionSeconds + itemDurationSeconds > sessionBudgetSeconds) {
                                warnings += "TEMPLATE_REQUIRED_SLOT_OVERRUN: ${templateSelection.templateId}/${templateSlot.targetSlot}"
                            }
                        } else {
                            val fitted = prescriptionPolicy.fitOptionalPrescription(
                                candidate = picked,
                                prescription = prescription,
                                remainingSeconds = sessionBudgetSeconds - estimatedSessionSeconds
                            )
                            if (fitted == null) {
                                timeBudgetTrimmed = true
                                val trimmedTrace = query.trace.copy(
                                    warnings = query.trace.warnings + "PROGRAM_SLOT_TIME_BUDGET_TRIMMED"
                                )
                                candidateTraces += trimmedTrace
                                warnings += trimmedTrace.warnings
                                return@forEachIndexed
                            }
                            prescription = fitted
                            itemDurationSeconds = prescriptionPolicy.estimateItemDurationSeconds(picked, prescription)
                            timeBudgetTrimmed = true
                        }
                    }
                    candidateTraces += query.trace.markSelected(picked)
                    warnings += query.trace.warnings
                    selected += picked
                    varietyPolicy.recordSelection(selectionHistory, picked, week.weekIndex, absoluteDay, coveragePolicy)
                    estimatedSessionSeconds += itemDurationSeconds
                    val requestedSlot = templateSlot.targetSlot ?: picked.resolvedSlotForRole(role)
                    val weight = weightSuggestionPolicy.suggest(
                        exercise = picked.exercise,
                        targetReps = prescription.reps,
                        intensityMultiplier = week.intensityMultiplier * fatigueGate.planningLoadFactor(),
                        today = today
                    )
                    generated += ProgramSkeletonItem(
                        localId = "${week.weekIndex}-${day.dayOfWeek}-${itemIndex + 1}-${picked.exercise.id}",
                        weekNumber = week.weekIndex,
                        dayOfWeek = day.dayOfWeek,
                        orderIndex = itemIndex + 1,
                        exerciseId = picked.exercise.id,
                        exerciseName = picked.exercise.name,
                        category = picked.exercise.category,
                        restSeconds = picked.exercise.defaultRestSeconds,
                        prescription = listOf(
                            "SLOT:${day.slot.name}",
                            "DAY:${day.intensity.name}",
                            week.weekType,
                            prescription.label,
                            "RPE ${prescription.rpe}"
                        ).joinToString(" · "),
                        setCount = prescription.setCount,
                        reps = prescription.reps,
                        weightKg = weight.weightKg,
                        seconds = prescription.seconds,
                        selectionReason = reasonFormatter.format(picked, role, fatigueGate),
                        weightSource = weight.source,
                        trainingSlot = day.slot.name,
                        dayIntensity = day.intensity.name,
                        stableKey = picked.exercise.stableKey,
                        selectionRole = role.name,
                        movementFamily = picked.metadata?.movementFamily.orEmpty(),
                        movementSubtype = picked.metadata?.movementSubtype.orEmpty(),
                        metadataProgramSlot = picked.metadata?.programSlot.orEmpty(),
                        redundancyGroup = picked.metadata?.redundancyGroup.orEmpty(),
                        strengthProgressionGroup = picked.metadata?.strengthProgressionGroup.orEmpty(),
                        primaryStressProfile = picked.metadata?.primaryStressProfile.orEmpty(),
                        stressMagnitudeHint = picked.metadata?.stressMagnitudeHint.orEmpty(),
                        neuromuscularStressLevel = picked.metadata?.neuromuscularStressLevel.orEmpty(),
                        systemicMuscularStressLevel = picked.metadata?.systemicMuscularStressLevel.orEmpty(),
                        localMuscularStressLevel = picked.metadata?.localMuscularStressLevel.orEmpty(),
                        jointTendonImpactStressLevel = picked.metadata?.jointTendonImpactStressLevel.orEmpty(),
                        movementFocusDemandLevel = picked.metadata?.movementFocusDemandLevel.orEmpty(),
                        recoveryDurationClass = picked.metadata?.recoveryDurationClass.orEmpty(),
                        badmintonTransferLevel = picked.metadata?.badmintonTransferLevel.orEmpty(),
                        estimatedDurationSeconds = itemDurationSeconds,
                        directSportSession = picked.isDirectSportSession,
                        rehabLikeActivation = picked.isRehabLikeActivation,
                        scapularStabilityExposure = picked.isScapularStabilityExposure,
                        primarySlotCapabilities = picked.slotCapabilities.primary.map(ProgramSlotId::name).sorted(),
                        secondarySlotCapabilities = picked.slotCapabilities.secondary.map(ProgramSlotId::name).sorted(),
                        weakSlotCapabilities = picked.slotCapabilities.weakMatches.map(ProgramSlotId::name).sorted(),
                        slotCapabilitySource = picked.slotCapabilities.source.name,
                        slotCapabilityConfidence = picked.slotCapabilities.confidence.name,
                        slotCapabilityWarnings = picked.slotCapabilities.warnings,
                        requestedTemplateSlot = requestedSlot?.name.orEmpty(),
                        requiredTemplateAnchor = templateSlot.required
                    )
                }
            }
        }
        if (candidates.size < 8) warnings += "조건에 맞는 운동 후보가 적어 일부 세션 구성이 짧을 수 있습니다."
        if (fatigueGate.band != ProgramFatigueBand.GREEN) {
            warnings += "계획 피로도 참고: ${fatigueGate.band.name} · 볼륨 ${fatigueGate.planningLoadFactor().toPercent()} · RPE 상한 ${fatigueGate.rpeCap}. RED 전에는 메인 운동을 줄이지 않고 계획합니다."
        }
        if (timeBudgetTrimmed) {
            warnings += "세션 시간 예산에 맞춰 일부 보조 항목을 줄였습니다."
        }
        warnings += sessionDensityPolicy.warnings(generated, normalized)
        warnings += varietyPolicy.distributionWarnings(generated, normalized)
        warnings += dayIntensityPolicy.warnings(generated, normalized)
        warnings += compositionPolicy.warnings(generated, normalized)
        warnings += foundationAnchorPolicy.warnings(generated, normalized)
        warnings += corePatternPolicy.warnings(generated, normalized)
        val result = GeneratedProgramSkeleton(
            suggestedName = normalized.name.ifBlank { defaultName(normalized) },
            durationDays = normalized.durationWeeks * 7,
            request = normalized,
            periodizationType = periodization,
            weekPlans = weekPlans,
            items = generated,
            candidateTraces = candidateTraces,
            warnings = warnings.distinct(),
            templateId = templateSelection.templateId,
            representativeTemplate = templateSelection.representative
        )
        val templateIssues = warnings
            .filter { it.startsWith("TEMPLATE_REQUIRED_SLOT_") }
            .map { warning ->
                ProgramValidationIssue(
                    code = warning.substringBefore(':'),
                    severity = ProgramValidationSeverity.WARNING,
                    message = warning.substringAfter(':').trim()
                )
            }
        val issues = ProgramBuilderValidator.validate(result, fatigueGate) + templateIssues
        val renderedIssues = issues.map(ProgramValidationIssue::render)
        val visibleIssues = issues
            .filter { it.severity != ProgramValidationSeverity.SOFT_PENALTY }
            .map(ProgramValidationIssue::render)
        val validated = result.copy(
            validationIssues = renderedIssues,
            validationDetails = issues,
            warnings = (warnings + visibleIssues).distinct()
        )
        return optimizationPolicy.optimize(validated, inventory.reservoir)
            .withPreferredExerciseHardIncludes(
                exercises = exercises,
                runtimeMetadataCatalog = runtimeMetadataCatalog
            )
            .withExerciseConstraintSummary()
    }

    private fun defaultName(request: ProgramSkeletonRequest): String =
        "${request.durationWeeks}주 ${request.badmintonSpecificityRatio}:${100 - request.badmintonSpecificityRatio} 프로그램"

    private fun captainChairCanBeConsidered(
        request: ProgramSkeletonRequest,
        availableSelectedMainStableKeys: Set<String>,
        generatedItems: List<ProgramSkeletonItem>,
        weekNumber: Int
    ): Boolean {
        if (request.goal != ProgramGoal.BADMINTON_SUPPORT || request.dailyAvailableMinutes < 40) return true
        if (availableSelectedMainStableKeys.isEmpty()) return true
        if (generatedItems.count { selectedExerciseScorePolicy.isCaptainChairStableKey(it.stableKey) } >= 1) return false
        val weekSelectedMain = generatedItems.count { item ->
            item.weekNumber == weekNumber && selectedExerciseScorePolicy.isSelectedMainStableKey(item.stableKey)
        }
        val programSelectedMainTypes = generatedItems
            .map(ProgramSkeletonItem::stableKey)
            .filter(selectedExerciseScorePolicy::isSelectedMainStableKey)
            .toSet()
        return weekSelectedMain >= 2 &&
            programSelectedMainTypes.size >= minOf(3, availableSelectedMainStableKeys.size)
    }

    private fun GeneratedProgramSkeleton.withPreferredExerciseHardIncludes(
        exercises: List<Exercise>,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog
    ): GeneratedProgramSkeleton {
        val preferred = request.preferredExerciseStableKeys.filter(String::isNotBlank).toSet()
        if (preferred.isEmpty()) return this

        val excluded = request.excludedExerciseStableKeys.filter(String::isNotBlank).toSet()
        val exercisesByStableKey = exercises
            .filter { it.stableKey.isNotBlank() }
            .associateBy(Exercise::stableKey)
        val conflicts = preferred.intersect(excluded)
        val missing = preferred
            .filter { it !in conflicts && it !in exercisesByStableKey.keys }
            .toSet()
        val inactive = preferred
            .filter { key -> key !in conflicts && key !in missing && exercisesByStableKey[key]?.isActive != true }
            .toSet()
        var adjusted = this
        val issues = mutableListOf<ProgramValidationIssue>()
        val outcomeMessages = mutableListOf<String>()

        conflicts.sorted().forEach { key ->
            issues += ProgramValidationIssue(
                code = "PROGRAM_PREFERRED_EXCLUDED_CONFLICT",
                severity = ProgramValidationSeverity.HARD,
                message = "Preferred exercise '$key' is also excluded."
            )
        }
        missing.sorted().forEach { key ->
            issues += ProgramValidationIssue(
                code = "PROGRAM_PREFERRED_EXERCISE_MISSING",
                severity = ProgramValidationSeverity.HARD,
                message = "Preferred exercise '$key' was not found in the exercise catalog."
            )
        }
        inactive.sorted().forEach { key ->
            issues += ProgramValidationIssue(
                code = "PROGRAM_PREFERRED_EXERCISE_INACTIVE",
                severity = ProgramValidationSeverity.HARD,
                message = "Preferred exercise '$key' is inactive."
            )
        }

        val placeable = preferred - conflicts - missing - inactive
        placeable.sorted().forEach { key ->
            if (adjusted.items.any { it.stableKey == key }) {
                outcomeMessages += "PROGRAM_PREFERRED_EXERCISE_INCLUDED: $key"
                return@forEach
            }
            val exercise = exercisesByStableKey[key] ?: return@forEach
            val candidate = preferredCandidate(exercise, runtimeMetadataCatalog)
            val target = adjusted.preferredReplacementTarget(preferred)
            adjusted = if (target != null) {
                outcomeMessages += "PROGRAM_PREFERRED_EXERCISE_FORCED: $key replaced ${target.stableKey.ifBlank { target.exerciseName }}"
                adjusted.copy(
                    items = adjusted.items.map { item ->
                        if (item.localId == target.localId) {
                            item.withPreferredCandidate(candidate, preferredRole(candidate))
                        } else {
                            item
                        }
                    }
                )
            } else {
                outcomeMessages += "PROGRAM_PREFERRED_EXERCISE_FORCED: $key appended"
                adjusted.appendPreferredCandidate(candidate)
            }
        }

        val mergedIssues = (adjusted.validationDetails + issues)
            .distinctBy { "${it.code}:${it.message}" }
        val renderedIssues = mergedIssues.map(ProgramValidationIssue::render)
        return adjusted.copy(
            validationDetails = mergedIssues,
            validationIssues = renderedIssues,
            warnings = (adjusted.warnings + renderedIssues + outcomeMessages).distinct(),
            optimizationSummary = adjusted.optimizationSummary.copy(
                messages = (adjusted.optimizationSummary.messages + outcomeMessages).distinct()
            )
        )
    }

    private fun preferredCandidate(
        exercise: Exercise,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog
    ): ProgramCandidate {
        val catalogMetadata = runtimeMetadataCatalog.resolve(exercise)
        val metadata = catalogMetadata ?: RuntimeExerciseMetadataDefaults.forExercise(exercise)
        return ProgramCandidate(
            exercise = exercise,
            metadata = metadata,
            canonical = catalogMetadata != null,
            slotCapabilities = slotCapabilityResolver.resolve(exercise, metadata)
        )
    }

    private fun GeneratedProgramSkeleton.preferredReplacementTarget(preferred: Set<String>): ProgramSkeletonItem? =
        items.firstOrNull { item -> item.stableKey !in preferred && item.selectionRole in WEAK_PREFERRED_REPLACEMENT_ROLES }
            ?: items.firstOrNull { item -> item.stableKey !in preferred && !item.requiredTemplateAnchor }
            ?: items.firstOrNull { item -> item.stableKey !in preferred }

    private fun ProgramSkeletonItem.withPreferredCandidate(
        candidate: ProgramCandidate,
        role: ProgramExerciseRole
    ): ProgramSkeletonItem {
        val requestedSlot = candidate.resolvedSlotForRole(role)
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

    private fun GeneratedProgramSkeleton.appendPreferredCandidate(candidate: ProgramCandidate): GeneratedProgramSkeleton {
        val anchor = items.firstOrNull()
        val weekNumber = anchor?.weekNumber ?: 1
        val dayOfWeek = anchor?.dayOfWeek ?: 1
        val orderIndex = items
            .filter { it.weekNumber == weekNumber && it.dayOfWeek == dayOfWeek }
            .maxOfOrNull(ProgramSkeletonItem::orderIndex)
            ?.plus(1)
            ?: 1
        val week = weekPlans.firstOrNull { it.weekIndex == weekNumber } ?: ProgramWeekPlan(
            weekIndex = weekNumber,
            weekType = ProgramWeekType.BUILD.name,
            volumeMultiplier = 1.0,
            intensityMultiplier = 1.0,
            heavyExposureLimit = 2,
            lowerBodyFatigueLimit = 8.0,
            axialLoadLimit = 2,
            plyometricLimit = 1,
            deloadFlag = false
        )
        val role = preferredRole(candidate)
        val prescription = prescriptionPolicy.prescribe(
            candidate = candidate,
            role = role,
            week = week,
            gate = unrestrictedFatigueGate(),
            useCase = ProgramFatigueUseCase.PROGRAM_PLANNING
        )
        val duration = prescriptionPolicy.estimateItemDurationSeconds(candidate, prescription)
        return copy(
            items = items + ProgramSkeletonItem(
                localId = "$weekNumber-$dayOfWeek-$orderIndex-${candidate.exercise.id}-preferred",
                weekNumber = weekNumber,
                dayOfWeek = dayOfWeek,
                orderIndex = orderIndex,
                exerciseId = candidate.exercise.id,
                exerciseName = candidate.exercise.name,
                category = candidate.exercise.category,
                restSeconds = candidate.exercise.defaultRestSeconds,
                prescription = listOf(
                    "PREFERRED_INCLUDE",
                    prescription.label,
                    "RPE ${prescription.rpe}"
                ).joinToString(" · "),
                setCount = prescription.setCount,
                reps = prescription.reps,
                weightKg = 0.0,
                seconds = prescription.seconds,
                selectionReason = "Preferred exercise included",
                weightSource = "",
                trainingSlot = anchor?.trainingSlot ?: ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name,
                dayIntensity = anchor?.dayIntensity ?: ProgramDayIntensity.MODERATE.name,
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
                estimatedDurationSeconds = duration,
                directSportSession = candidate.isDirectSportSession,
                rehabLikeActivation = candidate.isRehabLikeActivation,
                scapularStabilityExposure = candidate.isScapularStabilityExposure,
                primarySlotCapabilities = candidate.slotCapabilities.primary.map(ProgramSlotId::name).sorted(),
                secondarySlotCapabilities = candidate.slotCapabilities.secondary.map(ProgramSlotId::name).sorted(),
                weakSlotCapabilities = candidate.slotCapabilities.weakMatches.map(ProgramSlotId::name).sorted(),
                slotCapabilitySource = candidate.slotCapabilities.source.name,
                slotCapabilityConfidence = candidate.slotCapabilities.confidence.name,
                slotCapabilityWarnings = candidate.slotCapabilities.warnings,
                requestedTemplateSlot = candidate.resolvedSlotForRole(role)?.name.orEmpty()
            )
        )
    }

    private fun preferredRole(candidate: ProgramCandidate): ProgramExerciseRole = when {
        candidate.isAnchor || candidate.isLoadedStrength -> ProgramExerciseRole.ANCHOR
        candidate.isCore -> ProgramExerciseRole.CORE
        candidate.badmintonFit >= 0.9 -> ProgramExerciseRole.TRANSFER
        else -> ProgramExerciseRole.SUPPORT
    }

    private fun unrestrictedFatigueGate(): ProgramFatigueGate =
        ProgramFatigueGate(
            band = ProgramFatigueBand.GREEN,
            volumeFactor = 1.0,
            rpeCap = 10,
            allowsHeavyLower = true,
            allowsHighImpact = true,
            allowsHighIntensityCod = true,
            lowerBodyRestricted = false
        )

    private companion object {
        val WEAK_PREFERRED_REPLACEMENT_ROLES = setOf(
            ProgramExerciseRole.CORE.name,
            ProgramExerciseRole.PREHAB.name,
            ProgramExerciseRole.ACCESSORY.name,
            ProgramExerciseRole.TRANSFER.name
        )
    }
}

private fun Double.toPercent(): String = "${(this * 100).roundToInt()}%"

private fun ProgramCandidateTrace.markSelected(candidate: ProgramCandidate): ProgramCandidateTrace =
    copy(
        selected = 1,
        scoreAdjustments = scoreAdjustments.map { trace ->
            if (trace.stableKey == candidate.exercise.stableKey) trace.copy(selected = true) else trace
        }
    )

private fun ProgramFatigueGate.planningLoadFactor(): Double = when (band) {
    ProgramFatigueBand.GREEN -> 1.0
    ProgramFatigueBand.YELLOW -> 1.0
    ProgramFatigueBand.ORANGE -> 1.0
    ProgramFatigueBand.RED -> 0.70
}

internal fun GeneratedProgramSkeleton.withExerciseConstraintSummary(): GeneratedProgramSkeleton {
    val excludedCount = request.excludedExerciseStableKeys.count(String::isNotBlank)
    val preferred = request.preferredExerciseStableKeys.filter(String::isNotBlank).toSet()
    val preferredSelected = items.map(ProgramSkeletonItem::stableKey).toSet().intersect(preferred).size
    val messages = buildList {
        if (excludedCount > 0) add("PROGRAM_EXCLUDED_EXERCISES_APPLIED: $excludedCount")
        if (preferred.isNotEmpty()) {
            if (preferredSelected > 0) {
                add("PROGRAM_PREFERRED_EXERCISES_INCLUDED: $preferredSelected/${preferred.size}")
            }
        }
    }
    if (messages.isEmpty()) return this
    return copy(
        warnings = (warnings + messages).distinct(),
        optimizationSummary = optimizationSummary.copy(
            messages = (optimizationSummary.messages + messages).distinct()
        )
    )
}
