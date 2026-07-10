package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.analysis.features.AnalysisExerciseFeatures
import com.training.trackplanner.analysis.features.BodyweightEffectiveLoadCalculator
import com.training.trackplanner.analysis.features.DurationHoldLoadCalculator
import com.training.trackplanner.analysis.features.DurationHoldPolicy
import com.training.trackplanner.analysis.features.ExerciseAnalysisMapper
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseTaxonomy
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

class DailyAnalysisLoadAggregator {
    fun aggregate(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exerciseMap: Map<Long, Exercise>,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
        dailyMetrics: List<DailyMetric> = emptyList(),
        initialProfile: InitialUserProfile? = null
    ): List<DailyAnalysisLoad> {
        val contributions = entriesWithSets.mapNotNull { entryWithSets ->
            val confirmedSets = entryWithSets.sets.filter { set -> set.confirmed }
            if (confirmedSets.isEmpty()) return@mapNotNull null
            val exercise = exerciseMap[entryWithSets.entry.exerciseId] ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(entryWithSets.entry.date) }.getOrNull()
                ?: return@mapNotNull null
            val bodyWeightKg = BodyweightEffectiveLoadCalculator.bodyWeightFor(
                date = entryWithSets.entry.date,
                dailyMetrics = dailyMetrics,
                initialProfile = initialProfile
            )
            val features = ExerciseAnalysisMapper.fromRecord(
                exercise = exercise,
                entry = entryWithSets.entry,
                sets = entryWithSets.sets,
                runtimeMetadata = runtimeMetadataCatalog.resolve(exercise),
                bodyWeightKg = bodyWeightKg
            )
            if (!features.isCompleted) return@mapNotNull null

            val rpeModifier = TodayReadinessConstants.rpeModifier(features.averageRpe)
            val baseDose = baseDose(features)
            val systemicLoad = baseDose * features.systemicLoadWeight * rpeModifier *
                features.systemicMuscularStressLevel.axisMultiplier()
            val neuralHeavyLoad = baseDose * features.neuralHeavyWeight * rpeModifier *
                features.neuromuscularStressLevel.axisMultiplier()
            val neuralSpeedLoad =
                baseDose * features.neuralSpeedWeight * TodayReadinessConstants.speedRpeModifier(rpeModifier) *
                    features.neuromuscularStressLevel.axisMultiplier()
            val localLoad =
                baseDose * features.localLoadWeight * TodayReadinessConstants.localRpeModifier(rpeModifier) *
                    features.localMuscularStressLevel.axisMultiplier()
            val jointStressMultiplier = features.jointTendonImpactStressLevel.axisMultiplier()
            val decelerationLoad = baseDose * features.decelerationWeight * jointStressMultiplier
            val elasticLoad = baseDose * features.elasticSscWeight * jointStressMultiplier
            val rotationLoad = baseDose * features.rotationPowerWeight
            val antiRotationLoad = baseDose * features.antiRotationWeight
            val overheadLoad = baseDose * features.overheadSwingWeight
            val gripLoad = baseDose * features.gripLoadWeight
            val hasCourtMovement = features.courtMovementTypes.isNotEmpty()
            val badmintonBonus =
                baseDose * TodayReadinessConstants.badmintonTransferBonus(
                    features.badmintonTransferStrength,
                    hasCourtMovement
                )
            val badmintonCourtLoad =
                neuralSpeedLoad + decelerationLoad + elasticLoad + overheadLoad + gripLoad + badmintonBonus

            val categoryLoads = mapOf(
                FatigueCategoryKey.SYSTEMIC to systemicLoad,
                FatigueCategoryKey.NEURAL_HEAVY to neuralHeavyLoad,
                FatigueCategoryKey.NEURAL_SPEED to neuralSpeedLoad,
                FatigueCategoryKey.LOCAL_MUSCLE to localLoad,
                FatigueCategoryKey.DECELERATION to decelerationLoad,
                FatigueCategoryKey.ELASTIC_SSC to elasticLoad,
                FatigueCategoryKey.ROTATION_POWER to rotationLoad,
                FatigueCategoryKey.ANTI_ROTATION to antiRotationLoad,
                FatigueCategoryKey.OVERHEAD_REPETITION to overheadLoad,
                FatigueCategoryKey.GRIP_FOREARM to gripLoad,
                FatigueCategoryKey.BADMINTON_COURT to badmintonCourtLoad
            ).filterValues { load -> load > 0.0 }

            DailyLoadContribution(
                date = date,
                exerciseId = features.exerciseId,
                entryId = entryWithSets.entry.id,
                exerciseName = features.exerciseName,
                recoveryDecayProfile = features.recoveryDecayProfile.ifBlank { "SHORT" },
                categoryLoads = categoryLoads,
                bodyPartLoads = bodyPartLoads(
                    primaryMuscles = features.primaryMuscles,
                    secondaryMuscles = features.secondaryMuscles,
                    localLoad = localLoad,
                    axialLoadLevel = features.axialLoadLevel,
                    decelerationLoad = decelerationLoad,
                    elasticLoad = elasticLoad,
                    overheadLoad = overheadLoad,
                    gripLoad = gripLoad
                ).durationHoldBodyPartLoads(features, localLoad),
                baselineGroupLoads = baselineGroupLoads(features.adaptiveBaselineGroups, categoryLoads),
                completedSets = features.completedSets,
                totalReps = features.totalReps ?: 0,
                durationMinutes = features.durationMinutes,
                averageRpe = features.averageRpe
            )
        }

