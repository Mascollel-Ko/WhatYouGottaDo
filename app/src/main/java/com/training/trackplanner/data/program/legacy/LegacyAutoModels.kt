package com.training.trackplanner.data.program.legacy

// Mechanically isolated from f5cc0ac7e0ba58cf21be81ec83e90d1c619921f9.
// Frozen product rules: do not generalize or route through another planner.
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseDao
import com.training.trackplanner.data.ProgramOptimizationSummary
import com.training.trackplanner.data.ProgramUserNotice
import com.training.trackplanner.data.ProgramUserNoticeCode
import com.training.trackplanner.data.ProgramUserNoticeLevel
import com.training.trackplanner.data.ProgramSetPrescription


enum class LegacyAutoGoal {
    BADMINTON_SUPPORT,
    STRENGTH,
    BODYBUILDING,
    FUNCTIONAL_CONDITIONING
}

enum class LegacyAutoPeriodizationType {
    AUTO,
    STEP_DELOAD,
    BADMINTON_WAVE,
    DAILY_UNDULATING,
    LINEAR_STRENGTH
}

enum class LegacyAutoWeekType {
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

enum class LegacyAutoTrainingSlot {
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

enum class LegacyAutoDayIntensity { HARD, MODERATE, LIGHT }

enum class LegacyAutoFatigueBand { GREEN, YELLOW, ORANGE, RED }

enum class LegacyAutoVarietyPreference { LOW, NORMAL, HIGH }

enum class BadmintonEventProfile {
    SINGLES,
    DOUBLES,
    MIXED_DOUBLES,
    ALL_ROUND,
    NOT_SPECIFIED
}

data class LegacyAutoRequest(
    val name: String,
    val goal: LegacyAutoGoal,
    val weeklyTrainingDays: Int,
    val sessionMinutes: Int,
    val availableEquipment: Set<String>,
    val excludedExerciseText: String,
    val badmintonTransferRatio: Double,
    val sportStrengthRatio: String,
    val periodizationType: LegacyAutoPeriodizationType,
    val durationWeeks: Int = 4,
    val badmintonEventProfile: BadmintonEventProfile = BadmintonEventProfile.NOT_SPECIFIED,
    val varietyPreference: LegacyAutoVarietyPreference = LegacyAutoVarietyPreference.NORMAL,
    val excludedExerciseStableKeys: Set<String> = emptySet(),
    val preferredExerciseStableKeys: Set<String> = emptySet()
) {
    val availableDaysPerWeek: Int get() = weeklyTrainingDays.coerceIn(1, 7)
    val dailyAvailableMinutes: Int get() = sessionMinutes.coerceIn(15, 120)
    val badmintonSpecificityRatio: Int get() = (badmintonTransferRatio.coerceIn(0.0, 0.9) * 100).toInt()
}

data class LegacyAutoWeekPlan(
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

data class LegacyAutoSkeletonItem(
    val localId: String,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val orderIndex: Int,
    val exerciseStableKey: String,
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
    val trainingSlot: String = LegacyAutoTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name,
    val dayIntensity: String = LegacyAutoDayIntensity.MODERATE.name,
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
    val slotCapabilitySource: String = "NONE",
    val slotCapabilityConfidence: String = "NONE",
    val slotCapabilityWarnings: List<String> = emptyList(),
    val requestedTemplateSlot: String = "",
    val requiredTemplateAnchor: Boolean = false,
    val setPrescriptions: List<ProgramSetPrescription> = emptyList()
)

data class LegacyAutoSkeleton(
    val suggestedName: String,
    val durationDays: Int,
    val request: LegacyAutoRequest,
    val periodizationType: LegacyAutoPeriodizationType,
    val weekPlans: List<LegacyAutoWeekPlan>,
    val items: List<LegacyAutoSkeletonItem>,
    val weekDaySchedule: Map<Int, Set<Int>> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val optimizationSummary: ProgramOptimizationSummary = ProgramOptimizationSummary(),
    val templateId: String = "POLICY_FALLBACK",
    val representativeTemplate: Boolean = false
)
