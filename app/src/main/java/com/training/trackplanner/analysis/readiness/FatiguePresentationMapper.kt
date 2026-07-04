package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.analysis.fatigue.FatigueThresholds
import kotlin.math.roundToInt

class FatiguePresentationMapper {
    fun map(pressure: FatiguePressureSnapshot): FatiguePresentationSnapshot {
        val categoryScores = FatigueCategoryKey.entries.associateWith { category ->
            score(pressure.categoryPressures[category])
        }
        val bodyPartScores = pressure.bodyPartPressures.mapValues { (_, item) -> score(item) }

        val neuralHeavyScore = categoryScores.score(FatigueCategoryKey.NEURAL_HEAVY)
        val neuralSpeedScore = categoryScores.score(FatigueCategoryKey.NEURAL_SPEED)
        val localCategoryScore = categoryScores.score(FatigueCategoryKey.LOCAL_MUSCLE)
        val decelerationScore = categoryScores.score(FatigueCategoryKey.DECELERATION)
        val elasticSscScore = categoryScores.score(FatigueCategoryKey.ELASTIC_SSC)
        val overheadScore = categoryScores.score(FatigueCategoryKey.OVERHEAD_REPETITION)
        val gripCategoryScore = categoryScores.score(FatigueCategoryKey.GRIP_FOREARM)
        val systemicCategoryScore = categoryScores.score(FatigueCategoryKey.SYSTEMIC)
        val badmintonCourtScore = categoryScores.score(FatigueCategoryKey.BADMINTON_COURT)

        val lowerBodyScore = bodyPartScores.maxScore(
            "quads",
            "hamstrings",
            "glutes",
            "erectors_low_back",
            "calves_achilles",
            "hips_adductors_abductors"
        )
        val landingBodyPartScore = bodyPartScores.maxScore("calves_achilles", "quads", "glutes")
        val shoulderScore = bodyPartScores.maxScore("shoulders", "rotator_cuff")
        val upperPushBodyScore = bodyPartScores.maxScore("chest", "shoulders", "rotator_cuff", "elbow_extensors")
        val gripBodyScore = bodyPartScores.maxScore("forearm_grip", "elbow_flexors", "elbow_extensors")

        val neuralScore = combineAxisScores(neuralHeavyScore, neuralSpeedScore)
        val localMuscleScore = maxOf(localCategoryScore, (lowerBodyScore * SUPPORTING_BODY_PART_WEIGHT).roundToInt())
            .clampScore()
        val jointTendonScore = maxOf(
            combineAxisScores(decelerationScore, elasticSscScore, overheadScore, gripCategoryScore),
            (maxOf(landingBodyPartScore, shoulderScore, gripBodyScore) * SUPPORTING_BODY_PART_WEIGHT).roundToInt()
        ).clampScore()
        val systemicScore = maxOf(
            systemicCategoryScore,
            (badmintonCourtScore * BADMINTON_SYSTEMIC_SUPPORT_WEIGHT).roundToInt()
        ).clampScore()
        val focusScore = combineAxisScores(neuralSpeedScore, badmintonCourtScore)

        // Beta heuristic: overall blends average pressure, worst axis, and a small high-axis boost.
        // These weights are transparent tuning defaults, not research-derived constants.
        val axisScores = listOf(neuralScore, localMuscleScore, jointTendonScore, systemicScore, focusScore)
        val meanScore = axisScores.average()
        val maxScore = axisScores.maxOrNull() ?: 0
        val highAxisBoost = axisScores.count { score -> score >= RESTRICTED_SCORE } * 4 +
            axisScores.count { score -> score >= VERY_HIGH_SCORE } * 3
        val overallScore = (meanScore * 0.45 + maxScore * 0.45 + highAxisBoost).roundToInt().clampScore()

        val heavyLowerRestricted = neuralHeavyScore >= RESTRICTED_SCORE ||
            neuralScore >= RESTRICTED_SCORE ||
            lowerBodyScore >= RESTRICTED_SCORE
        val highImpactRestricted = decelerationScore >= RESTRICTED_SCORE ||
            elasticSscScore >= RESTRICTED_SCORE ||
            landingBodyPartScore >= RESTRICTED_SCORE
        val codReactiveRestricted = decelerationScore >= RESTRICTED_SCORE ||
            elasticSscScore >= RESTRICTED_SCORE ||
            neuralSpeedScore >= RESTRICTED_SCORE ||
            badmintonCourtScore >= RESTRICTED_SCORE ||
            landingBodyPartScore >= RESTRICTED_SCORE
        val upperPushRestricted = overheadScore >= RESTRICTED_SCORE ||
            upperPushBodyScore >= RESTRICTED_SCORE
        val overheadRestricted = overheadScore >= RESTRICTED_SCORE ||
            shoulderScore >= RESTRICTED_SCORE
        val gripForearmRestricted = gripCategoryScore >= RESTRICTED_SCORE ||
            gripBodyScore >= RESTRICTED_SCORE

        val reasons = reasons(
            neuralHeavyScore = neuralHeavyScore,
            neuralSpeedScore = neuralSpeedScore,
            decelerationScore = decelerationScore,
            elasticSscScore = elasticSscScore,
            overheadScore = overheadScore,
            gripCategoryScore = gripCategoryScore,
            systemicCategoryScore = systemicCategoryScore,
            badmintonCourtScore = badmintonCourtScore,
            lowerBodyScore = lowerBodyScore,
            landingBodyPartScore = landingBodyPartScore,
            shoulderScore = shoulderScore,
            gripBodyScore = gripBodyScore
        )

        val gate = TrainingGateSnapshot(
            overallScore = overallScore,
            heavyLowerRestricted = heavyLowerRestricted,
            highImpactRestricted = highImpactRestricted,
            codReactiveRestricted = codReactiveRestricted,
            upperPushRestricted = upperPushRestricted,
            overheadRestricted = overheadRestricted,
            gripForearmRestricted = gripForearmRestricted,
            volumeFactor = volumeFactor(overallScore),
            rpeCap = rpeCap(overallScore, neuralScore, neuralHeavyScore),
            reasons = reasons
        )

        return FatiguePresentationSnapshot(
            overallScore = overallScore,
            neuralScore = neuralScore,
            localMuscleScore = localMuscleScore,
            jointTendonScore = jointTendonScore,
            systemicScore = systemicScore,
            focusScore = focusScore,
            highCategories = highCategories(pressure, categoryScores),
            highBodyParts = highBodyParts(pressure, bodyPartScores),
            gate = gate,
            reduceToday = reduceToday(gate, reasonsByRestriction(gate, reasons)),
            availableToday = availableToday(gate, overallScore),
            reasons = reasons
        )
    }