        return contributions
            .groupBy { contribution -> contribution.date }
            .map { (date, dailyContributions) ->
                DailyAnalysisLoad(
                    date = date,
                    categoryLoads = dailyContributions.foldLoads { it.categoryLoads },
                    bodyPartLoads = dailyContributions.foldLoads { it.bodyPartLoads },
                    baselineGroupLoads = dailyContributions.foldLoads { it.baselineGroupLoads },
                    completedEntryCount = dailyContributions.map { it.entryId }.distinct().size,
                    completedSetCount = dailyContributions.sumOf { it.completedSets },
                    contributions = dailyContributions
                )
            }
            .sortedBy { daily -> daily.date }
    }

    private fun baseDose(features: AnalysisExerciseFeatures): Double {
        val volumeLoad = features.totalVolumeLoad ?: 0.0
        if (volumeLoad > 0.0) return volumeLoad
        val repsLoad = (features.totalReps ?: 0) * TodayReadinessConstants.BODYWEIGHT_PROXY_LOAD
        if (repsLoad > 0.0) return repsLoad
        val durationLoad =
            (features.durationMinutes ?: 0.0) * TodayReadinessConstants.DRILL_INTENSITY_PROXY_LOAD_PER_MINUTE
        return durationLoad.coerceAtLeast(features.completedSets * 10.0)
    }

    private fun baselineGroupLoads(
        groups: Set<String>,
        categoryLoads: Map<FatigueCategoryKey, Double>
    ): Map<String, Double> {
        if (groups.isEmpty()) return emptyMap()
        val weightedLoad = categoryLoads.values.sum()
        if (weightedLoad <= 0.0) return emptyMap()
        return groups.associateWith { weightedLoad / groups.size }
    }

    private fun bodyPartLoads(
        primaryMuscles: Set<String>,
        secondaryMuscles: Set<String>,
        localLoad: Double,
        axialLoadLevel: String,
        decelerationLoad: Double,
        elasticLoad: Double,
        overheadLoad: Double,
        gripLoad: Double
    ): Map<String, Double> {
        val loads = mutableMapOf<String, Double>()
        primaryMuscles.forEach { token ->
            muscleBodyParts(token).forEach { part ->
                loads.add(part, localLoad * TodayReadinessConstants.LOCAL_PRIMARY_SHARE)
            }
        }
        secondaryMuscles.forEach { token ->
            muscleBodyParts(token).forEach { part ->
                loads.add(part, localLoad * TodayReadinessConstants.LOCAL_SECONDARY_SHARE)
            }
        }
        if (axialLoadLevel == "HIGH" || axialLoadLevel == "MODERATE") {
            loads.add("erectors_low_back", localLoad * TodayReadinessConstants.AXIAL_BACK_SHARE)
        }
        val landingLoad = decelerationLoad + elasticLoad
        if (landingLoad > 0.0) {
            listOf("quads", "glutes", "calves_achilles", "hips_adductors_abductors").forEach { part ->
                loads.add(part, landingLoad * TodayReadinessConstants.DECELERATION_LOWER_SHARE)
            }
        }
        if (overheadLoad > 0.0) {
            loads.add("shoulders", overheadLoad * TodayReadinessConstants.OVERHEAD_SHOULDER_SHARE)
            loads.add("rotator_cuff", overheadLoad * TodayReadinessConstants.OVERHEAD_SHOULDER_SHARE)
        }
        if (gripLoad > 0.0) {
            loads.add("forearm_grip", gripLoad * TodayReadinessConstants.GRIP_FOREARM_SHARE)
        }
        return loads.filterValues { value -> value > 0.0 }
    }

    private fun Map<String, Double>.durationHoldBodyPartLoads(
        features: AnalysisExerciseFeatures,
        localLoad: Double
    ): Map<String, Double> {
        if (localLoad <= 0.0) return this
        val policy = DurationHoldLoadCalculator.policyFor(
            stableKey = features.stableKey,
            displayName = features.exerciseName,
            movementPattern = features.movementPattern,
            movementCategory = features.movementCategory,
            equipment = features.equipment.joinToString("|")
        ) ?: return this
        val loads = mutableMapOf<String, Double>()
        when (policy) {
            DurationHoldPolicy.PLANK -> {
                loads.add("core_abs_obliques", localLoad * 0.65)
                loads.add("glutes", localLoad * 0.15)
                loads.add("shoulders", localLoad * 0.10)
                loads.add("core_abs_obliques", localLoad * 0.10)
            }
            DurationHoldPolicy.SIDE_PLANK -> {
                loads.add("core_abs_obliques", localLoad * 0.55)
                loads.add("hips_adductors_abductors", localLoad * 0.25)
                loads.add("shoulders", localLoad * 0.10)
                loads.add("core_abs_obliques", localLoad * 0.10)
            }
        }
        return loads.filterValues { value -> value > 0.0 }
    }

    private fun muscleBodyParts(token: String): List<String> {
        return when (ExerciseTaxonomy.canonicalMuscleToken(token)) {
            "QUADRICEPS", "RECTUS_FEMORIS" -> listOf("quads")
            "HAMSTRING" -> listOf("hamstrings")
            "GLUTE", "GLUTE_MEDIUS" -> listOf("glutes")
            "CALF", "TIBIALIS" -> listOf("calves_achilles")
            "ERECTOR_SPINAE" -> listOf("erectors_low_back")
            "CHEST", "UPPER_CHEST" -> listOf("chest")
            "LAT", "BACK", "RHOMBOID", "TRAPEZIUS", "LOWER_TRAP" -> listOf("lats_upper_back")
            "SHOULDER", "ANTERIOR_DELTOID", "LATERAL_DELTOID", "REAR_DELT", "SCAPULAR_STABILIZERS" ->
                listOf("shoulders")
            "ROTATOR_CUFF" -> listOf("rotator_cuff")
            "BICEPS" -> listOf("elbow_flexors")
            "TRICEPS" -> listOf("elbow_extensors")
            "FOREARM", "GRIP" -> listOf("forearm_grip")
            "CORE", "DEEP_CORE", "OBLIQUE", "ROTATION_CORE" -> listOf("core_abs_obliques")
            "HIP_ADDUCTOR" -> listOf("hips_adductors_abductors")
            else -> emptyList()
        }
    }

    private fun String.axisMultiplier(): Double =
        when (uppercase()) {
            "VERY_HIGH" -> 1.25
            "HIGH" -> 1.12
            "LOW" -> 0.90
            else -> 1.0
        }

    private fun MutableMap<String, Double>.add(key: String, value: Double) {
        if (value <= 0.0) return
        put(key, (this[key] ?: 0.0) + value)
    }

    private fun <K> List<DailyLoadContribution>.foldLoads(
        selector: (DailyLoadContribution) -> Map<K, Double>
    ): Map<K, Double> {
        val totals = mutableMapOf<K, Double>()
        forEach { contribution ->
            selector(contribution).forEach { (key, value) ->
                if (value > 0.0) totals[key] = (totals[key] ?: 0.0) + value
            }
        }
        return totals
    }
}
