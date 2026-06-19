package com.training.trackplanner.analysis.core

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.TrainingDatabase
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AnalysisInputCollector(
    private val db: TrainingDatabase,
    private val runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
) {
    suspend fun collect(today: LocalDate): AnalysisInputSnapshot {
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val windows = AnalysisWindows.from(today)
        val exerciseMetadata = db.exerciseDao()
            .allExercises()
            .associate { exercise -> exercise.id to exercise.toAnalysisMetadata() }

        val completedEntries = db.workoutDao()
            .entriesWithSetsUntil(todayString)
            .mapNotNull { entryWithSets ->
                val date = entryWithSets.entry.date.toLocalDateOrNull() ?: return@mapNotNull null
                val confirmedSets = entryWithSets.sets
                    .filter { it.confirmed }
                    .sortedBy { it.setIndex }
                if (confirmedSets.isEmpty()) {
                    null
                } else {
                    entryWithSets.toAnalysisEntry(date, confirmedSets)
                }
            }

        val plannedEntries = db.workoutDao()
            .entriesWithSetsAfter(todayString)
            .mapNotNull { entryWithSets ->
                val date = entryWithSets.entry.date.toLocalDateOrNull() ?: return@mapNotNull null
                val plannedSets = entryWithSets.sets
                    .filter { !it.confirmed }
                    .sortedBy { it.setIndex }
                if (plannedSets.isEmpty()) {
                    null
                } else {
                    entryWithSets.toAnalysisEntry(date, plannedSets)
                }
            }

        val conditionRecords = db.dailyMetricDao()
            .metricsUntil(todayString)
            .mapNotNull { metric -> metric.toAnalysisConditionRecord() }

        return AnalysisInputSnapshot(
            today = today,
            completedEntriesUntilToday = completedEntries,
            plannedEntriesFromTomorrow = plannedEntries,
            conditionRecordsUntilToday = conditionRecords,
            exerciseMetadataMap = exerciseMetadata,
            recentWindow = windows.recent7Days,
            futureWindow = windows.future7Days,
            windows = windows
        )
    }

    private fun WorkoutEntryWithSets.toAnalysisEntry(
        date: LocalDate,
        selectedSets: List<WorkoutSet>
    ): AnalysisEntry =
        AnalysisEntry(
            entryId = entry.id,
            date = date,
            exerciseId = entry.exerciseId,
            exerciseName = entry.exerciseName,
            category = entry.category,
            restSeconds = entry.restSeconds,
            rpe = entry.rpe,
            maxReps = entry.maxReps,
            sets = selectedSets.map { set ->
                AnalysisSet(
                    setId = set.id,
                    setIndex = set.setIndex,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    seconds = set.seconds,
                    confirmed = set.confirmed,
                    manualWeight = set.manualWeight,
                    rpe = set.rpe,
                    restSecondsOverride = set.restSecondsOverride
                )
            }
        )

    private fun Exercise.toAnalysisMetadata(): AnalysisExerciseMetadata {
        val runtime = runtimeMetadataCatalog.resolve(this)
        return AnalysisExerciseMetadata(
            exerciseId = id,
            stableKey = stableKey,
            activityKind = runtime?.activityKind?.takeIf { it.isNotBlank() } ?: activityKind,
            planningEligibility = runtime?.planningEligibility
                ?.takeIf { it.isNotBlank() }
                ?: planningEligibility,
            category = category,
            movementPattern = movementPattern,
            movementCategory = movementCategory,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles,
            equipment = equipment,
            equipmentTags = equipmentTags,
            compoundType = compoundType,
            forceType = forceType,
            bodyRegion = bodyRegion,
            plane = plane,
            laterality = laterality,
            axialLoadLevel = axialLoadLevel,
            trainingRole = trainingRole,
            stabilityRoles = stabilityRoles,
            sportTransferDirect = sportTransferDirect,
            sportTransferSupportive = sportTransferSupportive,
            badmintonTransferRoles = badmintonTransferRoles,
            fatigueCategories = fatigueCategories,
            adaptiveBaselineGroups = adaptiveBaselineGroups,
            accessoryRoles = accessoryRoles,
            loadProfile = loadProfile,
            recoveryDecayProfile = runtime?.recoveryDecayProfile
                ?.takeIf { it.isNotBlank() }
                ?: recoveryDecayProfile,
            systemicLoadWeight = systemicLoadWeight,
            neuralHeavyWeight = neuralHeavyWeight,
            neuralSpeedWeight = neuralSpeedWeight,
            localLoadWeight = localLoadWeight,
            decelerationWeight = decelerationWeight,
            elasticSscWeight = elasticSscWeight,
            rotationPowerWeight = rotationPowerWeight,
            antiRotationWeight = antiRotationWeight,
            overheadSwingWeight = overheadSwingWeight,
            gripLoadWeight = gripLoadWeight,
            progressMetricType = runtime?.progressMetricType
                ?.takeIf { it.isNotBlank() }
                ?: progressMetricType,
            strengthProgressionGroup = runtime?.strengthProgressionGroup
                ?.takeIf { it.isNotBlank() }
                ?: strengthProgressionGroup,
            hypertrophyVolumeGroup = hypertrophyVolumeGroup,
            mainLiftGroup = mainLiftGroup,
            accessoryContributionGroup = accessoryContributionGroup,
            estimated1RmEligible = estimated1RmEligible,
            volumeLoadEligible = volumeLoadEligible,
            badmintonTransferStrength = runtime?.badmintonTransferLevel
                ?.takeIf { it.isNotBlank() }
                ?: badmintonTransferStrength,
            courtMovementTypes = courtMovementTypes,
            badmintonSkillTargets = runtime?.badmintonSkillTargets?.raw ?: badmintonSkillTargets,
            jointStressTags = jointStressTags,
            stabilityDemandLevel = stabilityDemandLevel,
            mobilityDemandLevel = mobilityDemandLevel,
            balanceContributionTags = balanceContributionTags,
            analysisEligibility = runtime?.analysisEligibility?.raw ?: analysisEligibility,
            metadataConfidence = metadataConfidence,
            movementFamily = runtime?.movementFamily.orEmpty(),
            movementSubtype = runtime?.movementSubtype.orEmpty(),
            programSlot = runtime?.programSlot.orEmpty(),
            redundancyGroup = runtime?.redundancyGroup.orEmpty(),
            primaryStressProfile = runtime?.primaryStressProfile.orEmpty(),
            secondaryStressTags = runtime?.secondaryStressTags?.raw.orEmpty(),
            tendonStressTags = runtime?.tendonStressTags?.raw.orEmpty(),
            ligamentJointStabilityStressTags = runtime?.ligamentJointStabilityStressTags?.raw.orEmpty(),
            jointImpactStressTags = runtime?.jointImpactStressTags?.raw.orEmpty(),
            cognitiveStressTags = runtime?.cognitiveStressTags?.raw.orEmpty(),
            sportContextTags = runtime?.sportContextTags?.raw.orEmpty(),
            stressMagnitudeHint = runtime?.stressMagnitudeHint.orEmpty(),
            neuromuscularStressLevel = runtime?.neuromuscularStressLevel.orEmpty(),
            systemicMuscularStressLevel = runtime?.systemicMuscularStressLevel.orEmpty(),
            localMuscularStressLevel = runtime?.localMuscularStressLevel.orEmpty(),
            jointTendonImpactStressLevel = runtime?.jointTendonImpactStressLevel.orEmpty(),
            movementFocusDemandLevel = runtime?.movementFocusDemandLevel.orEmpty(),
            recoveryDurationClass = runtime?.recoveryDurationClass.orEmpty(),
            canonicalBadmintonTransferLevel = runtime?.badmintonTransferLevel.orEmpty(),
            canonicalBadmintonTransferType = runtime?.badmintonTransferType?.raw.orEmpty(),
            canonicalBadmintonSkillTargets = runtime?.badmintonSkillTargets?.raw.orEmpty(),
            badmintonPhysicalQualities = runtime?.badmintonPhysicalQualities?.raw.orEmpty(),
            transferConfidence = runtime?.transferConfidence.orEmpty(),
            sourceConfidenceLevel = runtime?.sourceConfidenceLevel.orEmpty(),
            finalSourceStatus = runtime?.finalSourceStatus.orEmpty()
        )
    }

    private fun DailyMetric.toAnalysisConditionRecord(): AnalysisConditionRecord? {
        val parsedDate = date.toLocalDateOrNull() ?: return null
        return AnalysisConditionRecord(
            date = parsedDate,
            sleepHours = sleepHours,
            bodyWeightKg = bodyWeightKg
        )
    }

    private fun String.toLocalDateOrNull(): LocalDate? =
        runCatching { LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
}
