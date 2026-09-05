package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveStimulusCalculator
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.BadmintonPracticeLoadCalculator
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseRoleRelationCatalog
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

class PlanningHistorySnapshotBuilder {
    fun build(
        cutoff: LocalDate,
        history: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        metadata: Map<String, RuntimeExerciseMetadata>,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        profile: InitialUserProfile?,
        preferences: PersonalizedPlanningPreferences,
        canonicalStrengthSignals: Map<String, CanonicalStrengthSignal> = emptyMap(),
        recoverySignals: PlanningRecoverySignals = PlanningRecoverySignals(),
        exerciseRoleCatalog: ExerciseRoleRelationCatalog = ExerciseRoleRelationCatalog.EMPTY
    ): PlanningHistorySnapshot {
        val exerciseByKey = exercises.associateBy(Exercise::stableKey)
        val confirmed = history.asSequence()
            .filter { runCatching { LocalDate.parse(it.entry.date) }.getOrNull()?.let { date -> !date.isAfter(cutoff) } == true }
            .flatMap { record ->
                val date = LocalDate.parse(record.entry.date)
                record.sets.asSequence().filter { it.confirmed }.map { set ->
                    PlanningSetRecord(
                        date = date,
                        stableKey = record.entry.exerciseStableKey,
                        exerciseName = record.entry.exerciseName,
                        category = record.entry.category,
                        setIndex = set.setIndex,
                        reps = set.reps,
                        weightKg = set.weightKg,
                        seconds = set.seconds,
                        rpe = set.rpe ?: record.entry.rpe
                    )
                }
            }
            .sortedWith(compareBy(PlanningSetRecord::date, PlanningSetRecord::stableKey, PlanningSetRecord::setIndex))
            .toList()
        require(confirmed.isNotEmpty()) { "기록 기반 계획에 사용할 완료 세트가 없습니다." }
        require(confirmed.all { it.stableKey.isNotBlank() && it.stableKey in exerciseByKey }) {
            "완료 기록의 canonical stableKey 메타데이터를 확인할 수 없습니다."
        }
        val objectiveMap = exercises.map(Exercise::stableKey).distinct().associateWith { key ->
            badmintonCatalog.relations(key).associate { it.objective.name to it.transferLevel.coefficient }
        }
        val directObjectiveMap = exercises.map(Exercise::stableKey).distinct().associateWith { key ->
            badmintonCatalog.relations(key)
                .filter { it.transferLevel == BadmintonObjectiveTransferLevel.DIRECT }
                .mapTo(linkedSetOf()) { it.objective.name }
        }
        val eligibleHistory = history.filter { record ->
            runCatching { LocalDate.parse(record.entry.date) }.getOrNull()?.let { !it.isAfter(cutoff) } == true
        }
        val recentHistory = eligibleHistory.filter { record ->
            runCatching { LocalDate.parse(record.entry.date) }.getOrNull()?.let { !it.isBefore(cutoff.minusDays(27)) } == true
        }
        val runtimeCatalog = com.training.trackplanner.data.RuntimeExerciseMetadataCatalog.of(metadata.values)
        val objectiveExposure = BadmintonObjectiveStimulusCalculator(badmintonCatalog)
            .calculate(recentHistory, exerciseByKey)
        val genericCourtLoad28d = BadmintonPracticeLoadCalculator(runtimeCatalog)
            .calculateRaw(recentHistory, exerciseByKey)
        return PlanningHistorySnapshot(
            cutoff = cutoff,
            allConfirmedSets = confirmed,
            exercises = exerciseByKey,
            metadata = metadata,
            badmintonObjectives = objectiveMap,
            profilePrimaryGoal = profile?.primaryGoal.orEmpty(),
            strengthTrainingYears = profile?.strengthTrainingYears ?: 0.0,
            badmintonTrainingYears = profile?.badmintonTrainingYears ?: 0.0,
            preferences = preferences,
            genericCourtLoad = genericCourtLoad28d / 4.0,
            genericCourtLoad28d = genericCourtLoad28d,
            objectiveExposure = objectiveExposure,
            canonicalStrengthSignals = canonicalStrengthSignals,
            recoverySignals = recoverySignals,
            badmintonDirectObjectives = directObjectiveMap,
            badmintonSupportiveObjectives = exercises.associate { exercise ->
                exercise.stableKey to badmintonCatalog.relations(exercise.stableKey)
                    .filter { it.transferLevel == BadmintonObjectiveTransferLevel.SUPPORTIVE }
                    .mapTo(linkedSetOf()) { it.objective.name }
            },
            exerciseRoleCatalog = exerciseRoleCatalog
        )
    }
}
