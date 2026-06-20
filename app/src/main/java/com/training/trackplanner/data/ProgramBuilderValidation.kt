package com.training.trackplanner.data

import kotlin.math.max

internal object ProgramBuilderValidator {
    fun validate(
        result: GeneratedProgramSkeleton,
        gate: ProgramFatigueGate? = null
    ): List<ProgramValidationIssue> {
        val issues = mutableListOf<ProgramValidationIssue>()
        validateSessions(result, issues)
        validateHardDays(result, gate, issues)
        validateRepetition(result, issues)
        validateRehabLikeDominance(result, gate, issues)
        validateRollingFatigue(result, issues)
        validateMovementCoverage(result, issues)
        validateFourWeekDistribution(result, issues)
        return issues.distinctBy { Triple(it.code, it.severity, it.message) }
    }

    private fun validateSessions(
        result: GeneratedProgramSkeleton,
        issues: MutableList<ProgramValidationIssue>
    ) {
        val sessions = result.items.groupBy { it.weekNumber to it.dayOfWeek }
        sessions.forEach { (key, items) ->
            if (items.size > 7) {
                issues += issue(
                    "SESSION_EXERCISE_LIMIT",
                    ProgramValidationSeverity.HARD,
                    "${key.first}주 ${key.second}일 세션이 운동 7개를 초과합니다."
                )
            }
            val estimatedSeconds = warmupReserveSeconds(result.request.dailyAvailableMinutes) +
                items.sumOf { item -> item.estimatedDurationSeconds.takeIf { it > 0 } ?: fallbackDuration(item) }
            if (estimatedSeconds > result.request.dailyAvailableMinutes * 60) {
                issues += issue(
                    "SESSION_TIME_BUDGET",
                    ProgramValidationSeverity.HARD,
                    "${key.first}주 ${key.second}일 예상 ${estimatedSeconds / 60}분이 " +
                        "시간 예산 ${result.request.dailyAvailableMinutes}분을 초과합니다."
                )
            }
        }
        result.items.filter(ProgramSkeletonItem::directSportSession).forEach { item ->
            issues += issue(
                "DIRECT_SPORT_SESSION_EXCLUDED",
                ProgramValidationSeverity.HARD,
                "${item.exerciseName}은 직접 스포츠 세션이므로 프로그램 운동으로 선택할 수 없습니다."
            )
        }
    }

    private fun validateHardDays(
        result: GeneratedProgramSkeleton,
        gate: ProgramFatigueGate?,
        issues: MutableList<ProgramValidationIssue>
    ) {
        val hardDaysByWeek = result.items.groupBy(ProgramSkeletonItem::weekNumber).mapValues { (_, weekItems) ->
            weekItems.groupBy(ProgramSkeletonItem::dayOfWeek)
                .count { (_, dayItems) -> dayItems.firstOrNull()?.dayIntensity == ProgramDayIntensity.HARD.name }
        }
        result.weekPlans.forEachIndexed { index, week ->
            val hardDays = hardDaysByWeek[week.weekIndex] ?: 0
            val normalLimit = if (result.request.availableDaysPerWeek <= 4) 1 else 2
            val nextWeek = result.weekPlans.getOrNull(index + 1)
            val followedByLowWeek = nextWeek?.deloadFlag == true ||
                (nextWeek != null && (hardDaysByWeek[nextWeek.weekIndex] ?: 0) <= 1)
            val threeHardAllowed = result.request.availableDaysPerWeek >= 5 &&
                week.weekType in setOf(ProgramWeekType.HIGH.name, ProgramWeekType.INTENSIFY.name) &&
                gate?.band !in setOf(ProgramFatigueBand.ORANGE, ProgramFatigueBand.RED) &&
                followedByLowWeek
            val hardLimit = if (threeHardAllowed) 3 else normalLimit
            if (hardDays > hardLimit) {
                issues += issue(
                    "WEEKLY_HARD_DAY_LIMIT",
                    ProgramValidationSeverity.HARD,
                    "${week.weekIndex}주 hard day ${hardDays}일이 허용 상한 ${hardLimit}일을 초과합니다."
                )
            }
            if (hardDays == 3 && !threeHardAllowed) {
                issues += issue(
                    "THREE_HARD_DAY_WAVE",
                    ProgramValidationSeverity.HARD,
                    "${week.weekIndex}주의 hard day 3일은 HIGH/INTENSIFY와 후속 저부하 주 조건을 충족하지 않습니다."
                )
            }
        }
        if (gate?.band == ProgramFatigueBand.RED && hardDaysByWeek.values.any { it > 0 }) {
            issues += issue(
                "RED_FATIGUE_HARD_DAY",
                ProgramValidationSeverity.HARD,
                "RED 피로 상태에서는 hard day가 0이어야 합니다."
            )
        }
    }

