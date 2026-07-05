package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import java.time.LocalDate

enum class ProgramGoal {
    BADMINTON_SUPPORT,
    STRENGTH,
    BODYBUILDING,
    FUNCTIONAL_CONDITIONING
}

enum class ProgramPeriodizationType {
    AUTO,
    STEP_DELOAD,
    BADMINTON_WAVE,
    DAILY_UNDULATING,
    LINEAR_STRENGTH
}

enum class ProgramWeekType {
    ADAPT,
    BUILD,
    HIGH,
    DELOAD,
    REBUILD,
    BUILD_PLUS,
    INTENSIFY,
    FINAL_DELOAD,
    REALIZATION
}

enum class ProgramTrainingSlot {
    FULL_BODY_BADMINTON_SUPPORT,
    LOWER_TRANSFER_FULL,
    UPPER_SCAP_CORE_FULL,
    LOWER_STRENGTH,
    UPPER_STRENGTH_SCAP,
    BADMINTON_TRANSFER,
    BADMINTON_COD_DECEL,
    RECOVERY_WEAKPOINT,
    LOWER_STRENGTH_HEAVY,
    POWER_REACTIVE_LIGHT,
    UPPER_STRENGTH,
    BADMINTON_COD,
    POWER_REACTIVE,
    WEAKPOINT_ACCESSORY,
    RECOVERY_PREHAB,
    MICRO_RECOVERY
}

enum class ProgramDayIntensity { HARD, MODERATE, LIGHT }

enum class ProgramFatigueBand { GREEN, YELLOW, ORANGE, RED }

enum class ProgramVarietyPreference { LOW, NORMAL, HIGH }

enum class ProgramValidationSeverity { HARD, WARNING, SOFT_PENALTY }

data class ProgramValidationIssue(
    val code: String,
    val severity: ProgramValidationSeverity,
    val message: String
) {
    fun render(): String = "[${severity.name}] $code: $message"
}

enum class BadmintonEventProfile {
    SINGLES,
    DOUBLES,
    MIXED_DOUBLES,
    ALL_ROUND,
    NOT_SPECIFIED
}

data class ProgramSkeletonRequest(
    val name: String,
    val goal: ProgramGoal,
    val weeklyTrainingDays: Int,
    val sessionMinutes: Int,
    val availableEquipment: Set<String>,
    val excludedExerciseText: String,
    val badmintonTransferRatio: Double,
    val sportStrengthRatio: String,
    val periodizationType: ProgramPeriodizationType,
    val durationWeeks: Int = 4,
    val badmintonEventProfile: BadmintonEventProfile = BadmintonEventProfile.NOT_SPECIFIED,
    val varietyPreference: ProgramVarietyPreference = ProgramVarietyPreference.NORMAL,
    val excludedExerciseStableKeys: Set<String> = emptySet(),
    val preferredExerciseStableKeys: Set<String> = emptySet()
) {
    val availableDaysPerWeek: Int get() = weeklyTrainingDays.coerceIn(1, 7)
    val dailyAvailableMinutes: Int get() = sessionMinutes.coerceIn(15, 120)
    val badmintonSpecificityRatio: Int get() = (badmintonTransferRatio.coerceIn(0.0, 0.9) * 100).toInt()
}

data class ProgramWeekPlan(
    val weekIndex: Int,
    val weekType: String,
    val volumeMultiplier: Double,
    val intensityMultiplier: Double,
    val heavyExposureLimit: Int,
    val lowerBodyFatigueLimit: Double,
    val axialLoadLimit: Int,
    val plyometricLimit: Int,
    val deloadFlag: Boolean,
    val targetRpeMin: Double = 6.0,
    val targetRpeMax: Double = 8.0
)

