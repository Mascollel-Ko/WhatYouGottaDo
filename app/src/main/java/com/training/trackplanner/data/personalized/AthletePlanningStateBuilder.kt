package com.training.trackplanner.data.personalized

import kotlin.math.pow
import kotlin.math.sqrt

class AthletePlanningStateBuilder(
    private val styleAnalyzer: StrengthProgrammingStyleAnalyzer = StrengthProgrammingStyleAnalyzer()
) {
    fun build(snapshot: PlanningHistorySnapshot, answers: PersonalizedPlanningAnswers): AthletePlanningState {
        val recentStart = snapshot.cutoff.minusDays(27)
        val recent = snapshot.allConfirmedSets.filter { !it.date.isBefore(recentStart) }
        val resistance = recent.filterNot { snapshot.isSportSession(it.stableKey) || snapshot.isStructuredBadminton(it.stableKey) }
        val heavy = resistance.count { it.weightKg > 0 && it.reps in 1..6 && !snapshot.movementGroup(it.stableKey).contains("ISOLATION") }
        val hypertrophy = resistance.count { it.reps in 6..20 && snapshot.metadata[it.stableKey]?.analysisEligibility?.let { tokens -> "HYPERTROPHY_VOLUME" in tokens } == true }
        val heavyRatio = heavy.toDouble() / resistance.size.coerceAtLeast(1)
        val hypertrophyRatio = hypertrophy.toDouble() / resistance.size.coerceAtLeast(1)
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
        val structuredSessions = snapshot.allConfirmedSets.filter { snapshot.isStructuredBadminton(it.stableKey) }.map { it.date to it.stableKey }.distinct().size
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
        val primary = when {
            badmintonIntent == BadmintonPlanningIntent.ENABLED && structuredSessions >= 4 -> "BADMINTON_SUPPORT"
            snapshot.profilePrimaryGoal.contains("HYPERTROPHY", true) || snapshot.profilePrimaryGoal.contains("PHYSIQUE", true) ->
                if (strengthIntent == StrengthIntent.MIXED) "HYPERTROPHY_STRENGTH" else "HYPERTROPHY"
            strengthIntent == StrengthIntent.STRENGTH_PRIORITY -> "STRENGTH_SUPPORT"
            snapshot.strengthTrainingYears < 1.0 -> "GENERAL_FOUNDATION"
            else -> "STRENGTH_SUPPORT"
        }
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
            recoveryConstraint = "CANONICAL_RECOVERY_CONSTRAINTS_REMAIN_AUTHORITATIVE",
            confidence = confidence
        )
    }

    private fun anchors(snapshot: PlanningHistorySnapshot): List<UserAnchor> {
        val start = snapshot.cutoff.minusDays(55)
        return snapshot.allConfirmedSets.asSequence()
            .filter { !it.date.isBefore(start) && !snapshot.isSportSession(it.stableKey) && !snapshot.isStructuredBadminton(it.stableKey) }
            .groupBy(PlanningSetRecord::stableKey)
            .mapNotNull { (key, rows) ->
                val sessions = rows.map(PlanningSetRecord::date).distinct().size
                if (sessions < 2 || snapshot.metadata[key]?.planningEligibility !in setOf("PROGRAM_SELECTABLE", "SELECTABLE")) return@mapNotNull null
                val first = rows.minBy(PlanningSetRecord::date)
                val last = rows.maxBy(PlanningSetRecord::date)
                val firstMetric = performance(first)
                val lastMetric = performance(last)
                val change = if (firstMetric > 0) (lastMetric / firstMetric - 1.0) * 100 else 0.0
                val response = when { change >= 4 -> "STRONG_POSITIVE"; change >= 1.5 -> "POSITIVE"; change <= -2 -> "NEGATIVE"; else -> "STABLE" }
                UserAnchor(key, last.exerciseName, sessions, rows.size, snapshot.movementGroup(key), snapshot.metadata[key]?.progressMetricType.orEmpty(), response, sessions * 2.0 + rows.size * .15 + when (response) { "STRONG_POSITIVE" -> 4; "POSITIVE" -> 3; "NEGATIVE" -> -3; else -> 1 })
            }
            .sortedWith(compareByDescending<UserAnchor> { it.score }.thenBy(UserAnchor::stableKey))
            .groupBy(UserAnchor::movementGroup).values.flatMap { it.take(2) }
            .sortedWith(compareByDescending<UserAnchor> { it.score }.thenBy(UserAnchor::stableKey))
            .take(9)
    }

    private fun performance(set: PlanningSetRecord): Double = when {
        set.weightKg > 0 && set.reps > 0 -> set.weightKg * (1.0 + set.reps / 30.0)
        set.seconds > 0 -> set.seconds.toDouble()
        else -> set.reps.toDouble()
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

internal fun PlanningHistorySnapshot.isSportSession(key: String): Boolean =
    key in setOf("ex_ae9ecdbc", "ex_badminton_lesson") || metadata[key]?.activityKind == "SPORT_SESSION"

internal fun PlanningHistorySnapshot.isStructuredBadminton(key: String): Boolean = !isSportSession(key) && badmintonObjectives[key].orEmpty().isNotEmpty()

private fun PlanningHistorySnapshot.isMachine(key: String): Boolean = metadata[key]?.let { it.progressMetricType.startsWith("MACHINE_") || it.programSlot.contains("MACHINE") } == true || exercises[key]?.equipmentTags.orEmpty().contains("MACHINE")

private fun PlanningHistorySnapshot.isFreeWeight(key: String): Boolean = exercises[key]?.equipmentTags.orEmpty().split('|').any { it in setOf("BARBELL", "DUMBBELL", "KETTLEBELL", "EZ_BAR") }

internal fun PlanningHistorySnapshot.movementGroup(key: String): String {
    val merged = "${metadata[key]?.programSlot}|${metadata[key]?.strengthProgressionGroup}".uppercase()
    return when {
        listOf("HINGE", "HAMSTRING", "POSTERIOR_CHAIN", "DEADLIFT").any(merged::contains) -> "POSTERIOR_CHAIN"
        listOf("SQUAT", "LEG_PRESS", "LUNGE", "KNEE_DOMINANT").any(merged::contains) -> "LOWER_KNEE"
        listOf("HORIZONTAL_PUSH", "CHEST_PRESS").any(merged::contains) -> "HORIZONTAL_PUSH"
        listOf("HORIZONTAL_PULL", "ROW").any(merged::contains) -> "HORIZONTAL_PULL"
        listOf("VERTICAL_PULL", "LAT_PULL").any(merged::contains) -> "VERTICAL_PULL"
        listOf("VERTICAL_PUSH", "OVERHEAD", "SHOULDER").any(merged::contains) -> "VERTICAL_PUSH"
        listOf("CORE", "TRUNK", "ANTI_ROTATION").any(merged::contains) -> "CORE_DIRECT"
        listOf("CALF", "ANKLE").any(merged::contains) -> "CALVES"
        listOf("BICEP", "ELBOW_FLEX", "CURL").any(merged::contains) -> "ARMS_BICEPS_ISOLATION"
        listOf("TRICEP", "ELBOW_EXTENSION").any(merged::contains) -> "ARMS_TRICEPS_ISOLATION"
        else -> "OTHER"
    }
}

private fun List<Double>.medianOr(default: Double): Double = if (isEmpty()) default else sorted().let { if (size % 2 == 1) it[size / 2] else (it[size / 2 - 1] + it[size / 2]) / 2 }

internal const val QUESTION_STRENGTH_INTENT = "STRENGTH_INTENT"
internal const val QUESTION_BADMINTON_INTENT = "BADMINTON_INTENT"
internal const val QUESTION_FREE_WEIGHT = "FREE_WEIGHT_WILLINGNESS"
