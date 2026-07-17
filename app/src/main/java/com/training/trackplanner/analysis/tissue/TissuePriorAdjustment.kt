package com.training.trackplanner.analysis.tissue

import kotlin.math.exp
import kotlin.math.ln

enum class TissuePriorCoefficientSource {
    SIMULATION_FITTED,
    POLICY_BOUNDED,
    NEUTRAL_NOT_APPLICABLE
}

enum class TissueHabitualTrainingIntensity {
    LIGHT,
    NORMAL,
    HARD
}

enum class TissuePriorMissingInput {
    BODY_WEIGHT,
    STRENGTH_EXPERIENCE,
    RACKET_EXPERIENCE,
    HABITUAL_INTENSITY
}

data class TissuePriorBoundaries(
    val meaningfulFloor: Double,
    val q30: Double,
    val q80: Double,
    val q95: Double
) {
    init {
        require(meaningfulFloor.isFinite() && q30.isFinite() && q80.isFinite() && q95.isFinite())
        require(meaningfulFloor >= 0.0 && meaningfulFloor < q30 && q30 < q80 && q80 < q95)
    }
}

data class TissuePriorProfileAdjustment(
    val priorProfileId: String,
    val bodyMassBeta: Double,
    val bodyMassSource: TissuePriorCoefficientSource,
    val lightIntensityLogOffset: Double,
    val hardIntensityLogOffset: Double,
    val habitualIntensitySource: TissuePriorCoefficientSource,
    val strengthExperienceLogCoefficient: Double,
    val strengthExperienceRelevance: Double,
    val strengthExperienceSource: TissuePriorCoefficientSource,
    val racketExperienceLogCoefficient: Double,
    val racketExperienceRelevance: Double,
    val racketExperienceSource: TissuePriorCoefficientSource,
    val normalClampMin: Double = 0.85,
    val normalClampMax: Double = 1.15,
    val hardClampMin: Double = 0.80,
    val hardClampMax: Double = 1.20
) {
    init {
        require(priorProfileId.isNotBlank())
        require(
            listOf(
                bodyMassBeta,
                lightIntensityLogOffset,
                hardIntensityLogOffset,
                strengthExperienceLogCoefficient,
                racketExperienceLogCoefficient,
                normalClampMin,
                normalClampMax,
                hardClampMin,
                hardClampMax
            ).all(Double::isFinite)
        )
        require(strengthExperienceRelevance in 0.0..1.0)
        require(racketExperienceRelevance in 0.0..1.0)
        require(0.0 < hardClampMin && hardClampMin <= normalClampMin)
        require(normalClampMin < 1.0 && normalClampMax > 1.0)
        require(normalClampMax <= hardClampMax)
    }
}

data class TissuePriorUserProfileInputs(
    val bodyWeightKg: Double?,
    val strengthTrainingExperienceYears: Double?,
    val racketSportExperienceYears: Double?,
    val habitualTrainingIntensity: TissueHabitualTrainingIntensity?
)

data class TissueAdjustedPriorResult(
    val boundaries: TissuePriorBoundaries,
    val multiplier: Double,
    val bodyMassContribution: Double,
    val habitualIntensityContribution: Double,
    val strengthExperienceContribution: Double,
    val racketExperienceContribution: Double,
    val combinedExperienceContribution: Double,
    val combinedExperienceClampApplied: Boolean,
    val normalClampApplied: Boolean,
    val hardClampApplied: Boolean,
    val missingInputs: Set<TissuePriorMissingInput>,
    val coefficientSources: Map<String, TissuePriorCoefficientSource>
)

object TissuePriorAdjustment {
    const val REFERENCE_BODY_WEIGHT_KG = 75.0
    const val EXPERIENCE_COMBINED_MIN = 0.94
    const val EXPERIENCE_COMBINED_MAX = 1.06

