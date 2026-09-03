package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.data.ProgramGoal
import kotlin.math.pow
import kotlin.math.sqrt

class AthletePlanningStateBuilder(
    private val styleAnalyzer: StrengthProgrammingStyleAnalyzer = StrengthProgrammingStyleAnalyzer()
) {
    fun build(snapshot: PlanningHistorySnapshot, answers: PersonalizedPlanningAnswers): AthletePlanningState {
        val recentStart = snapshot.cutoff.minusDays(27)
        val recent = snapshot.allConfirmedSets.filter { !it.date.isBefore(recentStart) }
        val resistance = recent.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }
        val heavy = resistance.count { it.weightKg > 0 && it.reps in 1..6 && snapshot.movementCoverage(it.stableKey) !in isolationMovements }
        val hypertrophyStimulus = resistance.groupBy { snapshot.movementCoverage(it.stableKey) }.mapValues { (_, rows) ->
            rows.sumOf { row -> snapshot.hypertrophyStimulus(row) }
        }
        val hypertrophy = hypertrophyStimulus.values.sum()
        val heavyRatio = heavy.toDouble() / resistance.size.coerceAtLeast(1)
        val hypertrophyRatio = hypertrophy / resistance.size.coerceAtLeast(1)
        val behavior = when {
            heavyRatio >= .30 && hypertrophyRatio >= .35 -> ObservedTrainingBehavior.MIXED_STRENGTH_HYPERTROPHY
            heavyRatio >= .35 -> ObservedTrainingBehavior.STRENGTH_DOMINANT
            hypertrophyRatio >= .60 && heavyRatio <= .15 -> ObservedTrainingBehavior.HYPERTROPHY_DOMINANT
            resistance.isNotEmpty() -> ObservedTrainingBehavior.GENERAL_MIXED
            else -> ObservedTrainingBehavior.UNKNOWN
        }
        val heavyWeeks = resistance.filter { it.weightKg > 0 && it.reps in 1..6 }
            .map { it.date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) to it.date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) }.distinct().size
        val exposure = when {
            heavyRatio >= .15 || heavyWeeks >= 3 -> StrengthExposure.PRESENT
            heavy > 0 -> StrengthExposure.LOW
            resistance.isNotEmpty() -> StrengthExposure.ABSENT
            else -> StrengthExposure.UNKNOWN
        }
        val savedStrength = snapshot.preferences.strengthIntent
        val answeredStrength = answers.values[QUESTION_STRENGTH_INTENT]?.let { runCatching { StrengthIntent.valueOf(it) }.getOrNull() }
        val strengthIntent = answeredStrength ?: savedStrength ?: when {
            snapshot.profilePrimaryGoal.contains("STRENGTH", true) -> StrengthIntent.STRENGTH_PRIORITY
            behavior == ObservedTrainingBehavior.MIXED_STRENGTH_HYPERTROPHY && exposure == StrengthExposure.PRESENT -> StrengthIntent.MIXED
            else -> StrengthIntent.UNRESOLVED
        }
        val structuredSessions = snapshot.allConfirmedSets.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.STRUCTURED_BADMINTON_DRILL }.map { it.date to it.stableKey }.distinct().size
        val answeredBadminton = answers.values[QUESTION_BADMINTON_INTENT]?.let { runCatching { BadmintonPlanningIntent.valueOf(it) }.getOrNull() }
        val badmintonIntent = answeredBadminton ?: snapshot.preferences.badmintonIntent ?: when {
            snapshot.profilePrimaryGoal.contains("BADMINTON", true) || snapshot.badmintonTrainingYears > 0 -> BadmintonPlanningIntent.ENABLED
            else -> BadmintonPlanningIntent.UNRESOLVED
        }
        val answeredFreeWeight = answers.values[QUESTION_FREE_WEIGHT]?.let { runCatching { FreeWeightWillingness.valueOf(it) }.getOrNull() }
        val freeWeight = answeredFreeWeight ?: snapshot.preferences.freeWeightWillingness ?: FreeWeightWillingness.UNRESOLVED
        val machineRatio = resistance.count { snapshot.isMachine(it.stableKey) }.toDouble() / resistance.size.coerceAtLeast(1)
        val freeRatio = resistance.count { snapshot.isFreeWeight(it.stableKey) }.toDouble() / resistance.size.coerceAtLeast(1)
        val anchors = anchors(snapshot)
        val style = styleAnalyzer.analyze(snapshot, anchors.map(UserAnchor::stableKey).toSet())
        val weeklyCounts = snapshot.allConfirmedSets.filterNot { snapshot.isSportSession(it.stableKey) }
            .groupBy { it.date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) to it.date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) }
            .values.map { rows -> rows.map(PlanningSetRecord::date).distinct().size.toDouble() }
        val typicalDays = weeklyCounts.medianOr(3.0)
        val volatility = if (weeklyCounts.size < 2) 0.0 else sqrt(weeklyCounts.sumOf { (it - weeklyCounts.average()).pow(2) } / weeklyCounts.size)
        val profileGoal = normalizeProfileGoal(snapshot.profilePrimaryGoal)
        val primary = primaryAdaptation(profileGoal, strengthIntent, badmintonIntent)
        val objectiveGaps = badmintonGaps(snapshot)
        val confidence = when {
            snapshot.historyDays >= 42 && anchors.size >= 4 -> PlanningConfidence.HIGH
            snapshot.historyDays >= 14 && anchors.isNotEmpty() -> PlanningConfidence.MODERATE
            else -> PlanningConfidence.LOW
        }
        return AthletePlanningState(
            observedBehavior = behavior,
            strengthExposure = exposure,
            strengthIntent = strengthIntent,
            badmintonIntent = badmintonIntent,
            freeWeightWillingness = freeWeight,
            primaryAdaptation = primary,
            historyDays = snapshot.historyDays,
            recentTrainingDaysPerWeek = typicalDays,
            scheduleVolatility = volatility,
            machineSetRatio = machineRatio,
            freeWeightSetRatio = freeRatio,
            anchors = anchors,
            observedStrengthStyle = style.first,
            observedStyleConfidence = style.second,
            structuredBadmintonSessions = structuredSessions,
            recoveryConstraint = recoveryConstraint(snapshot.recoverySignals),
            confidence = confidence,
            profileGoal = profileGoal,
            programGoal = mapProgramGoal(profileGoal),
            objectiveExposure = snapshot.objectiveExposure,
            objectiveDropGaps = objectiveGaps.first,
            objectiveDevelopmentalGaps = objectiveGaps.second,
            genericCourtLoad = snapshot.genericCourtLoad,
            recoverySignals = snapshot.recoverySignals,
            hypertrophyStimulusByMovement = hypertrophyStimulus
        )
    }

    private fun anchors(snapshot: PlanningHistorySnapshot): List<UserAnchor> {
        val start = snapshot.cutoff.minusDays(55)
        return snapshot.allConfirmedSets.asSequence()
            .filter { !it.date.isBefore(start) && snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }
            .groupBy(PlanningSetRecord::stableKey)
            .mapNotNull { (key, rows) ->
                val sessions = rows.map(PlanningSetRecord::date).distinct().size
                if (sessions < 2 || snapshot.metadata[key]?.planningEligibility !in setOf("PROGRAM_SELECTABLE", "SELECTABLE")) return@mapNotNull null
                val signal = snapshot.canonicalStrengthSignals[key]
                val change = signal?.posteriorChangePercent ?: 0.0
                val response = when { change >= 4 -> "STRONG_POSITIVE"; change >= 1.5 -> "POSITIVE"; change <= -2 -> "NEGATIVE"; else -> "STABLE" }
                val perAnchorStyle = styleAnalyzer.analyze(snapshot, setOf(key))
                UserAnchor(key, rows.maxBy(PlanningSetRecord::date).exerciseName, sessions, rows.size, snapshot.movementCoverage(key).name, snapshot.metadata[key]?.progressMetricType.orEmpty(), response, sessions * 2.0 + rows.size * .15 + when (response) { "STRONG_POSITIVE" -> 4; "POSITIVE" -> 3; "NEGATIVE" -> -3; else -> 1 }, perAnchorStyle.first, perAnchorStyle.second, signal?.source ?: "CANONICAL_SIGNAL_UNAVAILABLE")
            }
            .sortedWith(compareByDescending<UserAnchor> { it.score }.thenBy(UserAnchor::stableKey))
            .groupBy(UserAnchor::movementGroup).values.flatMap { it.take(2) }
            .sortedWith(compareByDescending<UserAnchor> { it.score }.thenBy(UserAnchor::stableKey))
            .take(9)
    }

}