    private fun score(item: FatiguePressure?): Int {
        if (item == null || item.currentResidualLoad <= TodayReadinessConstants.LOW_STD_FLOOR) return 0
        val raw = listOfNotNull(
            item.pressure?.let { pressure -> (pressure.coerceIn(0.0, 2.0) * 50.0).roundToInt() },
            item.zScore?.let { zScore -> (zScore.coerceIn(0.0, 2.5) / 2.5 * 100.0).roundToInt() },
            item.percentile?.roundToInt(),
            levelFloor(item.level)
        ).maxOrNull() ?: 0
        return raw
            .coerceAtLeast(levelFloor(item.level))
            .coerceAtMost(levelCap(item.level))
            .clampScore()
    }

    private fun combineAxisScores(vararg scores: Int): Int {
        val positiveScores = scores.filter { score -> score > 0 }
        if (positiveScores.isEmpty()) return 0
        val max = positiveScores.maxOrNull() ?: 0
        val supportingMean = positiveScores
            .filter { score -> score != max }
            .takeIf { values -> values.isNotEmpty() }
            ?.average()
            ?: 0.0
        return (max + supportingMean * SUPPORTING_AXIS_WEIGHT).roundToInt().clampScore()
    }

    private fun highCategories(
        pressure: FatiguePressureSnapshot,
        scores: Map<FatigueCategoryKey, Int>
    ): List<FatigueCategoryPressure> =
        scores.entries
            .mapNotNull { (category, score) ->
                val item = pressure.categoryPressures[category] ?: return@mapNotNull null
                if (score < RESTRICTED_SCORE && item.level < FatigueLevel.HIGH) return@mapNotNull null
                FatigueCategoryPressure(
                    category = category,
                    score = score,
                    level = item.level,
                    pressure = item.pressure
                )
            }
            .sortedByDescending { item -> item.score }

    private fun highBodyParts(
        pressure: FatiguePressureSnapshot,
        scores: Map<String, Int>
    ): List<BodyPartPressure> =
        scores.entries
            .mapNotNull { (part, score) ->
                val item = pressure.bodyPartPressures[part] ?: return@mapNotNull null
                if (score < RESTRICTED_SCORE && item.level < FatigueLevel.HIGH) return@mapNotNull null
                BodyPartPressure(
                    key = part,
                    score = score,
                    level = item.level,
                    pressure = item.pressure
                )
            }
            .sortedByDescending { item -> item.score }

