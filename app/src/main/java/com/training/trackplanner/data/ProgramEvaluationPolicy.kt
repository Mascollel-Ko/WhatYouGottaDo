package com.training.trackplanner.data

import kotlin.math.min
import kotlin.math.roundToInt

internal class ProgramEvaluationPolicy(
    private val densityPolicy: ProgramSessionDensityPolicy = ProgramSessionDensityPolicy(),
    private val dayIntensityPolicy: ProgramDayIntensityPolicy = ProgramDayIntensityPolicy(),
    private val corePatternPolicy: ProgramCorePatternPolicy = ProgramCorePatternPolicy(),
    private val selectedExerciseScorePolicy: ProgramSelectedExerciseScorePolicy = ProgramSelectedExerciseScorePolicy()
) {
    fun evaluate(skeleton: GeneratedProgramSkeleton): ProgramEvaluation {
        val weekly = skeleton.weekPlans.map { week ->
            evaluateWeek(week.weekIndex, skeleton)
        }
        val programIssues = programIssues(skeleton, weekly)
        val fatigueScore = average(weekly.map(WeeklyProgramEvaluation::fatigueScore))
        val strengthScore = average(weekly.map(WeeklyProgramEvaluation::strengthDistributionScore))
        val transferScore = average(weekly.map(WeeklyProgramEvaluation::badmintonTransferScore))
        val densityScore = average(weekly.map(WeeklyProgramEvaluation::densityScore))
        val intensityScore = average(weekly.map(WeeklyProgramEvaluation::intensityDistributionScore))
        val equipmentScore = equipmentScore(skeleton)
        val overall = average(
            listOf(fatigueScore, strengthScore, transferScore, densityScore, intensityScore, equipmentScore)
        )
        val recoveredIssues = if (weekly.any { it.score < 70 } && overall >= 70) {
            listOf(issue(
                ProgramEvaluationIssueType.WEEKLY_BALANCE_RECOVERS_LATER,
                ProgramEvaluationIssueSeverity.INFO,
                "Weekly imbalance is present but program-wide balance recovers."
            ))
        } else {
            emptyList()
        }
        val issues = (programIssues + recoveredIssues).distinctBy { it.type to it.message }
        val adjustedOverall = (overall - issues.count { it.severity == ProgramEvaluationIssueSeverity.SEVERE } * 4)
            .coerceIn(0, 100)
        return ProgramEvaluation(
            overallScore = capOverallScore(adjustedOverall, issues),
            weeklyScores = weekly,
            fatigueScore = fatigueScore,
            strengthDistributionScore = strengthScore,
            badmintonTransferScore = transferScore,
            densityScore = densityScore,
            intensityDistributionScore = intensityScore,
            equipmentUtilizationScore = equipmentScore,
            issues = issues,
            suggestions = issues.map(::suggestionFor).distinct()
        )
    }

    private fun evaluateWeek(weekIndex: Int, skeleton: GeneratedProgramSkeleton): WeeklyProgramEvaluation {
        val items = skeleton.items.filter { it.weekNumber == weekIndex }
        val densityScore = densityScore(items, skeleton.request)
        val strengthScore = strengthScore(items, skeleton.request, weekly = true)
        val transferScore = transferScore(items)
        val fatigueScore = fatigueScore(items, skeleton.request)
        val intensityScore = intensityScore(items, skeleton.request)
        val issues = weekIssues(items, skeleton.request)
        return WeeklyProgramEvaluation(
            weekIndex = weekIndex,
            score = (average(listOf(densityScore, strengthScore, transferScore, fatigueScore, intensityScore)) -
                issues.count { it.severity == ProgramEvaluationIssueSeverity.SEVERE } * 5).coerceIn(0, 100),
            fatigueScore = fatigueScore,
            strengthDistributionScore = strengthScore,
            badmintonTransferScore = transferScore,
            densityScore = densityScore,
            intensityDistributionScore = intensityScore,
            issues = issues
        )
    }

    private fun programIssues(
        skeleton: GeneratedProgramSkeleton,
        weekly: List<WeeklyProgramEvaluation>
    ): List<ProgramEvaluationIssue> {
        val items = skeleton.items
        val issues = buildList {
            if (selectedMainMissing(skeleton)) {
                add(issue(ProgramEvaluationIssueType.SELECTED_MAIN_MISSING, ProgramEvaluationIssueSeverity.SEVERE,
                    "Exact selected-main exercises are available but missing from the generated plan."))
            }
            if (strengthScore(items, skeleton.request, weekly = false) < 70) {
                add(issue(ProgramEvaluationIssueType.LOW_STRENGTH_ANCHOR, ProgramEvaluationIssueSeverity.SEVERE,
                    "Program-wide strength anchors are under target."))
            }
            if (equipmentScore(skeleton) < 70) {
                add(issue(ProgramEvaluationIssueType.LOADED_STRENGTH_UNDERUSED, ProgramEvaluationIssueSeverity.SEVERE,
                    "Loaded strength equipment is available but underused."))
            }
            if (densityScore(items, skeleton.request) < 70) {
                add(issue(ProgramEvaluationIssueType.LOW_SESSION_DENSITY, ProgramEvaluationIssueSeverity.SEVERE,
                    "Multiple sessions are below useful exercise density."))
            }
            if (transferOveruse(items)) {
                add(issue(ProgramEvaluationIssueType.TRANSFER_GOAL_OVERUSE, ProgramEvaluationIssueSeverity.WARNING,
                    "A badminton transfer target repeats across too many weeks."))
            }
            if (movementFamilyOveruse(items)) {
                add(issue(ProgramEvaluationIssueType.MUSCLE_GROUP_OVERUSE, ProgramEvaluationIssueSeverity.WARNING,
                    "One movement family dominates too much of the program."))
            }
            if (coreAccessoryOveruse(items, skeleton.request)) {
                add(issue(ProgramEvaluationIssueType.TOO_MUCH_CORE_REPETITION, ProgramEvaluationIssueSeverity.SEVERE,
                    "One core or accessory filler repeats too often."))
            }
            if (weekProfilesRepeat(skeleton)) {
                add(issue(ProgramEvaluationIssueType.NO_WEEK_VARIATION, ProgramEvaluationIssueSeverity.WARNING,
                    "Week-to-week exercise profiles are too similar."))
            }
            if (dayIntensityPolicy.warnings(items, skeleton.request).contains("PROGRAM_HIGH_LOWER_FATIGUE_CLUSTER")) {
                add(issue(ProgramEvaluationIssueType.HIGH_LOWER_BODY_FATIGUE_CLUSTER,
                    ProgramEvaluationIssueSeverity.WARNING,
                    "High lower-body or COD stress is clustered on adjacent days."))
            }
            if (weekly.zipWithNext().any { (previous, next) -> previous.fatigueScore < 55 && next.fatigueScore < 65 }) {
                add(issue(ProgramEvaluationIssueType.NO_RECOVERY_AFTER_HIGH_FATIGUE_WEEK,
                    ProgramEvaluationIssueSeverity.WARNING,
                    "High fatigue week is not followed by enough recovery."))
            }
        }
        return issues
    }

    private fun weekIssues(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): List<ProgramEvaluationIssue> =
        buildList {
            if (densityScore(items, request) < 70) {
                add(issue(ProgramEvaluationIssueType.LOW_SESSION_DENSITY, ProgramEvaluationIssueSeverity.WARNING,
                    "This week has low session density."))
            }
            if (strengthScore(items, request, weekly = true) < 60) {
                add(issue(ProgramEvaluationIssueType.LOW_STRENGTH_ANCHOR, ProgramEvaluationIssueSeverity.WARNING,
                    "This week has few strength anchors."))
            }
            if (transferOveruse(items)) {
                add(issue(ProgramEvaluationIssueType.TRANSFER_GOAL_OVERUSE, ProgramEvaluationIssueSeverity.WARNING,
                    "This week repeats one transfer target too often."))
            }
        }

    private fun densityScore(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): Int {
        val minimum = densityPolicy.usefulMinimumExerciseCount(request.dailyAvailableMinutes)
        val low = items.groupBy { it.weekNumber to it.dayOfWeek }.count { (_, rows) -> rows.size < minimum }
        return (100 - low * 12).coerceIn(0, 100)
    }

    private fun strengthScore(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest, weekly: Boolean): Int {
        val count = items.count(::isStrengthAnchor)
        val target = when {
            request.goal != ProgramGoal.BADMINTON_SUPPORT -> if (weekly) 1 else request.durationWeeks
            request.badmintonTransferRatio >= 0.80 -> if (weekly) 1 else request.durationWeeks
            request.availableDaysPerWeek >= 5 -> if (weekly) 2 else request.durationWeeks * 2
            else -> if (weekly) 1 else request.durationWeeks
        }
        return (100.0 * count / target.coerceAtLeast(1)).roundToInt().coerceIn(0, 100)
    }

    private fun transferScore(items: List<ProgramSkeletonItem>): Int {
        val transfer = items.filter { it.selectionRole == ProgramExerciseRole.TRANSFER.name }
        if (transfer.isEmpty()) return 85
        val largest = transfer.groupBy { it.redundancyGroup.ifBlank { it.stableKey } }.values.maxOfOrNull(List<*>::size) ?: 0
        return (100 - largest * 4).coerceIn(0, 100)
    }

    private fun fatigueScore(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): Int =
        if ("PROGRAM_HIGH_LOWER_FATIGUE_CLUSTER" in dayIntensityPolicy.warnings(items, request)) 62 else 88

    private fun intensityScore(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): Int {
        val warnings = dayIntensityPolicy.warnings(items, request)
        return (100 - warnings.count { it.startsWith("PROGRAM_INTENSITY") } * 22).coerceIn(0, 100)
    }

    private fun equipmentScore(skeleton: GeneratedProgramSkeleton): Int {
        val loadedAvailable = skeleton.request.availableEquipment.any { it.uppercase() in LOADED_EQUIPMENT }
        if (!loadedAvailable) return 90
        val loaded = skeleton.items.count(::isLoadedStrength)
        val target = if (skeleton.request.availableDaysPerWeek >= 5) skeleton.request.durationWeeks * 2 else skeleton.request.durationWeeks
        return (100.0 * loaded / target.coerceAtLeast(1)).roundToInt().coerceIn(0, 100)
    }

    private fun transferOveruse(items: List<ProgramSkeletonItem>): Boolean =
        items.filter { it.selectionRole == ProgramExerciseRole.TRANSFER.name }
            .groupBy { it.redundancyGroup.ifBlank { it.stableKey } }
            .any { (key, rows) -> key.isNotBlank() && key != "NOT_APPLICABLE" && rows.size > 4 }

    private fun movementFamilyOveruse(items: List<ProgramSkeletonItem>): Boolean {
        val families = items.map(ProgramSkeletonItem::movementFamily).filter { it.isNotBlank() && it != "NOT_APPLICABLE" }
        if (families.isEmpty()) return false
        return families.groupingBy { it }.eachCount().values.maxOrNull().orEmptyInt() > items.size / 3
    }

    private fun coreAccessoryOveruse(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): Boolean =
        items
            .filter { item ->
                item.selectionRole in CORE_ACCESSORY_ROLE_NAMES ||
                    corePatternPolicy.corePattern(item) != ProgramCorePattern.NONE
            }
            .groupBy { it.stableKey.ifBlank { it.exerciseName } }
            .any { (key, rows) -> key.isNotBlank() && rows.size > request.durationWeeks }

    private fun selectedMainMissing(skeleton: GeneratedProgramSkeleton): Boolean {
        if (skeleton.request.goal != ProgramGoal.BADMINTON_SUPPORT || skeleton.request.dailyAvailableMinutes < 40) {
            return false
        }
        val selectedMainAvailable = skeleton.candidateTraces.any { trace ->
            trace.selectedMainReservationStableKey.isNotBlank() ||
                trace.scoreAdjustments.any(ProgramCandidateScoreTrace::selectedMainBoostApplied)
        }
        return selectedMainAvailable &&
            skeleton.items.none { selectedExerciseScorePolicy.isSelectedMainStableKey(it.stableKey) }
    }

    private fun weekProfilesRepeat(skeleton: GeneratedProgramSkeleton): Boolean {
        if (skeleton.request.durationWeeks < 4) return false
        val signatures = skeleton.items
            .groupBy(ProgramSkeletonItem::weekNumber)
            .map { (_, rows) ->
                rows.sortedWith(compareBy(ProgramSkeletonItem::dayOfWeek, ProgramSkeletonItem::orderIndex))
                    .joinToString("|") { item ->
                        listOf(item.selectionRole, item.requestedTemplateSlot, item.stableKey).joinToString(":")
                    }
            }
        return signatures.size >= 4 && signatures.distinct().size == 1
    }

    private fun capOverallScore(score: Int, issues: List<ProgramEvaluationIssue>): Int {
        val issueTypes = issues.map(ProgramEvaluationIssue::type).toSet()
        var capped = score
        if (ProgramEvaluationIssueType.LOW_STRENGTH_ANCHOR in issueTypes) capped = min(capped, 92)
        if (ProgramEvaluationIssueType.LOADED_STRENGTH_UNDERUSED in issueTypes) capped = min(capped, 92)
        if (ProgramEvaluationIssueType.LOW_SESSION_DENSITY in issueTypes) capped = min(capped, 92)
        if (ProgramEvaluationIssueType.TOO_MUCH_CORE_REPETITION in issueTypes) capped = min(capped, 90)
        if (ProgramEvaluationIssueType.NO_WEEK_VARIATION in issueTypes) capped = min(capped, 84)
        return capped
    }

    private fun isStrengthAnchor(item: ProgramSkeletonItem): Boolean =
        item.selectionRole in STRENGTH_ROLE_NAMES &&
            item.badmintonTransferLevel != "DIRECT" &&
            (
                item.requestedTemplateSlot in STRENGTH_SLOT_NAMES ||
                    item.primarySlotCapabilities.any(STRENGTH_SLOT_NAMES::contains) ||
                    item.secondarySlotCapabilities.any(STRENGTH_SLOT_NAMES::contains)
                )

    private fun isLoadedStrength(item: ProgramSkeletonItem): Boolean =
        isStrengthAnchor(item) && item.exerciseName.contains(LOADED_NAME_HINTS)

    private fun String.contains(tokens: Set<String>): Boolean = tokens.any { contains(it, ignoreCase = true) }

    private fun issue(
        type: ProgramEvaluationIssueType,
        severity: ProgramEvaluationIssueSeverity,
        message: String
    ): ProgramEvaluationIssue = ProgramEvaluationIssue(type, severity, message)

    private fun suggestionFor(issue: ProgramEvaluationIssue): String = when (issue.type) {
        ProgramEvaluationIssueType.SELECTED_MAIN_MISSING,
        ProgramEvaluationIssueType.LOW_STRENGTH_ANCHOR,
        ProgramEvaluationIssueType.LOADED_STRENGTH_UNDERUSED -> "근력운동과 배드민턴 전이훈련의 균형을 보정했습니다."
        ProgramEvaluationIssueType.LOW_SESSION_DENSITY -> "45분 세션에 맞게 운동 수를 조정했습니다."
        ProgramEvaluationIssueType.HIGH_LOWER_BODY_FATIGUE_CLUSTER,
        ProgramEvaluationIssueType.NO_RECOVERY_AFTER_HIGH_FATIGUE_WEEK -> "연속 하체 피로가 몰리지 않도록 일부 운동을 재배치했습니다."
        else -> "반복되는 운동 목적이 과해지지 않도록 구성을 점검했습니다."
    }

    private fun average(values: List<Int>): Int =
        if (values.isEmpty()) 100 else values.average().roundToInt().coerceIn(0, 100)

    private fun Int?.orEmptyInt(): Int = this ?: 0

    private companion object {
        val STRENGTH_ROLE_NAMES = setOf(ProgramExerciseRole.ANCHOR.name, ProgramExerciseRole.SUPPORT.name)
        val STRENGTH_SLOT_NAMES = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN.name,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name,
            ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL.name,
            ProgramSlotId.UPPER_PULL_ANCHOR.name,
            ProgramSlotId.UPPER_PUSH_SUPPORT.name,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT.name,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name
        )
        val CORE_ACCESSORY_ROLE_NAMES = setOf(
            ProgramExerciseRole.CORE.name,
            ProgramExerciseRole.PREHAB.name,
            ProgramExerciseRole.ACCESSORY.name
        )
        val LOADED_EQUIPMENT = setOf(
            "BARBELL", "DUMBBELL", "MACHINE", "CABLE", "SMITH_MACHINE",
            "LEG_PRESS_MACHINE", "HACK_SQUAT_MACHINE", "LEG_CURL_MACHINE", "LEG_EXTENSION_MACHINE",
            "바벨", "덤벨", "머신", "케이블", "스미스", "레그프레스", "핵스쿼트", "레그컬", "레그익스텐션"
        )
        val LOADED_NAME_HINTS = setOf(
            "바벨", "덤벨", "머신", "케이블", "스미스", "레그 프레스", "핵 스쿼트",
            "bench", "row", "press", "squat", "deadlift"
        )
    }
}