class StrengthProgrammingStyleAnalyzer {
    fun analyze(snapshot: PlanningHistorySnapshot, anchorKeys: Set<String>): Pair<StrengthProgrammingStyle, PlanningConfidence> {
        val rows = snapshot.allConfirmedSets.filter { it.stableKey in anchorKeys }
        val bySession = rows.groupBy { it.stableKey to it.date }
        val byWeek = bySession.entries.groupBy { (key, _) -> Triple(key.first, key.second.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR), key.second.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)) }
        var madcowHits = 0
        var hlmHits = 0
        var dupHits = 0
        byWeek.values.forEach { sessions ->
            val ordered = sessions.sortedBy { it.key.second }.map { it.value.filter { row -> row.weightKg > 0 && row.reps > 0 } }
            if (ordered.size >= 3) {
                val maxima = ordered.mapNotNull { session -> session.maxOfOrNull(PlanningSetRecord::weightKg) }
                if (maxima.size >= 3 && maxima.min() / maxima.max().coerceAtLeast(.01) <= .88) {
                    hlmHits += 1
                    val hasRamp = ordered.any { session -> session.size >= 4 && session.map(PlanningSetRecord::weightKg).distinct().size >= 3 && session.count { it.reps in 4..6 } >= 3 }
                    val hasTripleBackoff = ordered.any { session ->
                        val top = session.maxOfOrNull(PlanningSetRecord::weightKg) ?: 0.0
                        session.any { it.weightKg >= top * .97 && it.reps in 2..4 } && session.any { it.weightKg <= top * .90 && it.reps >= 7 }
                    }
                    if (hasRamp && hasTripleBackoff) madcowHits += 1
                }
            }
            val medians = ordered.mapNotNull { session -> session.map(PlanningSetRecord::reps).sorted().takeIf(List<Int>::isNotEmpty)?.let { it[it.size / 2] } }
            if (medians.size >= 2 && medians.min() <= 5 && medians.max() >= 8) dupHits += 1
        }
        if (madcowHits >= 2) return StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING to PlanningConfidence.HIGH
        if (hlmHits >= 3) return StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM to PlanningConfidence.MODERATE
        if (dupHits >= 3) return StrengthProgrammingStyle.DUP_LIKE_UNDULATING to PlanningConfidence.MODERATE
        val sessions = bySession.values.mapNotNull(::classifySession)
        if (sessions.isEmpty()) return StrengthProgrammingStyle.UNRESOLVED to PlanningConfidence.LOW
        val dominant = sessions.groupingBy { it }.eachCount().maxBy { it.value }
        val confidence = if (dominant.value >= 4 && dominant.value.toDouble() / sessions.size >= .60) PlanningConfidence.HIGH else if (dominant.value >= 3) PlanningConfidence.MODERATE else PlanningConfidence.LOW
        return dominant.key to confidence
    }

    fun classifySession(rows: List<PlanningSetRecord>): StrengthProgrammingStyle? {
        val work = rows.filter { it.weightKg > 0 && it.reps > 0 }.sortedBy(PlanningSetRecord::setIndex)
        if (work.isEmpty()) return null
        val loads = work.map(PlanningSetRecord::weightKg)
        val sameLoad = (loads.max() - loads.min()) / loads.max() <= .03
        if (work.size == 5 && sameLoad && work.all { it.reps == 5 }) return StrengthProgrammingStyle.STRAIGHT_5X5
        if (work.size in 3..6 && sameLoad && work.map { it.reps }.sorted()[work.size / 2] <= 6) return StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS
        val max = loads.max()
        val topIndex = loads.indexOf(max)
        val top = work[topIndex]
        val backoffs = work.drop(topIndex + 1).filter { it.weightKg in (max * .70)..(max * .94) }
        if (backoffs.size >= 2 && (top.rpe == null || top.rpe >= 7.5)) return StrengthProgrammingStyle.TOP_SET_BACKOFF
        if (top.reps in 6..15 && backoffs.isEmpty() && (top.rpe == null || top.rpe >= 8.0)) return StrengthProgrammingStyle.TOP_SET_HYPERTROPHY
        return null
    }
}