    private fun validateRepetition(
        result: GeneratedProgramSkeleton,
        issues: MutableList<ProgramValidationIssue>
    ) {
        val weeks = result.request.durationWeeks
        val anchorKeys = result.items
            .filter { it.selectionRole == "ANCHOR" }
            .map(ProgramSkeletonItem::stableKey)
            .filter(String::isNotBlank)
            .toSet()
        val accessoryByKey = result.items
            .filter { it.selectionRole in setOf("ACCESSORY", "PREHAB", "TRANSFER") }
            .filter { it.stableKey.isNotBlank() }
            .filterNot { it.stableKey in anchorKeys }
            .groupBy(ProgramSkeletonItem::stableKey)
        accessoryByKey.forEach { (stableKey, rows) ->
            val exposureWeeks = rows.map(ProgramSkeletonItem::weekNumber).distinct().size
            if (weeks >= 4 && exposureWeeks == weeks) {
                issues += issue(
                    "NON_ANCHOR_FIXED_REPETITION",
                    ProgramValidationSeverity.WARNING,
                    "비-anchor $stableKey 항목이 모든 주에 고정되어 rotation이 부족합니다."
                )
            }
        }
        result.items.groupBy { it.weekNumber to it.redundancyGroup }
            .filterKeys { (_, group) -> group.isNotBlank() && group != "NOT_APPLICABLE" }
            .filterValues { it.size > 3 }
            .forEach { (key, rows) ->
                issues += issue(
                    "REDUNDANCY_GROUP_EXPOSURE",
                    ProgramValidationSeverity.SOFT_PENALTY,
                    "${key.first}주 ${key.second} 중복군 노출이 ${rows.size}회입니다."
                )
            }
        result.items.groupBy { it.weekNumber to it.movementFamily }
            .filterKeys { (_, family) -> family.isNotBlank() && family != "NOT_APPLICABLE" }
            .filterValues { it.size > max(3, result.request.availableDaysPerWeek) }
            .forEach { (key, rows) ->
                issues += issue(
                    "MOVEMENT_FAMILY_EXPOSURE",
                    ProgramValidationSeverity.SOFT_PENALTY,
                    "${key.first}주 ${key.second} family 노출이 ${rows.size}회로 편중되었습니다."
                )
            }
    }

