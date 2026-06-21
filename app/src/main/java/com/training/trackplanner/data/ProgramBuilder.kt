package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class ProgramBuilder internal constructor(
    private val templateCatalog: ProgramTemplateCatalog = ProgramTemplateCatalog.DEFAULT,
    private val slotCapabilityResolver: SlotCapabilityResolver = SlotCapabilityResolver.DEFAULT,
    private val fatigueSlotPolicy: FatigueSlotPolicy = FatigueSlotPolicy.DEFAULT,
    private val coveragePolicy: CoverageAccountingPolicy = CoverageAccountingPolicy.DEFAULT
) {
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
        val candidates = exercises.asSequence()
            .filter(Exercise::isActive)
            .map { exercise ->
                val metadata = runtimeMetadataCatalog.resolve(exercise)
                ProgramCandidate(
                    exercise = exercise,
                    metadata = metadata,
                    canonical = metadata != null,
                    slotCapabilities = slotCapabilityResolver.resolve(exercise, metadata)
                )
            }
            .filter(ProgramCandidate::isProgramSelectable)
            .filter { it.matchesEquipment(normalized.availableEquipment) }
            .filter { candidate -> excludedTerms.none { candidate.exercise.name.contains(it, ignoreCase = true) } }
            .toList()

        val historyWeights = ProgramHistoryWeightIndex(history, exercises)
        val generated = mutableListOf<ProgramSkeletonItem>()
        val selectionHistory = ProgramSelectionHistory()
        val warnings = mutableListOf<String>()
        var timeBudgetTrimmed = false
        weekPlans.forEach { week ->
            schedule.forEachIndexed { dayIndex, day ->
                val exerciseSlots = templateCatalog.exerciseSlots(
                    day,
                    exerciseCount(normalized.dailyAvailableMinutes)
                )
                val selected = mutableListOf<ProgramCandidate>()
                val sessionBudgetSeconds = normalized.dailyAvailableMinutes * 60
                var estimatedSessionSeconds = warmupReserveSeconds(normalized.dailyAvailableMinutes)
                var timeBudgetReached = false
                exerciseSlots.forEachIndexed { itemIndex, templateSlot ->
                    if (timeBudgetReached) return@forEachIndexed
                    val role = templateSlot.role
                    val absoluteDay = (week.weekIndex - 1) * 7 + day.dayOfWeek
                    val scored = candidates.asSequence()
                        .filterNot { candidate -> selected.any { it.exercise.id == candidate.exercise.id } }
                        .filter { candidate ->
                            selectionHistory.allowsRepeat(
                                candidate = candidate,
                                absoluteDay = absoluteDay,
                                minimumGapDays = templateSlot.minimumRepeatGapDays
                            )
                        }
                        .filter { candidate -> fatigueSlotPolicy.allows(candidate, fatigueGate) }
                        .filter { candidate ->
                            candidate.allowedForTemplateSlot(
                                plannedSlot = day.slot,
                                templateSlot = templateSlot,
                                coveragePolicy = coveragePolicy
                            )
                        }
                        .filter { candidate -> sessionAllows(selected, candidate, day.slot, week, fatigueGate) }
                        .map { candidate ->
                            candidate to score(
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
                        }
                        .sortedByDescending { it.second }
                        .take(3)
                        .toList()
                    val picked = chooseControlledCandidate(
                        scored = scored,
                        weekIndex = week.weekIndex,
                        dayIndex = dayIndex,
                        itemIndex = itemIndex,
                        preference = normalized.varietyPreference
                    )
                    if (picked == null) {
                        if (templateSlot.required) {
                            warnings += "TEMPLATE_REQUIRED_SLOT_UNFILLED: ${templateSelection.templateId}/${templateSlot.targetSlot}"
                        }
                        return@forEachIndexed
                    }
                    var prescription = prescribe(picked, role, week, fatigueGate)
                    var itemDurationSeconds = estimateItemDurationSeconds(picked, prescription)
                    if (estimatedSessionSeconds + itemDurationSeconds > sessionBudgetSeconds) {
                        timeBudgetTrimmed = true
                        if (templateSlot.required) {
                            prescription = fitRequiredPrescription(
                                candidate = picked,
                                prescription = prescription,
                                remainingSeconds = sessionBudgetSeconds - estimatedSessionSeconds
                            )
                            itemDurationSeconds = estimateItemDurationSeconds(picked, prescription)
                            if (estimatedSessionSeconds + itemDurationSeconds > sessionBudgetSeconds) {
                                warnings += "TEMPLATE_REQUIRED_SLOT_OVERRUN: ${templateSelection.templateId}/${templateSlot.targetSlot}"
                            }
                        } else {
                            timeBudgetReached = true
                            return@forEachIndexed
                        }
                    }
                    selected += picked
                    selectionHistory.record(picked, week.weekIndex, absoluteDay, coveragePolicy)
                    estimatedSessionSeconds += itemDurationSeconds
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
                        requestedTemplateSlot = templateSlot.targetSlot?.name.orEmpty(),
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
            warnings = warnings,
            templateId = templateSelection.templateId,
            representativeTemplate = templateSelection.representative
        )
        val templateIssues = warnings
            .filter { it.startsWith("TEMPLATE_REQUIRED_SLOT_") }
            .map { warning ->
                ProgramValidationIssue(
                    code = warning.substringBefore(':'),
                    severity = ProgramValidationSeverity.HARD,
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
            warnings = warnings + visibleIssues
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
        val recoveryContext = slot in RECOVERY_SLOTS || week.deloadFlag ||
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
        val first = scored.firstOrNull() ?: return null
        val second = scored.getOrNull(1)
        if (second == null || first.second - second.second > 0.35) return first.first
        val poolSize = when (preference) {
            ProgramVarietyPreference.LOW -> 2
            ProgramVarietyPreference.NORMAL -> 3
            ProgramVarietyPreference.HIGH -> 3
        }.coerceAtMost(scored.size)
        return scored[(weekIndex + dayIndex + itemIndex) % poolSize].first
    }

    private fun sessionAllows(
        selected: List<ProgramCandidate>,
        next: ProgramCandidate,
        slot: ProgramTrainingSlot,
        week: ProgramWeekPlan,
        gate: ProgramFatigueGate
    ): Boolean {
        if (next.isRehabLikeActivation) {
            val recoveryContext = slot in RECOVERY_SLOTS || week.deloadFlag || gate.band == ProgramFatigueBand.RED
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

    private fun prescribe(
        candidate: ProgramCandidate,
        role: ProgramExerciseRole,
        week: ProgramWeekPlan,
        gate: ProgramFatigueGate
    ): ProgramPrescription {
        val timed = candidate.isTimed
        val baseSets = when (role) {
            ProgramExerciseRole.ANCHOR -> 4
            ProgramExerciseRole.TRANSFER -> if (candidate.isHighImpact) 3 else 4
            ProgramExerciseRole.PREHAB -> 2
            else -> 3
        }
        val sets = max(1, (baseSets * week.volumeMultiplier * gate.volumeFactor).roundToInt())
        val reps = when {
            timed -> 0
            role == ProgramExerciseRole.ANCHOR -> 5
            role == ProgramExerciseRole.TRANSFER && candidate.isHighImpact -> 5
            role == ProgramExerciseRole.PREHAB -> 15
            role == ProgramExerciseRole.CORE -> 10
            else -> 10
        }
        val seconds = if (timed) {
            when {
                candidate.isSportLike -> 15 * 60
                role == ProgramExerciseRole.TRANSFER -> 20
                else -> 30
            }
        } else 0
        val plannedRpe = week.targetRpeMax.roundToInt().coerceAtMost(gate.rpeCap)
        val label = if (timed) "${sets}세트 · ${seconds}초" else "${sets}×${reps}"
        return ProgramPrescription(sets, reps, seconds, plannedRpe, label)
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

    private fun exerciseCount(minutes: Int): Int = when (minutes) {
        in 15..25 -> 3
        in 26..40 -> 4
        in 41..60 -> 5
        in 61..80 -> 6
        else -> 7
    }

    private fun warmupReserveSeconds(minutes: Int): Int = when {
        minutes <= 30 -> 5 * 60
        minutes <= 60 -> 8 * 60
        else -> 10 * 60
    }

    private fun estimateItemDurationSeconds(
        candidate: ProgramCandidate,
        prescription: ProgramPrescription
    ): Int {
        val workPerSet = when {
            prescription.seconds > 0 -> prescription.seconds
            prescription.reps > 0 -> prescription.reps * 4
            else -> 30
        }
        val work = prescription.setCount * workPerSet
        val rest = (prescription.setCount - 1).coerceAtLeast(0) * candidate.exercise.defaultRestSeconds
        return 45 + work + rest
    }

    private fun fitRequiredPrescription(
        candidate: ProgramCandidate,
        prescription: ProgramPrescription,
        remainingSeconds: Int
    ): ProgramPrescription {
        for (sets in prescription.setCount downTo 1) {
            val reduced = prescription.copy(setCount = sets)
            if (estimateItemDurationSeconds(candidate, reduced) <= remainingSeconds) return reduced
        }
        return prescription.copy(setCount = 1)
    }

    private companion object {
        val RECOVERY_SLOTS = setOf(
            ProgramTrainingSlot.RECOVERY_PREHAB,
            ProgramTrainingSlot.RECOVERY_WEAKPOINT,
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

private class ProgramSelectionHistory {
    private val stableKeyDays = mutableMapOf<String, MutableList<Int>>()
    private val redundancyDays = mutableMapOf<String, MutableList<Int>>()
    private val familyWeeklyExposure = mutableMapOf<Pair<Int, String>, Int>()
    private val slotExposure = mutableMapOf<ProgramSlotId, Double>()

    fun penalty(candidate: ProgramCandidate, weekIndex: Int, absoluteDay: Int): Double {
        val stableGap = stableKeyDays[candidate.exercise.stableKey]
            .orEmpty()
            .maxOrNull()
            ?.let { absoluteDay - it }
            ?: Int.MAX_VALUE
        val stablePenalty = when {
            candidate.isRehabLikeActivation && stableGap <= 14 -> 6.0
            candidate.isRehabLikeActivation && stableGap <= 28 -> 2.5
            candidate.isAnchor && stableGap <= 7 -> 0.45
            candidate.isAnchor && stableGap <= 14 -> 0.15
            stableGap <= 7 -> 4.5
            stableGap <= 14 -> 2.0
            else -> 0.0
        }
        val redundancyKey = candidate.metadata?.redundancyGroup.orEmpty()
        val redundancyGap = redundancyDays[redundancyKey]
            .orEmpty()
            .maxOrNull()
            ?.let { absoluteDay - it }
            ?: Int.MAX_VALUE
        val redundancyPenalty = when {
            redundancyKey.isBlank() || redundancyKey == "NOT_APPLICABLE" -> 0.0
            redundancyGap <= 3 -> 1.4
            redundancyGap <= 7 -> 0.7
            else -> 0.0
        }
        val familyKey = candidate.metadata?.movementFamily.orEmpty()
        val familyCount = familyWeeklyExposure[weekIndex to familyKey] ?: 0
        val familyPenalty = when {
            familyKey.isBlank() || familyKey == "NOT_APPLICABLE" -> 0.0
            familyCount <= 1 -> 0.0
            else -> (familyCount - 1) * 0.65
        }
        return stablePenalty + redundancyPenalty + familyPenalty
    }

    fun coverage(slot: ProgramSlotId): Double = slotExposure[slot] ?: 0.0

    fun allowsRepeat(candidate: ProgramCandidate, absoluteDay: Int, minimumGapDays: Int): Boolean {
        if (minimumGapDays <= 0) return true
        val lastDay = stableKeyDays[candidate.exercise.stableKey].orEmpty().maxOrNull() ?: return true
        return absoluteDay - lastDay >= minimumGapDays
    }

    fun record(
        candidate: ProgramCandidate,
        weekIndex: Int,
        absoluteDay: Int,
        coveragePolicy: CoverageAccountingPolicy
    ) {
        stableKeyDays.getOrPut(candidate.exercise.stableKey) { mutableListOf() } += absoluteDay
        candidate.metadata?.redundancyGroup
            ?.takeUnless { it.isBlank() || it == "NOT_APPLICABLE" }
            ?.let { redundancyDays.getOrPut(it) { mutableListOf() } += absoluteDay }
        candidate.metadata?.movementFamily
            ?.takeUnless { it.isBlank() || it == "NOT_APPLICABLE" }
            ?.let { family ->
                val key = weekIndex to family
                familyWeeklyExposure[key] = (familyWeeklyExposure[key] ?: 0) + 1
            }
        coveragePolicy.creditedSlots(candidate.slotCapabilities).forEach { (slot, credit) ->
            slotExposure[slot] = coverage(slot) + credit.value
        }
    }
}

internal data class ProgramCandidate(
    val exercise: Exercise,
    val metadata: RuntimeExerciseMetadata?,
    val canonical: Boolean,
    val slotCapabilities: SlotCapabilityProfile = SlotCapabilityResolver.DEFAULT.resolve(exercise, metadata)
) {
    private val identityTokens: Set<String> = buildSet {
        metadata?.let { value ->
            add(value.movementFamily)
            add(value.movementSubtype)
            add(value.programSlot)
            add(value.redundancyGroup)
            add(value.strengthProgressionGroup)
        }
        if (!canonical) {
            add(exercise.movementPattern)
            add(exercise.movementCategory)
            add(exercise.trainingRole)
            add(exercise.strengthProgressionGroup)
        }
    }.flatMap(::splitTokens).toSet()

    private val structuredTokens: Set<String> = buildSet {
        metadata?.let { value ->
            add(value.activityKind)
            add(value.planningEligibility)
            add(value.movementFamily)
            add(value.movementSubtype)
            add(value.programSlot)
            add(value.redundancyGroup)
            add(value.progressMetricType)
            add(value.strengthProgressionGroup)
            add(value.primaryStressProfile)
            add(value.stressMagnitudeHint)
            add(value.badmintonTransferLevel)
            add(value.appCueProfile)
            addAll(value.analysisEligibility.values)
            addAll(value.secondaryStressTags.values)
            addAll(value.jointImpactStressTags.values)
            addAll(value.badmintonTransferType.values)
            addAll(value.badmintonPhysicalQualities.values)
            addAll(value.sportContextTags.values)
        }
        if (!canonical) {
            add(exercise.activityKind)
            add(exercise.planningEligibility)
            add(exercise.movementPattern)
            add(exercise.movementCategory)
            add(exercise.trainingRole)
            add(exercise.badmintonTransferStrength)
            add(exercise.badmintonTransferRoles)
            add(exercise.progressMetricType)
            add(exercise.fatigueCategories)
        }
    }.flatMap(::splitTokens).toSet()

    val isRecovery: Boolean = identityHas("RECOVERY", "PREHAB", "MOBILITY", "CONTROL")
    val isCore: Boolean = identityHas("CORE", "ANTI_ROTATION", "ROTATION_CONTROL", "TRUNK")
    val isScapularStabilityExposure: Boolean = identityHas(
        "SCAP",
        "ROTATOR_CUFF",
        "REAR_DELT",
        "HORIZONTAL_PULL",
        "VERTICAL_PULL"
    ) || has("SCAPULAR_CONTROL", "SHOULDER_DURABILITY", "ROTATOR_CUFF_CONTROL")
    val isRehabLikeActivation: Boolean = rehabLikeActivationScore() >= 4
    val isIsolation: Boolean = identityHas("ISOLATION", "BICEPS", "TRICEPS", "CURL", "EXTENSION_ACCESSORY")
    val highStress: Boolean = metadata?.stressMagnitudeHint in setOf("HIGH", "VERY_HIGH") ||
        has("HIGH_AXIAL", "HIGH_NEURAL", "HIGH_IMPACT")
    val isHeavyLower: Boolean =
        identityHas("HEAVY_HINGE", "SQUAT_HEAVY_AXIAL", "MAIN_HINGE_STRENGTH", "MAIN_LOWER_STRENGTH") ||
            (identityHas("SQUAT", "HINGE", "DEADLIFT") && highStress)
    val isHighImpact: Boolean = has("PLYOMETRIC", "LANDING", "HOP", "JUMP", "ELASTIC_SSC", "JOINT_IMPACT")
    val isHighIntensityCod: Boolean = has("CHANGE_OF_DIRECTION", "DECELERATION", "COURT_FOOTWORK", "REACTION_DRILL")
    private val isCodSpecific: Boolean = metadata?.appCueProfile == "RANDOM_BEEP_CUE" ||
        has("FOOTWORK", "SPLIT_STEP", "CHANGE_OF_DIRECTION", "DECELERATION", "REACTION", "COURT_MOVEMENT")
    private val isReactivePowerSpecific: Boolean = isHighImpact ||
        has("ELASTIC_SSC", "REACTIVE_POWER", "EXPLOSIVE_POWER", "NEURAL_SPEED", "FIRST_STEP")
    val isAnchor: Boolean = identityHas("COMPOUND", "HEAVY_HINGE", "SQUAT_HEAVY", "VERTICAL_PULL", "HORIZONTAL_PULL")
    val isDirectSportSession: Boolean = metadata?.activityKind == "SPORT_SESSION" ||
        identityHas("BADMINTON_SESSION_SPORT_RECORDS", "OTHER_SPORT_SESSION_RECORDS") ||
        (metadata?.progressBehavior == ProgressMetricRuntimeBehavior.SESSION_DURATION &&
            has("BADMINTON_MATCH", "BADMINTON_LESSON", "DIRECT_PLAY_SESSION", "OTHER_SPORT_SESSION"))
    val isSportLike: Boolean = isDirectSportSession
    val isTimed: Boolean = metadata?.progressBehavior in setOf(
        ProgressMetricRuntimeBehavior.REPS_OR_TIME,
        ProgressMetricRuntimeBehavior.DISTANCE_OR_TIME,
        ProgressMetricRuntimeBehavior.SESSION_DURATION,
        ProgressMetricRuntimeBehavior.QUALITY_BASED
    )
    val badmintonFit: Double = when (metadata?.badmintonTransferLevel ?: exercise.badmintonTransferStrength) {
        "DIRECT" -> 1.0
        "SUPPORTIVE", BadmintonTransferStrength.SUPPORTIVE.name -> 0.75
        "GENERAL" -> 0.35
        else -> if (metadata?.appCueProfile == "RANDOM_BEEP_CUE") 0.85 else 0.1
    }
    val strengthFit: Double = when {
        has("STRENGTH_PROGRESS", "STRENGTH_VOLUME", "HYPERTROPHY_VOLUME", "ESTIMATED_1RM", "LOAD_REPS") -> 1.0
        isCore || isRecovery -> 0.5
        else -> 0.25
    }
    val metadataConfidenceFit: Double = if (canonical) 0.8 else 0.0
    private val isLowerPattern: Boolean = slotCapabilities.hasAny(
        ProgramSlotId.LOWER_SQUAT_PATTERN,
        ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
        ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL,
        ProgramSlotId.CALF_ANKLE_CAPACITY
    ) || identityHas(
        "LOWER", "SQUAT", "HINGE", "DEADLIFT", "LUNGE", "CALF", "ANKLE", "HIP", "KNEE", "HAMSTRING", "GLUTE"
    )
    private val isUpperPattern: Boolean = slotCapabilities.hasAny(
        ProgramSlotId.UPPER_PULL_ANCHOR,
        ProgramSlotId.UPPER_PUSH_SUPPORT,
        ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
        ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT
    ) || identityHas(
        "UPPER", "PULL", "ROW", "SCAP", "SHOULDER", "PUSH", "PRESS", "CHEST", "ELBOW", "BICEPS", "TRICEPS", "GRIP", "FOREARM"
    )

    fun isProgramSelectable(): Boolean = exercise.isActive && !isDirectSportSession && when {
        metadata != null -> metadata.planningEligibility == "PROGRAM_SELECTABLE"
        else -> exercise.isProgramSelectableExercise()
    }

    fun matchesEquipment(selected: Set<String>): Boolean {
        if (selected.isEmpty()) return true
        val equipment = splitTokens(exercise.equipment)
        return equipment.isEmpty() || "NONE" in equipment || equipment.any(selected::contains)
    }

    fun allowedForSlot(slot: ProgramTrainingSlot): Boolean = when (slot) {
        ProgramTrainingSlot.LOWER_STRENGTH,
        ProgramTrainingSlot.LOWER_STRENGTH_HEAVY,
        ProgramTrainingSlot.LOWER_TRANSFER_FULL -> isLowerPattern || isCore || isRecovery
        ProgramTrainingSlot.UPPER_STRENGTH,
        ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
        ProgramTrainingSlot.UPPER_SCAP_CORE_FULL -> isUpperPattern || isCore || isRecovery
        ProgramTrainingSlot.BADMINTON_TRANSFER -> badmintonFit >= 0.35 || isCore || isRecovery
        ProgramTrainingSlot.BADMINTON_COD,
        ProgramTrainingSlot.BADMINTON_COD_DECEL -> isCodSpecific || isCore || isRecovery
        ProgramTrainingSlot.POWER_REACTIVE,
        ProgramTrainingSlot.POWER_REACTIVE_LIGHT -> isReactivePowerSpecific || isCodSpecific || isCore || isRecovery
        ProgramTrainingSlot.RECOVERY_PREHAB,
        ProgramTrainingSlot.RECOVERY_WEAKPOINT,
        ProgramTrainingSlot.MICRO_RECOVERY -> isRecovery || isCore || (!highStress && !isAnchor && !isHighImpact && !isHighIntensityCod)
        else -> true
    }

    fun allowedForRole(slot: ProgramTrainingSlot, role: ProgramExerciseRole): Boolean {
        if (role == ProgramExerciseRole.ANCHOR && isRehabLikeActivation) return false
        if (role != ProgramExerciseRole.TRANSFER) return true
        return when (slot) {
            ProgramTrainingSlot.BADMINTON_COD,
            ProgramTrainingSlot.BADMINTON_COD_DECEL -> isCodSpecific
            ProgramTrainingSlot.POWER_REACTIVE,
            ProgramTrainingSlot.POWER_REACTIVE_LIGHT -> isReactivePowerSpecific || isCodSpecific
            ProgramTrainingSlot.BADMINTON_TRANSFER -> badmintonFit >= 0.90
            else -> true
        }
    }

    fun allowedForTemplateSlot(
        plannedSlot: ProgramTrainingSlot,
        templateSlot: TemplateExerciseSlot,
        coveragePolicy: CoverageAccountingPolicy
    ): Boolean {
        val target = templateSlot.targetSlot
        if (target == null) {
            return allowedForSlot(plannedSlot) && allowedForRole(plannedSlot, templateSlot.role)
        }
        if (templateSlot.role == ProgramExerciseRole.ANCHOR && isRehabLikeActivation) return false
        val credit = coveragePolicy.credit(slotCapabilities, target)
        return if (templateSlot.required) {
            credit.value >= CoverageCredit.PARTIAL.value
        } else {
            credit != CoverageCredit.NONE
        }
    }

    fun roleFit(role: ProgramExerciseRole): Double = when (role) {
        ProgramExerciseRole.ANCHOR -> if (isAnchor) 1.0 else if (strengthFit >= 0.8) 0.55 else 0.0
        ProgramExerciseRole.TRANSFER -> badmintonFit
        ProgramExerciseRole.SUPPORT -> when {
            isHighImpact || isHighIntensityCod -> 0.1
            !isIsolation && !isRecovery -> max(strengthFit, badmintonFit * 0.7)
            else -> 0.25
        }
        ProgramExerciseRole.CORE -> if (isCore) 1.0 else 0.0
        ProgramExerciseRole.PREHAB -> if (isRecovery) 1.0 else 0.1
        ProgramExerciseRole.ACCESSORY -> if (isIsolation || (!isAnchor && !isSportLike)) 0.9 else 0.2
    }

    fun slotFit(slot: ProgramTrainingSlot): Double = when (slot) {
        ProgramTrainingSlot.LOWER_STRENGTH,
        ProgramTrainingSlot.LOWER_STRENGTH_HEAVY,
        ProgramTrainingSlot.LOWER_TRANSFER_FULL -> if (isLowerPattern) 1.0 else 0.2
        ProgramTrainingSlot.UPPER_STRENGTH,
        ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
        ProgramTrainingSlot.UPPER_SCAP_CORE_FULL -> if (isUpperPattern) 1.0 else 0.2
        ProgramTrainingSlot.BADMINTON_TRANSFER -> badmintonFit
        ProgramTrainingSlot.BADMINTON_COD,
        ProgramTrainingSlot.BADMINTON_COD_DECEL -> if (isCodSpecific) 1.0 else 0.1
        ProgramTrainingSlot.POWER_REACTIVE,
        ProgramTrainingSlot.POWER_REACTIVE_LIGHT -> if (isReactivePowerSpecific || isCodSpecific) 1.0 else 0.1
        ProgramTrainingSlot.RECOVERY_PREHAB,
        ProgramTrainingSlot.RECOVERY_WEAKPOINT,
        ProgramTrainingSlot.MICRO_RECOVERY -> if (isRecovery || isCore) 1.0 else 0.2
        else -> max(strengthFit, badmintonFit)
    }

    fun templateSpecificityFit(targetSlot: ProgramSlotId?): Double = when (targetSlot) {
        ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT -> when {
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY in slotCapabilities.secondary -> 0.90
            badmintonFit >= 0.75 -> 0.35
            else -> 0.0
        }
        else -> 0.0
    }

    private fun has(vararg needles: String): Boolean = needles.any { needle ->
        structuredTokens.any { token -> token.contains(needle) }
    }

    private fun identityHas(vararg needles: String): Boolean = needles.any { needle ->
        identityTokens.any { token -> token.contains(needle) }
    }

    private fun rehabLikeActivationScore(): Int {
        val metadataValue = metadata ?: return 0
        var score = 0
        if (metadataValue.primaryStressProfile.contains("LOW_LOAD_PREHAB_CONTROL")) score += 2
        if (has("LOW_LOAD_CONTROL", "LOCAL_STABILIZER_LOAD", "MOTOR_CONTROL")) score += 1
        if (identityHas("RECOVERY_PREHAB", "ROTATOR_CUFF_CARE", "MOBILITY")) score += 2
        if (metadataValue.progressBehavior == ProgressMetricRuntimeBehavior.QUALITY_BASED) score += 1
        if (metadataValue.strengthProgressionGroup in setOf("", "NONE", "NOT_APPLICABLE")) score += 1
        if (metadataValue.stressMagnitudeHint == "LOW" && metadataValue.recoveryDurationClass == "SHORT") score += 1

        val equipmentTokens = splitTokens(exercise.equipment)
        if (exercise.defaultRestSeconds <= 45 && equipmentTokens.any {
                it in setOf("BODYWEIGHT", "BAND", "WALL", "맨몸", "밴드", "벽")
            }
        ) {
            score += 1
        }
        if (metadataValue.movementSubtype in LOW_LOAD_ACTIVATION_SUBTYPES) score += 4

        val hasProgression = metadataValue.strengthProgressionGroup !in setOf("", "NONE", "NOT_APPLICABLE")
        if (hasProgression) score -= 2
        if (identityHas("MAIN_", "STRENGTH", "COMPOUND", "HEAVY")) score -= 3
        if (identityHas("SCAPULAR_RETRACTION_EXTERNAL_ROTATION", "REAR_DELT")) score -= 2
        if (identityHas("ACCESSORY") && equipmentTokens.any {
                it in setOf("CABLE", "DUMBBELL", "BARBELL", "MACHINE", "케이블", "덤벨", "바벨", "머신")
            }
        ) {
            score -= 2
        }
        return score
    }

    private companion object {
        val LOW_LOAD_ACTIVATION_SUBTYPES = setOf(
            "WALL_SLIDE",
            "SCAPULAR_PUSH_UP",
            "BAND_EXTERNAL_ROTATION",
            "BIRD_DOG",
            "DEAD_BUG",
            "SUPERMAN",
            "SCAPULAR_PULL_UP"
        )
    }
}

private data class ProgramPrescription(
    val setCount: Int,
    val reps: Int,
    val seconds: Int,
    val rpe: Int,
    val label: String
)

private data class ProgramWeightSuggestion(val weightKg: Double, val source: String)

private class ProgramHistoryWeightIndex(
    history: List<WorkoutEntryWithSets>,
    exercises: List<Exercise>
) {
    private val exerciseById = exercises.associateBy(Exercise::id)
    private val confirmed = history.flatMap { entry ->
        entry.sets.filter { it.confirmed && it.weightKg > 0.0 && it.reps > 0 }.map { set ->
            HistoricalSet(entry.entry.exerciseId, entry.entry.date, set.reps, set.weightKg)
        }
    }

    fun suggest(
        exercise: Exercise,
        targetReps: Int,
        intensityMultiplier: Double,
        today: LocalDate
    ): ProgramWeightSuggestion {
        if (targetReps <= 0) return ProgramWeightSuggestion(0.0, "TIMED_OR_QUALITY")
        val recentCutoff = today.minusDays(90).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val direct = confirmed.filter { it.exerciseId == exercise.id }
        val recent = direct.filter { it.date >= recentCutoff }.maxByOrNull(HistoricalSet::date)
        val source = recent
            ?: direct.maxByOrNull { it.weightKg * it.reps }
            ?: return ProgramWeightSuggestion(0.0, "MANUAL_INPUT")
        val e1rm = source.weightKg * (1.0 + source.reps / 30.0)
        val target = e1rm / (1.0 + targetReps / 30.0) * intensityMultiplier * 0.90
        val step = if (exerciseById[source.exerciseId]?.equipment?.uppercase(Locale.US)?.contains("DUMBBELL") == true) 2.0 else 2.5
        return ProgramWeightSuggestion(
            weightKg = (target / step).toInt().coerceAtLeast(0) * step,
            source = if (recent != null) "DIRECT_HISTORY_HIGH" else "DIRECT_HISTORY_MEDIUM"
        )
    }

    private data class HistoricalSet(
        val exerciseId: Long,
        val date: String,
        val reps: Int,
        val weightKg: Double
    )
}

private fun splitTokens(value: String): Set<String> = value
    .split('|', ',', '/', ';', ' ')
    .map { it.trim().uppercase(Locale.US) }
    .filter(String::isNotBlank)
    .toSet()

private fun Double.toPercent(): String = "${(this * 100).roundToInt()}%"