internal fun PlanningHistorySnapshot.activityKind(key: String): PlannedActivityKind {
    val meta = metadata[key]
    if (key in setOf("ex_ae9ecdbc", "ex_badminton_lesson") || meta?.activityKind == "SPORT_SESSION") {
        return PlannedActivityKind.GENERIC_COURT_SESSION
    }
    if (meta?.activityKind == "EXERCISE" && meta.programSlot == "BADMINTON_FOOTWORK" &&
        "BADMINTON_TRANSFER" in meta.analysisEligibility && meta.badmintonTransferLevel == "DIRECT") {
        return PlannedActivityKind.STRUCTURED_BADMINTON_DRILL
    }
    return if (meta?.activityKind == "EXERCISE") PlannedActivityKind.RESISTANCE else PlannedActivityKind.OTHER
}

internal fun PlanningHistorySnapshot.isSportSession(key: String): Boolean =
    activityKind(key) == PlannedActivityKind.GENERIC_COURT_SESSION

internal fun PlanningHistorySnapshot.isStructuredBadminton(key: String): Boolean =
    activityKind(key) == PlannedActivityKind.STRUCTURED_BADMINTON_DRILL

private fun PlanningHistorySnapshot.isMachine(key: String): Boolean = metadata[key]?.let { it.progressMetricType.startsWith("MACHINE_") || it.programSlot.contains("MACHINE") } == true || exercises[key]?.equipmentTags.orEmpty().contains("MACHINE")