    private fun reasons(
        neuralHeavyScore: Int,
        neuralSpeedScore: Int,
        decelerationScore: Int,
        elasticSscScore: Int,
        overheadScore: Int,
        gripCategoryScore: Int,
        systemicCategoryScore: Int,
        badmintonCourtScore: Int,
        lowerBodyScore: Int,
        landingBodyPartScore: Int,
        shoulderScore: Int,
        gripBodyScore: Int
    ): List<String> {
        val reasons = linkedSetOf<String>()
        if (neuralHeavyScore >= RESTRICTED_SCORE) reasons += "High neural fatigue pressure"
        if (neuralSpeedScore >= RESTRICTED_SCORE) reasons += "High neural speed pressure"
        if (decelerationScore >= RESTRICTED_SCORE) reasons += "High deceleration / change-of-direction load"
        if (elasticSscScore >= RESTRICTED_SCORE) reasons += "High elastic SSC / landing load"
        if (overheadScore >= RESTRICTED_SCORE) reasons += "High overhead repetition pressure"
        if (gripCategoryScore >= RESTRICTED_SCORE) reasons += "High grip / forearm pressure"
        if (systemicCategoryScore >= RESTRICTED_SCORE) reasons += "High systemic fatigue pressure"
        if (badmintonCourtScore >= RESTRICTED_SCORE) reasons += "High badminton court pressure"
        if (lowerBodyScore >= RESTRICTED_SCORE) reasons += "High lower-body pressure"
        if (landingBodyPartScore >= RESTRICTED_SCORE) reasons += "High calves-achilles pressure"
        if (shoulderScore >= RESTRICTED_SCORE) reasons += "High shoulder / rotator cuff pressure"
        if (gripBodyScore >= RESTRICTED_SCORE) reasons += "High grip / forearm pressure"
        return reasons.toList()
    }

    private fun reasonsByRestriction(
        gate: TrainingGateSnapshot,
        reasons: List<String>
    ): Map<String, List<String>> =
        mapOf(
            "heavy_lower" to reasons.filterAny("neural", "lower-body"),
            "high_impact" to reasons.filterAny("deceleration", "elastic SSC", "calves-achilles"),
            "cod_reactive" to reasons.filterAny("neural speed", "deceleration", "elastic SSC", "badminton court", "calves-achilles"),
            "upper_push" to reasons.filterAny("overhead", "shoulder", "rotator cuff"),
            "overhead" to reasons.filterAny("overhead", "shoulder", "rotator cuff"),
            "grip_forearm" to reasons.filterAny("grip", "forearm")
        ).filterKeys { key ->
            when (key) {
                "heavy_lower" -> gate.heavyLowerRestricted
                "high_impact" -> gate.highImpactRestricted
                "cod_reactive" -> gate.codReactiveRestricted
                "upper_push" -> gate.upperPushRestricted
                "overhead" -> gate.overheadRestricted
                "grip_forearm" -> gate.gripForearmRestricted
                else -> false
            }
        }

    private fun reduceToday(
        gate: TrainingGateSnapshot,
        reasonsByRestriction: Map<String, List<String>>
    ): List<FatigueRestriction> =
        listOfNotNull(
            restriction(
                key = "heavy_lower",
                label = "Heavy lower / strength anchor",
                restricted = gate.heavyLowerRestricted,
                score = gate.overallScore,
                reasons = reasonsByRestriction["heavy_lower"].orEmpty()
            ),
            restriction(
                key = "high_impact",
                label = "Jump / landing / high impact",
                restricted = gate.highImpactRestricted,
                score = gate.overallScore,
                reasons = reasonsByRestriction["high_impact"].orEmpty()
            ),
            restriction(
                key = "cod_reactive",
                label = "Random footwork / COD / reactive",
                restricted = gate.codReactiveRestricted,
                score = gate.overallScore,
                reasons = reasonsByRestriction["cod_reactive"].orEmpty()
            ),
            restriction(
                key = "upper_push",
                label = "Upper push support",
                restricted = gate.upperPushRestricted,
                score = gate.overallScore,
                reasons = reasonsByRestriction["upper_push"].orEmpty()
            ),
            restriction(
                key = "overhead",
                label = "Overhead / shoulder load",
                restricted = gate.overheadRestricted,
                score = gate.overallScore,
                reasons = reasonsByRestriction["overhead"].orEmpty()
            ),
            restriction(
                key = "grip_forearm",
                label = "Grip / forearm / elbow load",
                restricted = gate.gripForearmRestricted,
                score = gate.overallScore,
                reasons = reasonsByRestriction["grip_forearm"].orEmpty()
            )
        )