data class ProgramSkeletonItem(
    val localId: String,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val orderIndex: Int,
    val exerciseId: Long,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int,
    val prescription: String,
    val setCount: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int,
    val selectionReason: String,
    val weightSource: String,
    val trainingSlot: String = ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name,
    val dayIntensity: String = ProgramDayIntensity.MODERATE.name,
    val stableKey: String = "",
    val selectionRole: String = "",
    val movementFamily: String = "",
    val movementSubtype: String = "",
    val metadataProgramSlot: String = "",
    val redundancyGroup: String = "",
    val strengthProgressionGroup: String = "",
    val primaryStressProfile: String = "",
    val stressMagnitudeHint: String = "",
    val neuromuscularStressLevel: String = "",
    val systemicMuscularStressLevel: String = "",
    val localMuscularStressLevel: String = "",
    val jointTendonImpactStressLevel: String = "",
    val movementFocusDemandLevel: String = "",
    val recoveryDurationClass: String = "",
    val badmintonTransferLevel: String = "",
    val estimatedDurationSeconds: Int = 0,
    val directSportSession: Boolean = false,
    val rehabLikeActivation: Boolean = false,
    val scapularStabilityExposure: Boolean = false,
    val primarySlotCapabilities: List<String> = emptyList(),
    val secondarySlotCapabilities: List<String> = emptyList(),
    val weakSlotCapabilities: List<String> = emptyList(),
    val slotCapabilitySource: String = SlotCapabilitySource.NONE.name,
    val slotCapabilityConfidence: String = SlotCapabilityConfidence.NONE.name,
    val slotCapabilityWarnings: List<String> = emptyList(),
    val requestedTemplateSlot: String = "",
    val requiredTemplateAnchor: Boolean = false
)

data class GeneratedProgramSkeleton(
    val suggestedName: String,
    val durationDays: Int,
    val request: ProgramSkeletonRequest,
    val periodizationType: ProgramPeriodizationType,
    val weekPlans: List<ProgramWeekPlan>,
    val items: List<ProgramSkeletonItem>,
    val weekDaySchedule: Map<Int, Set<Int>> = emptyMap(),
    val candidateTraces: List<ProgramCandidateTrace> = emptyList(),
    val warnings: List<String> = emptyList(),
    val validationIssues: List<String> = emptyList(),
    val validationDetails: List<ProgramValidationIssue> = emptyList(),
    val evaluation: ProgramEvaluation? = null,
    val optimizationSummary: ProgramOptimizationSummary = ProgramOptimizationSummary(),
    val optimizationTrace: List<ProgramOptimizationTrace> = emptyList(),
    val templateId: String = "POLICY_FALLBACK",
    val representativeTemplate: Boolean = false
)

data class ProgramCandidateTrace(
    val weekNumber: Int,
    val dayOfWeek: Int,
    val requestedTemplateSlot: String,
    val selectedMainReservationStableKey: String = "",
    val captainChairBlockedCount: Int = 0,
    val captainChairBlockReason: String = "",
    val role: String,
    val allActive: Int,
    val programSelectable: Int,
    val equipmentMatched: Int,
    val notExcludedByUser: Int,
    val capabilityMatched: Int,
    val repeatAllowed: Int,
    val fatigueAllowed: Int,
    val templateAllowed: Int,
    val sessionAllowed: Int,
    val scored: Int,
    val selectionPool: Int,
    val selected: Int,
    val warnings: List<String> = emptyList(),
    val scoreAdjustments: List<ProgramCandidateScoreTrace> = emptyList()
)

data class ProgramCandidateScoreTrace(
    val exerciseName: String,
    val stableKey: String,
    val baseScore: Double,
    val contextRerankScore: Double,
    val selectedMainBoostApplied: Boolean,
    val captainChairPenaltyApplied: Boolean,
    val finalScore: Double,
    val hardGatePassed: Boolean = true,
    val exclusionStage: String = "",
    val exclusionReason: String = "",
    val selectionWindowIncluded: Boolean = false,
    val selected: Boolean = false
)

/** Compatibility entry point retained for the existing repository and editor. */
class ProgramSkeletonGenerator(
    private val builder: ProgramBuilder = ProgramBuilder()
) {
    fun generate(
        request: ProgramSkeletonRequest,
        exercises: List<Exercise>,
        history: List<WorkoutEntryWithSets>,
        today: LocalDate = LocalDate.now(),
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
        fatigueState: DailyFatigueState? = null
    ): GeneratedProgramSkeleton = builder.build(
        request = request,
        exercises = exercises,
        history = history,
        today = today,
        runtimeMetadataCatalog = runtimeMetadataCatalog,
        fatigueState = fatigueState
    )
}