    fun adjust(
        basePrior: TissuePriorBoundaries,
        profile: TissuePriorProfileAdjustment,
        inputs: TissuePriorUserProfileInputs
    ): TissueAdjustedPriorResult {
        validateInputs(inputs)
        val missing = linkedSetOf<TissuePriorMissingInput>()
        val bodyMass = inputs.bodyWeightKg?.let {
            exp(profile.bodyMassBeta * ln(it / REFERENCE_BODY_WEIGHT_KG))
        } ?: 1.0.also { missing += TissuePriorMissingInput.BODY_WEIGHT }
        val habitualIntensity = when (inputs.habitualTrainingIntensity) {
            TissueHabitualTrainingIntensity.LIGHT -> exp(profile.lightIntensityLogOffset)
            TissueHabitualTrainingIntensity.NORMAL -> 1.0
            TissueHabitualTrainingIntensity.HARD -> exp(profile.hardIntensityLogOffset)
            null -> 1.0.also { missing += TissuePriorMissingInput.HABITUAL_INTENSITY }
        }
        val strengthExperience = inputs.strengthTrainingExperienceYears?.let {
            exp(profile.strengthExperienceLogCoefficient * experienceScore(it) * profile.strengthExperienceRelevance)
        } ?: 1.0.also { missing += TissuePriorMissingInput.STRENGTH_EXPERIENCE }
        val racketExperience = inputs.racketSportExperienceYears?.let {
            exp(profile.racketExperienceLogCoefficient * experienceScore(it) * profile.racketExperienceRelevance)
        } ?: 1.0.also { missing += TissuePriorMissingInput.RACKET_EXPERIENCE }
        val rawExperience = strengthExperience * racketExperience
        val combinedExperience = rawExperience.coerceIn(EXPERIENCE_COMBINED_MIN, EXPERIENCE_COMBINED_MAX)
        val rawMultiplier = bodyMass * habitualIntensity * combinedExperience
        require(rawMultiplier.isFinite() && rawMultiplier > 0.0)
        val hardBounded = rawMultiplier.coerceIn(profile.hardClampMin, profile.hardClampMax)
        val multiplier = hardBounded.coerceIn(profile.normalClampMin, profile.normalClampMax)
        return TissueAdjustedPriorResult(
            boundaries = TissuePriorBoundaries(
                meaningfulFloor = basePrior.meaningfulFloor,
                q30 = basePrior.q30 * multiplier,
                q80 = basePrior.q80 * multiplier,
                q95 = basePrior.q95 * multiplier
            ),
            multiplier = multiplier,
            bodyMassContribution = bodyMass,
            habitualIntensityContribution = habitualIntensity,
            strengthExperienceContribution = strengthExperience,
            racketExperienceContribution = racketExperience,
            combinedExperienceContribution = combinedExperience,
            combinedExperienceClampApplied = rawExperience != combinedExperience,
            normalClampApplied = hardBounded != multiplier,
            hardClampApplied = rawMultiplier != hardBounded,
            missingInputs = missing,
            coefficientSources = linkedMapOf(
                "bodyMass" to profile.bodyMassSource,
                "habitualIntensity" to profile.habitualIntensitySource,
                "strengthExperience" to profile.strengthExperienceSource,
                "racketExperience" to profile.racketExperienceSource
            )
        )
    }

    fun experienceScore(years: Double): Double {
        require(years.isFinite() && years >= 0.0)
        return when {
            years < 0.5 -> -1.0
            years < 2.0 -> -0.5
            years < 5.0 -> 0.0
            years < 10.0 -> 0.5
            else -> 1.0
        }
    }

    private fun validateInputs(inputs: TissuePriorUserProfileInputs) {
        inputs.bodyWeightKg?.let { require(it.isFinite() && it > 0.0) }
        inputs.strengthTrainingExperienceYears?.let { require(it.isFinite() && it >= 0.0) }
        inputs.racketSportExperienceYears?.let { require(it.isFinite() && it >= 0.0) }
    }
}
