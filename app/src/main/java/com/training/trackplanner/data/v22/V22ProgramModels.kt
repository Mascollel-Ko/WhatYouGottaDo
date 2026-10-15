package com.training.trackplanner.data.v22

import com.training.trackplanner.data.GeneratedProgramSkeleton
import java.time.LocalDate

enum class V22Mode { A, B, C }

data class V22GenerationRequest(
    val name: String,
    val weeks: Int,
    val days: Int,
    val mode: V22Mode,
    val asOf: LocalDate,
    val focusStableKey: String? = null,
    val sessionMinutes: Int? = null,
    val backupName: String = "APP_WORKOUT_HISTORY"
)

data class V22HistoryRow(
    val date: LocalDate,
    val exerciseName: String,
    val stableKey: String,
    val category: String,
    val setIndex: Int,
    val reps: Double?,
    val weightKg: Double?,
    val seconds: Double?,
    val rpe: Double?
)

data class V22ObservedSet(
    val weightKg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val observedRpe: Double?,
    val sourceDate: String,
    val loadStatus: String
)

data class V22ExercisePlan(
    val exercise: String,
    val stableKey: String,
    val category: String,
    val role: String,
    val setCount: Int,
    val sets: List<V22ObservedSet>,
    val historicalSets28d: Int,
    val historicalSets56d: Int,
    val source: String = "LATEST_COMPLETED_SESSION_REUSE_NOT_NEW_PRESCRIPTION",
    val prescriptionAuthority: String? = null,
    val plannedProgression: String? = null,
    val block: Int? = null
)

data class V22Session(
    val session: Int,
    val items: List<V22ExercisePlan>,
    val nominalSets: Int
)

data class V22BaselineProgram(
    val status: String,
    val reasons: List<String> = emptyList(),
    val sessions: List<V22Session> = emptyList(),
    val weeklyNominalSets: Int? = null,
    val exerciseCount: Int? = null,
    val retainedHistoryExerciseCount: Int? = null,
    val omittedEligibleExercises: List<String> = emptyList(),
    val roleSets: Map<String, Int> = emptyMap(),
    val placementError: V22PlacementError? = null
)

data class V22PlacementError(
    val exercise: String,
    val reason: String,
    val remainingSets: Int
)

data class V22TrainingWeekEvidence(
    val start: String,
    val end: String,
    val trainingDays: Int,
    val excludedBottom30Percent: Boolean
)

data class V22TrainingDayInference(
    val window: String,
    val categories: List<String>,
    val weeks: List<V22TrainingWeekEvidence>,
    val excludedWeekCount: Int,
    val rounding: String,
    val retainedMean: Double
)

data class V22BaselineGeneration(
    val schema: String,
    val source: String,
    val asOf: String,
    val trainingDays: Int,
    val trainingDaysSource: String,
    val trainingDayInference: V22TrainingDayInference,
    val profiles: List<V22ExerciseProfile>,
    val programs: Map<V22Mode, V22BaselineProgram>,
    val limitations: List<String>
)

data class V22ExerciseProfile(
    val key: String,
    val name: String,
    val category: String,
    val role: String,
    val sets28: Int,
    val sets56: Int,
    val days28: Int,
    val days56: Int,
    val lastDate: String,
    val ageDays: Long,
    val weekly28: Double,
    val weekly56: Double,
    val priority: Double,
    internal val recentShape: List<V22HistoryRow>
)

data class V22RoleEvidence(
    val historyWeeklyNominal: Double,
    val baselinePlanned: Int,
    val gap: Double
)

data class V22Reallocation(
    val from: String,
    val to: String,
    val donorRole: String,
    val receiverRole: String,
    val amountNominalSets: Int,
    val reason: String
)

data class V22Block(
    val block: Int,
    val startWeek: Int,
    val endWeek: Int,
    val phase: String,
    val plannedReallocations: List<V22Reallocation>,
    val strengthPolicy: String,
    val hypertrophyPolicy: String,
    val recoveryPolicy: String,
    val plannedRoleSets: Map<String, Int>
)

data class V22Week(
    val week: Int,
    val block: Int,
    val sessions: List<V22Session>,
    val weeklyNominalSets: Int
)

data class V22PlanInput(
    val backup: String,
    val asOf: String,
    val weeks: Int,
    val days: Int,
    val mode: String,
    val focusStableKey: String?,
    val sessionMinutes: Int?
)

data class V22Authority(
    val stimulus: String,
    val prescription: String,
    val executionMissCarryover: Boolean,
    val structuralPlanningOnly: Boolean
)

data class V22Plan(
    val schema: String,
    val input: V22PlanInput,
    val historyEvidence: Map<String, V22RoleEvidence>,
    val blocks: List<V22Block>,
    val weeks: List<V22Week>,
    val baselineWeeklyNominalSets: Int,
    val authority: V22Authority,
    val limitations: List<String>
)

data class V22GeneratedProgram(
    val plan: V22Plan,
    val skeleton: GeneratedProgramSkeleton
)
