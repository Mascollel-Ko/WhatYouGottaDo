package com.training.trackplanner.analysis.readiness

import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ResidualFatigueCalculator {
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
                val decay = decayFactor(contribution.recoveryDecayProfile, ageDays)
                if (decay <= 0.0) return@forEach

                contribution.categoryLoads.forEach { (category, load) ->
                    categoryResiduals.add(category, load * categoryAdjustedDecay(category, decay))
                }
                contribution.bodyPartLoads.forEach { (part, load) ->
                    bodyPartResiduals.add(part, load * decay)
                }
                contribution.baselineGroupLoads.forEach { (group, load) ->
                    baselineGroupResiduals.add(group, load * decay)
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
        val curve = TodayReadinessConstants.decayCurves[profile.ifBlank { "SHORT" }]
            ?: TodayReadinessConstants.decayCurves.getValue("SHORT")
        return curve.getOrElse(ageDays) { 0.0 }
    }

    private fun categoryAdjustedDecay(category: FatigueCategoryKey, baseDecay: Double): Double =
        when (category) {
            FatigueCategoryKey.NEURAL_HEAVY,
            FatigueCategoryKey.DECELERATION,
            FatigueCategoryKey.ELASTIC_SSC -> baseDecay.coerceAtLeast(baseDecay * 1.05)
            else -> baseDecay
        }

    private fun MutableMap<FatigueCategoryKey, Double>.add(key: FatigueCategoryKey, value: Double) {
        if (value <= 0.0) return
        put(key, (this[key] ?: 0.0) + value)
    }

    private fun MutableMap<String, Double>.add(key: String, value: Double) {
        if (value <= 0.0) return
        put(key, (this[key] ?: 0.0) + value)
    }
}