    private fun validateRollingFatigue(
        result: GeneratedProgramSkeleton,
        issues: MutableList<ProgramValidationIssue>
    ) {
        if (result.weekPlans.size < 2) return
        result.weekPlans.windowed(2).forEach { window ->
            val weekNumbers = window.map(ProgramWeekPlan::weekIndex).toSet()
            val items = result.items.filter { it.weekNumber in weekNumbers }
            val neuromuscular = rollingAxisLoad(items, ProgramSkeletonItem::neuromuscularStressLevel)
            val jointImpact = rollingAxisLoad(items, ProgramSkeletonItem::jointTendonImpactStressLevel)
            val systemic = rollingAxisLoad(items, ProgramSkeletonItem::systemicMuscularStressLevel)
            val local = rollingAxisLoad(items, ProgramSkeletonItem::localMuscularStressLevel)
            val focus = rollingAxisLoad(items, ProgramSkeletonItem::movementFocusDemandLevel)
            val managedLimit = result.request.availableDaysPerWeek * 2 * 1.45
            val informationalLimit = result.request.availableDaysPerWeek * 2 * 1.75
            if (neuromuscular > managedLimit) {
                issues += issue(
                    "TWO_WEEK_NEUROMUSCULAR_WAVE",
                    ProgramValidationSeverity.WARNING,
                    "${window.first().weekIndex}-${window.last().weekIndex}주 신경계 누적 ${format(neuromuscular)}가 관리 기준 ${format(managedLimit)}를 초과합니다."
                )
            }
            if (jointImpact > managedLimit) {
                issues += issue(
                    "TWO_WEEK_JOINT_IMPACT_WAVE",
                    ProgramValidationSeverity.WARNING,
                    "${window.first().weekIndex}-${window.last().weekIndex}주 관절/건/충격 누적 ${format(jointImpact)}가 관리 기준 ${format(managedLimit)}를 초과합니다."
                )
            }
            listOf("전신 근육" to systemic, "국소 근육" to local, "동작 집중" to focus)
                .filter { (_, value) -> value > informationalLimit }
                .forEach { (label, value) ->
                    issues += issue(
                        "TWO_WEEK_INFORMATIONAL_AXIS",
                        ProgramValidationSeverity.SOFT_PENALTY,
                        "${window.first().weekIndex}-${window.last().weekIndex}주 $label 누적 ${format(value)}가 높습니다."
                    )
                }
        }
    }

    private fun validateRehabLikeDominance(
        result: GeneratedProgramSkeleton,
        gate: ProgramFatigueGate?,
        issues: MutableList<ProgramValidationIssue>
    ) {
        val sessions = result.items.groupBy { it.weekNumber to it.dayOfWeek }
        sessions.forEach { (key, items) ->
            val rehabItems = items.filter(ProgramSkeletonItem::rehabLikeActivation)
            if (rehabItems.isEmpty()) return@forEach
            val recoveryContext = items.all { item -> item.isRecoveryContext(result, gate) }
            if (!recoveryContext && rehabItems.size > 1) {
                issues += issue(
                    "REHAB_ACTIVATION_SESSION_CAP",
                    ProgramValidationSeverity.WARNING,
                    "${key.first}주 ${key.second}일 정상 세션에 rehab-like activation이 ${rehabItems.size}개 포함되었습니다."
                )
            }
            if (!recoveryContext && rehabItems.any { it.orderIndex == 1 || it.selectionRole == "ANCHOR" }) {
                issues += issue(
                    "REHAB_ACTIVATION_MAIN_POSITION",
                    ProgramValidationSeverity.WARNING,
                    "${key.first}주 ${key.second}일 rehab-like activation이 메인 위치를 차지합니다."
                )
            }
            if (!recoveryContext && rehabItems.size >= 2 && rehabItems.size * 2 >= items.size) {
                issues += issue(
                    "REHAB_ACTIVATION_DOMINANCE",
                    ProgramValidationSeverity.HARD,
                    "${key.first}주 ${key.second}일 strength/transfer 세션의 절반 이상이 rehab-like activation입니다."
                )
            }
        }

        if (gate?.band != ProgramFatigueBand.RED && result.weekPlans.size >= 2) {
            result.weekPlans.windowed(2).forEach { window ->
                val weekNumbers = window.map(ProgramWeekPlan::weekIndex).toSet()
                val normalRehab = result.items.filter { item ->
                    item.weekNumber in weekNumbers && item.rehabLikeActivation &&
                        !item.isRecoveryContext(result, gate)
                }
                normalRehab.groupBy(ProgramSkeletonItem::stableKey)
                    .filterKeys(String::isNotBlank)
                    .filterValues { it.size > 1 }
                    .forEach { (stableKey, rows) ->
                        issues += issue(
                            "TWO_WEEK_REHAB_STABLE_KEY_REPEAT",
                            ProgramValidationSeverity.WARNING,
                            "${window.first().weekIndex}-${window.last().weekIndex}주 rehab-like $stableKey 항목이 ${rows.size}회 반복됩니다."
                        )
                    }
            }
        }

        if (result.weekPlans.size >= 4) {
            result.weekPlans.windowed(4).forEach { window ->
                val weekNumbers = window.map(ProgramWeekPlan::weekIndex).toSet()
                val normalItems = result.items.filter { item ->
                    item.weekNumber in weekNumbers && !item.isRecoveryContext(result, gate)
                }
                val rehabItems = normalItems.filter(ProgramSkeletonItem::rehabLikeActivation)
                if (normalItems.size >= 8 && rehabItems.size.toDouble() / normalItems.size > 0.15) {
                    issues += issue(
                        "FOUR_WEEK_REHAB_ACTIVATION_SHARE",
                        ProgramValidationSeverity.WARNING,
                        "${window.first().weekIndex}-${window.last().weekIndex}주 정상 세션의 rehab-like activation 비율이 15%를 초과합니다."
                    )
                }
                val scapularItems = normalItems.filter(ProgramSkeletonItem::scapularStabilityExposure)
                val rehabScapularItems = scapularItems.filter(ProgramSkeletonItem::rehabLikeActivation)
                if (scapularItems.size >= 3 && rehabScapularItems.size * 2 > scapularItems.size) {
                    issues += issue(
                        "FOUR_WEEK_SCAPULAR_REHAB_DOMINANCE",
                        ProgramValidationSeverity.WARNING,
                        "${window.first().weekIndex}-${window.last().weekIndex}주 견갑 안정성 노출의 과반이 저부하 rehab activation입니다."
                    )
                }
            }
        }
    }