internal fun PlanningHistorySnapshot.isFreeWeight(key: String): Boolean = exercises[key]?.equipmentTags.orEmpty().split('|').any { it in setOf("BARBELL", "DUMBBELL", "KETTLEBELL", "EZ_BAR") }

internal fun PlanningHistorySnapshot.movementCoverage(key: String): MovementCoverage = when (metadata[key]?.programSlot) {
    "MAIN_LOWER_STRENGTH", "MACHINE_LOWER_BODY_STRENGTH", "UNILATERAL_LOWER_STRENGTH",
    "UNILATERAL_LOWER_ACCESSORY", "KNEE_DOMINANT_ACCESSORY", "QUAD_ISOLATION_ACCESSORY",
    "BODYWEIGHT_LOWER_PATTERN", "LOWER_BODY_ISOMETRIC_ACCESSORY" -> MovementCoverage.LOWER_KNEE
    "MAIN_HINGE_STRENGTH", "POSTERIOR_CHAIN_ACCESSORY", "POSTERIOR_CHAIN_CONTROL_ACCESSORY",
    "HAMSTRING_ISOLATION_ACCESSORY", "GLUTE_POSTERIOR_CHAIN_ACCESSORY", "UNILATERAL_HINGE_ACCESSORY",
    "GLUTE_ACCESSORY", "BALLISTIC_HINGE_POWER" -> MovementCoverage.POSTERIOR_CHAIN
    "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY", "UPPER_PUSH_STRENGTH", "UPPER_PUSH_COMPOUND",
    "UPPER_PUSH_ACCESSORY", "CHEST_ISOLATION_ACCESSORY", "TRICEPS_EMPHASIS_COMPOUND_PUSH" -> MovementCoverage.HORIZONTAL_PUSH
    "HORIZONTAL_PULL_STRENGTH", "REAR_DELT_SCAPULAR_ACCESSORY" -> MovementCoverage.HORIZONTAL_PULL
    "VERTICAL_PULL_STRENGTH", "LAT_ACCESSORY", "PULLOVER_ACCESSORY" -> MovementCoverage.VERTICAL_PULL
    "OVERHEAD_PUSH_STRENGTH_OR_ACCESSORY", "BODYWEIGHT_VERTICAL_PUSH", "UNILATERAL_SHOULDER_PRESS",
    "SHOULDER_ACCESSORY" -> MovementCoverage.VERTICAL_PUSH
    "CORE_STABILITY_ACCESSORY", "CORE_FLEXION_ACCESSORY", "TRUNK_ANTI_ROTATION_STABILITY",
    "CORE_STABILITY", "CONDITIONING_CORE", "LOADED_CARRY_BRACING" -> MovementCoverage.CORE_DIRECT
    "ANKLE_CALF_SUPPORT", "ANKLE_SSC_CONDITIONING" -> MovementCoverage.CALVES
    "BICEPS_ACCESSORY", "BRACHIALIS_FOREARM_ACCESSORY" -> MovementCoverage.ARMS_BICEPS
    "TRICEPS_ACCESSORY" -> MovementCoverage.ARMS_TRICEPS
    else -> MovementCoverage.OTHER
}

internal fun PlanningHistorySnapshot.movementGroup(key: String): String = movementCoverage(key).name

internal fun PlanningHistorySnapshot.hypertrophyStimulus(row: PlanningSetRecord): Double {
    val meta = metadata[row.stableKey] ?: return 0.0
    if ("HYPERTROPHY_VOLUME" !in meta.analysisEligibility || row.reps !in 5..30) return 0.0
    val effort = when (row.rpe) {
        null -> 0.75
        in 9.0..10.0 -> 1.0
        in 7.0..<9.0 -> 0.85
        in 5.0..<7.0 -> 0.55
        else -> 0.30
    }
    val contribution = if (movementCoverage(row.stableKey) in isolationMovements) 1.0 else 0.75
    return effort * contribution
}