    private fun restriction(
        key: String,
        label: String,
        restricted: Boolean,
        score: Int,
        reasons: List<String>
    ): FatigueRestriction? =
        if (restricted) {
            FatigueRestriction(
                key = key,
                label = label,
                score = score,
                reasons = reasons
            )
        } else {
            null
        }

    private fun availableToday(gate: TrainingGateSnapshot, overallScore: Int): List<FatigueAvailability> {
        val items = mutableListOf<FatigueAvailability>()
        if (overallScore < RESTRICTED_SCORE) {
            items += FatigueAvailability(
                key = "recovery_prehab",
                label = "Recovery / prehab",
                reason = "Low external fatigue cost"
            )
        }
        if (!gate.highImpactRestricted && !gate.codReactiveRestricted) {
            items += FatigueAvailability(
                key = "low_risk_skill",
                label = "Low-risk skill work",
                reason = "Court-impact pressure is not high"
            )
        }
        if (!gate.upperPushRestricted && !gate.gripForearmRestricted) {
            items += FatigueAvailability(
                key = "light_upper_accessory",
                label = "Light upper-body accessory",
                reason = "Upper push and grip pressure are not high"
            )
        }
        if (!gate.heavyLowerRestricted && !gate.highImpactRestricted) {
            items += FatigueAvailability(
                key = "low_impact_lower_accessory",
                label = "Low-impact lower accessory",
                reason = "Lower-body and landing pressure are not high"
            )
        }
        return items
    }

    private fun volumeFactor(overallScore: Int): Double =
        when {
            overallScore >= FatigueThresholds.PRESENTATION_VOLUME_RED_START -> 0.25
            overallScore >= FatigueThresholds.PRESENTATION_VOLUME_ORANGE_START -> 0.50
            overallScore >= FatigueThresholds.PRESENTATION_VOLUME_YELLOW_START -> 0.75
            else -> 1.0
        }

    private fun rpeCap(overallScore: Int, neuralScore: Int, neuralHeavyScore: Int): Int? =
        when {
            neuralScore >= RESTRICTED_SCORE ||
                neuralHeavyScore >= RESTRICTED_SCORE ||
                overallScore >= FatigueThresholds.PRESENTATION_VOLUME_ORANGE_START -> 7
            overallScore >= FatigueThresholds.PRESENTATION_VOLUME_YELLOW_START -> 8
            else -> null
        }

    private fun levelFloor(level: FatigueLevel): Int =
        when (level) {
            FatigueLevel.LOW -> 0
            FatigueLevel.NORMAL -> 35
            FatigueLevel.ELEVATED -> CAUTION_SCORE
            FatigueLevel.HIGH -> RESTRICTED_SCORE + 5
            FatigueLevel.VERY_HIGH -> VERY_HIGH_SCORE
            FatigueLevel.LIMITED -> 100
        }

    private fun levelCap(level: FatigueLevel): Int =
        when (level) {
            FatigueLevel.LOW -> 30
            FatigueLevel.NORMAL -> CAUTION_SCORE - 1
            FatigueLevel.ELEVATED -> RESTRICTED_SCORE - 1
            FatigueLevel.HIGH -> VERY_HIGH_SCORE - 1
            FatigueLevel.VERY_HIGH,
            FatigueLevel.LIMITED -> 100
        }

    private fun Map<FatigueCategoryKey, Int>.score(category: FatigueCategoryKey): Int =
        this[category] ?: 0

    private fun Map<String, Int>.maxScore(vararg keys: String): Int =
        keys.maxOfOrNull { key -> this[key] ?: 0 } ?: 0

    private fun List<String>.filterAny(vararg fragments: String): List<String> =
        filter { reason -> fragments.any { fragment -> reason.contains(fragment, ignoreCase = true) } }

    private fun Int.clampScore(): Int = coerceIn(0, 100)

    private companion object {
        const val CAUTION_SCORE = FatigueThresholds.PRESENTATION_ELEVATED_SCORE
        const val RESTRICTED_SCORE = FatigueThresholds.PRESENTATION_RESTRICTED_SCORE
        const val VERY_HIGH_SCORE = FatigueThresholds.PRESENTATION_VERY_HIGH_SCORE
        const val SUPPORTING_AXIS_WEIGHT = 0.20
        const val SUPPORTING_BODY_PART_WEIGHT = 0.60
        const val BADMINTON_SYSTEMIC_SUPPORT_WEIGHT = 0.60
    }
}
