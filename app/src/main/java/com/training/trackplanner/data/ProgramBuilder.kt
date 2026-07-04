package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import java.time.LocalDate
import kotlin.math.max
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
        val weekPlans = templateCatalog.weekPlans(normalized.durationWeeks, fatigueGate)
        val templateSelection = templateCatalog.select(normalized)
        val exposureTargets = templateCatalog.exposureTargets(templateSelection, normalized)
            .associateBy(NumericExposureTarget::slot)
        val schedule = templateSelection.sessions.map { planned ->
            fatigueSlotPolicy.adapt(planned, fatigueGate)
        }
        val excludedTerms = normalized.excludedExerciseText
            .split(',', '\n', ';')
            .map(String::trim)
            .filter(String::isNotBlank)
        val inventory = candidateInventory.collect(
            exercises = exercises,
            runtimeMetadataCatalog = runtimeMetadataCatalog,
            availableEquipment = normalized.availableEquipment,
            excludedTerms = excludedTerms
        )
        val candidates = inventory.candidates

        val historyWeights = ProgramHistoryWeightIndex(history, exercises)
        val generated = mutableListOf<ProgramSkeletonItem>()
        val selectionHistory = ProgramSelectionHistory()
        val warnings = mutableListOf<String>()
        val candidateTraces = mutableListOf<ProgramCandidateTrace>()
        var timeBudgetTrimmed = false
        weekPlans.forEach { week ->
            schedule.forEachIndexed { dayIndex, day ->
                val exerciseSlots = templateCatalog.exerciseSlots(
                    day,
                    prescriptionPolicy.exerciseCount(normalized.dailyAvailableMinutes)
                )
                val selected = mutableListOf<ProgramCandidate>()
                val sessionBudgetSeconds = normalized.dailyAvailableMinutes * 60
                var estimatedSessionSeconds = prescriptionPolicy.warmupReserveSeconds(normalized.dailyAvailableMinutes)
                exerciseSlots.forEachIndexed { itemIndex, templateSlot ->
                    val role = templateSlot.role
                    val absoluteDay = (week.weekIndex - 1) * 7 + day.dayOfWeek
                    val query = slotCandidateQuery.query(
                        inventory = inventory,
                        selected = selected,
                        plannedSlot = day,
                        templateSlot = templateSlot,
                        week = week,
                        weekNumber = week.weekIndex,
                        absoluteDay = absoluteDay,
                        repeatAllowed = { candidate ->
                            selectionHistory.allowsRepeat(
                                candidate = candidate,
                                absoluteDay = absoluteDay,
                                minimumGapDays = templateSlot.minimumRepeatGapDays
                            )
                        },
                        fatigueAllowed = { candidate -> fatigueSlotPolicy.allows(candidate, fatigueGate) },
                        sessionAllowed = { candidate -> sessionAllows(selected, candidate, day.slot, week, fatigueGate) },
                        score = { candidate ->
                            score(
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
                        },
                        selectionPoolSize = selectionPoolSize(normalized),
                        selectedCount = 0
                    )
                    val picked = chooseControlledCandidate(
                        scored = query.scored,
                        weekIndex = week.weekIndex,
                        dayIndex = dayIndex,
                        itemIndex = itemIndex,
                        preference = normalized.varietyPreference
                    )
                    if (picked == null) {
                        candidateTraces += query.trace
                        warnings += query.trace.warnings
                        if (templateSlot.required) {
                            warnings += "TEMPLATE_REQUIRED_SLOT_UNFILLED: ${templateSelection.templateId}/${templateSlot.targetSlot}"
                        }
                        return@forEachIndexed
                    }
                    var prescription = prescriptionPolicy.prescribe(picked, role, week, fatigueGate)
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
                    candidateTraces += query.trace.copy(selected = 1)
                    warnings += query.trace.warnings
                    selected += picked
                    selectionHistory.record(picked, week.weekIndex, absoluteDay, coveragePolicy)
                    estimatedSessionSeconds += itemDurationSeconds
                    val requestedSlot = templateSlot.targetSlot ?: picked.resolvedSlotForRole(role)
                    val weight = historyWeights.suggest(
                        exercise = picked.exercise,
                        targetReps = prescription.reps,
                        intensityMultiplier = week.intensityMultiplier,
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
                        selectionReason = reasonTokens(picked, role, fatigueGate),
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
            warnings += "현재 피로도 ${fatigueGate.band.name} · 볼륨 ${fatigueGate.volumeFactor.toPercent()} · RPE 상한 ${fatigueGate.rpeCap}"
        }
        if (timeBudgetTrimmed) {
            warnings += "세션 시간 예산에 맞춰 일부 보조 항목을 줄였습니다."
        }
        val periodization = choosePeriodization(normalized)
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
        return result.copy(
            validationIssues = renderedIssues,
            validationDetails = issues,
            warnings = (warnings + visibleIssues).distinct()
        )
    }

    private fun score(
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

    private fun chooseControlledCandidate(
        scored: List<Pair<ProgramCandidate, Double>>,
        weekIndex: Int,
        dayIndex: Int,
        itemIndex: Int,
        preference: ProgramVarietyPreference
    ): ProgramCandidate? {
        scored.firstOrNull() ?: return null
        val poolSize = when (preference) {
            ProgramVarietyPreference.LOW -> 4
            ProgramVarietyPreference.NORMAL -> 6
            ProgramVarietyPreference.HIGH -> 8
        }.coerceAtMost(scored.size)
        return scored[(weekIndex + dayIndex + itemIndex) % poolSize].first
    }

    private fun selectionPoolSize(request: ProgramSkeletonRequest): Int =
        max(8, prescriptionPolicy.exerciseCount(request.dailyAvailableMinutes) * 3)

    private fun sessionAllows(
        selected: List<ProgramCandidate>,
        next: ProgramCandidate,
        slot: ProgramTrainingSlot,
        week: ProgramWeekPlan,
        gate: ProgramFatigueGate
    ): Boolean {
        if (next.isRehabLikeActivation) {
            val recoveryContext = slot in EXPANDED_RECOVERY_SLOTS || week.deloadFlag ||
                gate.band == ProgramFatigueBand.RED
            val cap = if (recoveryContext) 3 else 1
            if (selected.count(ProgramCandidate::isRehabLikeActivation) >= cap) return false
        }
        if (selected.count(ProgramCandidate::isIsolation) >= 2 && next.isIsolation) return false
        if (selected.any(ProgramCandidate::isHeavyLower) && next.isHeavyLower) return false
        val hasHeavyLower = selected.any(ProgramCandidate::isHeavyLower) || next.isHeavyLower
        val hasImpact = selected.any(ProgramCandidate::isHighImpact) || next.isHighImpact
        val hasCod = selected.any(ProgramCandidate::isHighIntensityCod) || next.isHighIntensityCod
        return !(hasHeavyLower && hasImpact && hasCod)
    }

    private fun reasonTokens(
        candidate: ProgramCandidate,
        role: ProgramExerciseRole,
        gate: ProgramFatigueGate
    ): String = buildList {
        add("역할:${role.name}")
        candidate.metadata?.movementFamily?.takeIf { it.isNotBlank() && it != "NOT_APPLICABLE" }
            ?.let { add("동작:$it") }
        if (candidate.badmintonFit >= 0.8) add("전이:${candidate.metadata?.badmintonTransferLevel}")
        if (candidate.metadata?.appCueProfile == "RANDOM_BEEP_CUE") add("앱 cue 가능")
        if (gate.band != ProgramFatigueBand.GREEN) add("피로:${gate.band.name}")
    }.joinToString(" / ")

    private fun choosePeriodization(request: ProgramSkeletonRequest): ProgramPeriodizationType =
        request.periodizationType.takeIf { it != ProgramPeriodizationType.AUTO }
            ?: if (request.badmintonTransferRatio >= 0.5) {
                ProgramPeriodizationType.BADMINTON_WAVE
            } else {
                ProgramPeriodizationType.STEP_DELOAD
            }

    private fun defaultName(request: ProgramSkeletonRequest): String =
        "${request.durationWeeks}주 ${request.badmintonSpecificityRatio}:${100 - request.badmintonSpecificityRatio} 프로그램"

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

private fun Double.toPercent(): String = "${(this * 100).roundToInt()}%"