internal val isolationMovements = setOf(MovementCoverage.ARMS_BICEPS, MovementCoverage.ARMS_TRICEPS, MovementCoverage.CALVES)

private fun normalizeProfileGoal(raw: String): String = raw.takeIf {
    it in setOf("BADMINTON_PERFORMANCE", "STRENGTH_GAIN", "STRENGTH_MAINTENANCE", "HYPERTROPHY_PHYSIQUE", "RECOVERY_INJURY_PREVENTION", "WEIGHT_MANAGEMENT", "MIXED")
} ?: "MIXED"

internal fun mapProgramGoal(profileGoal: String): ProgramGoal = when (profileGoal) {
    "BADMINTON_PERFORMANCE" -> ProgramGoal.BADMINTON_SUPPORT
    "STRENGTH_GAIN", "STRENGTH_MAINTENANCE" -> ProgramGoal.STRENGTH
    "HYPERTROPHY_PHYSIQUE" -> ProgramGoal.BODYBUILDING
    "RECOVERY_INJURY_PREVENTION", "WEIGHT_MANAGEMENT", "MIXED" -> ProgramGoal.FUNCTIONAL_CONDITIONING
    else -> ProgramGoal.FUNCTIONAL_CONDITIONING
}

private fun primaryAdaptation(goal: String, strength: StrengthIntent, badminton: BadmintonPlanningIntent): String = when (goal) {
    "BADMINTON_PERFORMANCE" -> "BADMINTON_SUPPORT"
    "HYPERTROPHY_PHYSIQUE" -> if (strength == StrengthIntent.MIXED) "HYPERTROPHY_STRENGTH" else "HYPERTROPHY"
    "STRENGTH_GAIN" -> "STRENGTH_SUPPORT"
    "STRENGTH_MAINTENANCE" -> "STRENGTH_MAINTENANCE"
    "RECOVERY_INJURY_PREVENTION" -> "RECOVERY_FOUNDATION"
    "WEIGHT_MANAGEMENT" -> "CONDITIONING_FOUNDATION"
    else -> when {
        badminton == BadmintonPlanningIntent.ENABLED -> "BADMINTON_SUPPORT"
        strength == StrengthIntent.HYPERTROPHY_PRIORITY -> "HYPERTROPHY"
        strength == StrengthIntent.MIXED -> "HYPERTROPHY_STRENGTH"
        else -> "GENERAL_FOUNDATION"
    }
}

private fun badmintonGaps(snapshot: PlanningHistorySnapshot): Pair<Set<String>, Set<String>> {
    val recent = snapshot.allConfirmedSets.filter { !it.date.isBefore(snapshot.cutoff.minusDays(27)) }
    val prior = snapshot.allConfirmedSets.filter { it.date.isBefore(snapshot.cutoff.minusDays(27)) }
    fun exposure(rows: List<PlanningSetRecord>) = BadmintonObjective.entries.associate { objective ->
        objective.name to rows.sumOf { row -> snapshot.badmintonObjectives[row.stableKey]?.get(objective.name) ?: 0.0 }
    }
    val current = exposure(recent)
    val historical = exposure(prior)
    val drop = current.filter { (objective, value) -> value == 0.0 && historical.getValue(objective) > 0.0 }.keys
    val developmental = current.filterValues { it == 0.0 }.keys - drop
    return drop to developmental
}

private fun recoveryConstraint(signals: PlanningRecoverySignals): String = when {
    signals.readinessStatus == "LIMITED" || signals.tissueStatus == "VERY_HIGH" -> "회복/조직 신호가 제한 상태이므로 부하 증가를 금지하고 검토가 필요합니다."
    signals.isConstrained -> "회복/조직 신호가 주의 상태이므로 빈도와 세션 밀도를 보수적으로 유지합니다."
    signals.readinessStatus == "UNKNOWN" -> "회복 신호가 없어 부하 증가는 수행 확인 뒤에만 허용합니다."
    else -> "프로덕션 회복 신호가 안정 범위입니다."
}

private fun List<Double>.medianOr(default: Double): Double = if (isEmpty()) default else sorted().let { if (size % 2 == 1) it[size / 2] else (it[size / 2 - 1] + it[size / 2]) / 2 }

internal const val QUESTION_STRENGTH_INTENT = "STRENGTH_INTENT"
internal const val QUESTION_BADMINTON_INTENT = "BADMINTON_INTENT"
internal const val QUESTION_FREE_WEIGHT = "FREE_WEIGHT_WILLINGNESS"
