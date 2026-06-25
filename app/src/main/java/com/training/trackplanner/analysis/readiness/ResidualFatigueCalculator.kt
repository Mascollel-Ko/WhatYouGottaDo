package com.training.trackplanner.analysis.readiness

import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ResidualFatigueCalculator {
    private val profileOrder = listOf("MINIMAL", "SHORT", "MEDIUM", "LONG", "VERY_LONG")

    fun calculate(
        dailyLoads: List<DailyAnalysisLoad>,
        today: LocalDate
    ): ResidualFatigueSnapshot {
        val categoryResiduals = mutableMapOf<FatigueCategoryKey, Double>()
        val bodyPartResiduals = mutableMapOf<String, Double>()
        val baselineGroupResiduals = mutableMapOf<String, Double>()

        dailyLoads
            .flatMap { daily -> daily.contributions }
            .forEach { contribution ->
                val ageDays = ChronoUnit.DAYS.between(contribution.date, today).toInt()
                if (ageDays < 0) return@forEach

                contribution.categoryLoads.forEach { (category, load) ->
                    val decay = categoryAdjustedDecay(
                        category = category,
                        profile = contribution.recoveryDecayProfile,
                        ageDays = ageDays,
                        averageRpe = contribution.averageRpe
                    )
                    categoryResiduals.add(category, load * decay)
                }
                contribution.bodyPartLoads.forEach { (part, load) ->
                    val decay = bodyPartAdjustedDecay(
                        part = part,
                        contribution = contribution,
                        ageDays = ageDays
                    )
                    bodyPartResiduals.add(part, load * decay)
                }
                val baseDecay = decayFactor(contribution.recoveryDecayProfile, ageDays)
                if (baseDecay > 0.0) {
                    contribution.baselineGroupLoads.forEach { (group, load) ->
                        baselineGroupResiduals.add(group, load * baseDecay)
                    }
                }
            }

        return ResidualFatigueSnapshot(
            residualByCategory = categoryResiduals,
            residualByBodyPart = bodyPartResiduals,
            residualByAdaptiveBaselineGroup = baselineGroupResiduals,
            highestResidualCategories = categoryResiduals
                .entries
                .sortedByDescending { entry -> entry.value }
                .take(3)
                .map { entry -> entry.key },
            highestResidualBodyParts = bodyPartResiduals
                .entries
                .sortedByDescending { entry -> entry.value }
                .take(3)
                .map { entry -> entry.key }
        )
    }

    fun decayFactor(profile: String, ageDays: Int): Double {
        if (ageDays < 0) return 0.0
        val curve = TodayReadinessConstants.decayCurves[normalizeProfile(profile)]
            ?: TodayReadinessConstants.decayCurves.getValue("SHORT")
        return curve.getOrElse(ageDays) { 0.0 }.coerceIn(0.0, 1.0)
    }

    private fun categoryAdjustedDecay(
        category: FatigueCategoryKey,
        profile: String,
        ageDays: Int,
        averageRpe: Double?
    ): Double {
        val baseProfile = normalizeProfile(profile)
        val adjustedProfile = categoryAdjustedProfile(category, baseProfile, averageRpe)
        return maxOf(
            decayFactor(baseProfile, ageDays),
            decayFactor(adjustedProfile, ageDays)
        ).coerceIn(0.0, 1.0)
    }

    private fun categoryAdjustedProfile(
        category: FatigueCategoryKey,
        baseProfile: String,
        averageRpe: Double?
    ): String {
        // Beta heuristic: use the existing decay curves, but choose a slower profile for fatigue types
        // that tend to leave residual neural, landing, or elastic stress. These are tuning defaults,
        // not research-derived constants.
        val categorySteps = when (category) {
            FatigueCategoryKey.NEURAL_HEAVY,
            FatigueCategoryKey.DECELERATION,
            FatigueCategoryKey.ELASTIC_SSC -> 1
            else -> 0
        }
        val rpeSteps = when {
            averageRpe == null || averageRpe < 8.0 -> 0
            category == FatigueCategoryKey.NEURAL_SPEED -> 1
            category in highRpePersistenceCategories -> 1
            else -> 0
        }
        val rpeNineMinimum = if (averageRpe != null && averageRpe >= 9.0 && category in highRpePersistenceCategories) {
            "LONG"
        } else {
            baseProfile
        }
        return maxProfile(
            promoteProfile(baseProfile, categorySteps + rpeSteps),
            rpeNineMinimum
        )
    }

    private fun bodyPartAdjustedDecay(
        part: String,
        contribution: DailyLoadContribution,
        ageDays: Int
    ): Double {
        val baseProfile = normalizeProfile(contribution.recoveryDecayProfile)
        val adjustedProfile = bodyPartAdjustedProfile(
            part = normalizeBodyPart(part),
            categories = contribution.categoryLoads.keys,
            baseProfile = baseProfile,
            averageRpe = contribution.averageRpe
        )
        return maxOf(
            decayFactor(baseProfile, ageDays),
            decayFactor(adjustedProfile, ageDays)
        ).coerceIn(0.0, 1.0)
    }

    private fun bodyPartAdjustedProfile(
        part: String,
        categories: Set<FatigueCategoryKey>,
        baseProfile: String,
        averageRpe: Double?
    ): String {
        // Beta heuristic: keep ordinary local muscle on the metadata profile, but slow decay for
        // clear tissue/category pairings that otherwise disappear before their category pressure.
        // These are tuning defaults, not research-derived recovery constants.
        var promotionSteps = 0
        var minimumProfile = baseProfile

        val hasLandingStress = categories.hasAny(FatigueCategoryKey.DECELERATION, FatigueCategoryKey.ELASTIC_SSC)
        if (part in landingBodyParts && hasLandingStress) {
            promotionSteps += 1
            if (averageRpe != null && averageRpe >= 8.0) promotionSteps += 1
            if (averageRpe != null && averageRpe >= 9.0 && part == "calves_achilles") {
                minimumProfile = maxProfile(minimumProfile, "LONG")
            }
        }

        val hasOverheadStress = categories.hasAny(FatigueCategoryKey.OVERHEAD_REPETITION)
        if (part in overheadBodyParts && hasOverheadStress) {
            promotionSteps += 1
            if (averageRpe != null && averageRpe >= 8.0) promotionSteps += 1
        }

        val hasGripStress = categories.hasAny(FatigueCategoryKey.GRIP_FOREARM)
        if (part in gripBodyParts && hasGripStress) {
            promotionSteps += 1
            if (averageRpe != null && averageRpe >= 8.0) promotionSteps += 1
        }

        val hasHeavyNeuralStress = categories.hasAny(FatigueCategoryKey.NEURAL_HEAVY)
        if (part in heavyNeuralSupportBodyParts && hasHeavyNeuralStress) {
            promotionSteps += 1
            if (averageRpe != null && averageRpe >= 9.0) {
                minimumProfile = maxProfile(minimumProfile, "LONG")
            }
        }

        return maxProfile(promoteProfile(baseProfile, promotionSteps), minimumProfile)
    }

    private fun normalizeProfile(profile: String): String =
        profile.trim().uppercase().takeIf { value -> value in profileOrder } ?: "SHORT"

    private fun normalizeBodyPart(part: String): String =
        part.trim().lowercase()

    private fun promoteProfile(profile: String, steps: Int): String {
        val startIndex = profileOrder.indexOf(normalizeProfile(profile)).coerceAtLeast(0)
        return profileOrder[(startIndex + steps).coerceAtMost(profileOrder.lastIndex)]
    }

    private fun maxProfile(first: String, second: String): String =
        if (profileOrder.indexOf(normalizeProfile(first)) >= profileOrder.indexOf(normalizeProfile(second))) {
            normalizeProfile(first)
        } else {
            normalizeProfile(second)
        }

    private fun MutableMap<FatigueCategoryKey, Double>.add(key: FatigueCategoryKey, value: Double) {
        if (value <= 0.0) return
        put(key, (this[key] ?: 0.0) + value)
    }

    private fun MutableMap<String, Double>.add(key: String, value: Double) {
        if (value <= 0.0) return
        put(key, (this[key] ?: 0.0) + value)
    }

    private fun Set<FatigueCategoryKey>.hasAny(vararg candidates: FatigueCategoryKey): Boolean =
        candidates.any { candidate -> candidate in this }

    private companion object {
        val highRpePersistenceCategories = setOf(
            FatigueCategoryKey.NEURAL_HEAVY,
            FatigueCategoryKey.DECELERATION,
            FatigueCategoryKey.ELASTIC_SSC
        )

        val landingBodyParts = setOf(
            "calves_achilles",
            "quads",
            "glutes",
            "hips_adductors_abductors"
        )
        val overheadBodyParts = setOf(
            "shoulders",
            "rotator_cuff",
            "elbow_extensors"
        )
        val gripBodyParts = setOf(
            "forearm_grip",
            "elbow_flexors",
            "elbow_extensors"
        )
        val heavyNeuralSupportBodyParts = setOf(
            "erectors_low_back",
            "glutes",
            "hamstrings"
        )
    }
}