    private fun validateMovementCoverage(
        result: GeneratedProgramSkeleton,
        issues: MutableList<ProgramValidationIssue>
    ) {
        if (result.weekPlans.size < 2) return
        val coveragePolicy = CoverageAccountingPolicy.DEFAULT
        val required = linkedMapOf<String, CoverageRequirement>(
            "squat/single-leg" to CoverageRequirement(
                slots = setOf(ProgramSlotId.LOWER_SQUAT_PATTERN, ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL),
                legacyTokens = arrayOf("SQUAT", "LUNGE", "SINGLE_LEG", "STEP_UP")
            ),
            "hinge" to CoverageRequirement(
                slots = setOf(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN),
                legacyTokens = arrayOf("HINGE", "DEADLIFT", "POSTERIOR_CHAIN")
            ),
            "upper pull" to CoverageRequirement(
                slots = setOf(ProgramSlotId.UPPER_PULL_ANCHOR),
                legacyTokens = arrayOf("PULL", "ROW", "LAT")
            ),
            "trunk" to CoverageRequirement(
                slots = setOf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY),
                legacyTokens = arrayOf("CORE", "TRUNK", "ANTI_ROTATION")
            ),
            "scapular" to CoverageRequirement(
                slots = setOf(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT),
                legacyTokens = arrayOf("SCAP", "ROTATOR_CUFF", "SHOULDER_PREHAB")
            ),
            "footwork/COD" to CoverageRequirement(
                slots = setOf(ProgramSlotId.BADMINTON_FOOTWORK_REACTION, ProgramSlotId.BADMINTON_DECEL_COD),
                legacyTokens = arrayOf("FOOTWORK", "CHANGE_OF_DIRECTION", "DECELERATION", "REACTION")
            ),
            "calf/ankle" to CoverageRequirement(
                slots = setOf(ProgramSlotId.CALF_ANKLE_CAPACITY),
                legacyTokens = arrayOf("CALF", "ANKLE", "ACHILLES")
            )
        )
        result.weekPlans.windowed(2).forEach { window ->
            val weekNumbers = window.map(ProgramWeekPlan::weekIndex).toSet()
            val items = result.items.filter { it.weekNumber in weekNumbers }
            val missing = required.filterValues { requirement ->
                items.none { item ->
                    val profile = coveragePolicy.profile(item)
                    val structuredCredit = requirement.slots.maxOf { slot ->
                        coveragePolicy.credit(profile, slot).value
                    }
                    structuredCredit >= CoverageCredit.PARTIAL.value ||
                        (profile.source == SlotCapabilitySource.NONE && item.hasMetadataToken(*requirement.legacyTokens))
                }
            }.keys
            if (missing.isNotEmpty()) {
                issues += issue(
                    "TWO_WEEK_MOVEMENT_COVERAGE",
                    if (result.request.availableDaysPerWeek >= 3) {
                        ProgramValidationSeverity.WARNING
                    } else {
                        ProgramValidationSeverity.SOFT_PENALTY
                    },
                    "${window.first().weekIndex}-${window.last().weekIndex}주 미노출 패턴: ${missing.joinToString()}"
                )
            }
        }
    }

    private data class CoverageRequirement(
        val slots: Set<ProgramSlotId>,
        val legacyTokens: Array<String>
    )

    private fun validateFourWeekDistribution(
        result: GeneratedProgramSkeleton,
        issues: MutableList<ProgramValidationIssue>
    ) {
        if (result.weekPlans.size < 4) return
        result.weekPlans.windowed(4).forEach { window ->
            val weekNumbers = window.map(ProgramWeekPlan::weekIndex).toSet()
            val items = result.items.filter { it.weekNumber in weekNumbers }
            val label = "${window.first().weekIndex}-${window.last().weekIndex}주"
            val movementFamilies = items.map(ProgramSkeletonItem::movementFamily).validDistinct()
            val strengthGroups = items.map(ProgramSkeletonItem::strengthProgressionGroup).validDistinct()
            val transferGroups = items.filter { it.badmintonTransferLevel in setOf("DIRECT", "SUPPORTIVE") }
                .map(ProgramSkeletonItem::redundancyGroup)
                .validDistinct()
            val expectedFamilies = if (result.request.availableDaysPerWeek >= 3) 6 else 3
            if (movementFamilies.size < expectedFamilies) {
                issues += issue(
                    "FOUR_WEEK_MOVEMENT_DISTRIBUTION",
                    ProgramValidationSeverity.SOFT_PENALTY,
                    "$label movement family가 ${movementFamilies.size}개로 제한적입니다."
                )
            }
            if (strengthGroups.size < 3) {
                issues += issue(
                    "FOUR_WEEK_STRENGTH_DISTRIBUTION",
                    ProgramValidationSeverity.SOFT_PENALTY,
                    "$label strength progression group가 ${strengthGroups.size}개로 제한적입니다."
                )
            }
            if (result.request.badmintonTransferRatio >= 0.4 && transferGroups.size < 2) {
                issues += issue(
                    "FOUR_WEEK_TRANSFER_DISTRIBUTION",
                    ProgramValidationSeverity.WARNING,
                    "$label 배드민턴 전이 중복군이 ${transferGroups.size}개뿐입니다."
                )
            }
            val highStressCount = items.count { it.stressMagnitudeHint in setOf("HIGH", "VERY_HIGH") }
            if (items.isNotEmpty() && highStressCount.toDouble() / items.size > 0.45) {
                issues += issue(
                    "FOUR_WEEK_STRESS_DISTRIBUTION",
                    ProgramValidationSeverity.WARNING,
                    "$label HIGH/VERY_HIGH 항목 비율이 45%를 초과합니다."
                )
            }
            val accessories = items.filter { it.selectionRole in setOf("ACCESSORY", "PREHAB", "TRANSFER") }
            if (accessories.size >= 4 && accessories.map(ProgramSkeletonItem::stableKey).validDistinct().size < 2) {
                issues += issue(
                    "FOUR_WEEK_ACCESSORY_ROTATION",
                    ProgramValidationSeverity.WARNING,
                    "$label accessory/prehab/transfer rotation이 부족합니다."
                )
            }
            val anchorContinuity = items.filter { it.selectionRole == "ANCHOR" }
                .groupBy(ProgramSkeletonItem::stableKey)
                .values
                .any { rows -> rows.map(ProgramSkeletonItem::weekNumber).distinct().size >= 2 }
            if (items.any { it.selectionRole == "ANCHOR" } && !anchorContinuity) {
                issues += issue(
                    "FOUR_WEEK_ANCHOR_CONTINUITY",
                    ProgramValidationSeverity.SOFT_PENALTY,
                    "$label anchor가 최소 2주 연속 노출되지 않아 진행성 추적이 어렵습니다."
                )
            }
        }
    }

    private fun rollingAxisLoad(
        items: List<ProgramSkeletonItem>,
        level: (ProgramSkeletonItem) -> String
    ): Double = items.groupBy { it.weekNumber to it.dayOfWeek }.values.sumOf { sessionItems ->
        val loads = sessionItems.map { item ->
            val levelWeight = when (level(item)) {
                "LOW" -> 0.25
                "MODERATE" -> 0.55
                "HIGH" -> 0.85
                "VERY_HIGH" -> 1.0
                else -> 0.0
            }
            val intensityWeight = when (item.dayIntensity) {
                ProgramDayIntensity.HARD.name -> 1.15
                ProgramDayIntensity.LIGHT.name -> 0.75
                else -> 1.0
            }
            levelWeight * intensityWeight * (item.setCount / 3.0).coerceIn(0.5, 1.5)
        }.sortedDescending()
        loads.firstOrNull().orZero() + loads.drop(1).sum() * 0.25
    }

    private fun ProgramSkeletonItem.hasMetadataToken(vararg needles: String): Boolean {
        val source = listOf(
            movementFamily,
            movementSubtype,
            metadataProgramSlot,
            redundancyGroup,
            strengthProgressionGroup,
            primaryStressProfile
        ).joinToString("|").uppercase()
        return needles.any(source::contains)
    }

    private fun ProgramSkeletonItem.isRecoveryContext(
        result: GeneratedProgramSkeleton,
        gate: ProgramFatigueGate?
    ): Boolean {
        if (gate?.band == ProgramFatigueBand.RED) return true
        val week = result.weekPlans.firstOrNull { it.weekIndex == weekNumber }
        return week?.deloadFlag == true || trainingSlot in RECOVERY_SLOT_NAMES
    }

    private fun Iterable<String>.validDistinct(): Set<String> =
        filter { it.isNotBlank() && it != "NONE" && it != "NOT_APPLICABLE" }.toSet()

    private fun fallbackDuration(item: ProgramSkeletonItem): Int {
        val workPerSet = if (item.seconds > 0) item.seconds else max(30, item.reps * 4)
        return 45 + item.setCount * workPerSet +
            (item.setCount - 1).coerceAtLeast(0) * item.restSeconds
    }

    private fun warmupReserveSeconds(minutes: Int): Int = when {
        minutes <= 30 -> 5 * 60
        minutes <= 60 -> 8 * 60
        else -> 10 * 60
    }

    private fun issue(
        code: String,
        severity: ProgramValidationSeverity,
        message: String
    ) = ProgramValidationIssue(code, severity, message)

    private fun Double?.orZero(): Double = this ?: 0.0
    private fun format(value: Double): String = "%.1f".format(value)

    private val RECOVERY_SLOT_NAMES = setOf(
        ProgramTrainingSlot.RECOVERY_PREHAB.name,
        ProgramTrainingSlot.RECOVERY_WEAKPOINT.name,
        ProgramTrainingSlot.MICRO_RECOVERY.name
    )
}
