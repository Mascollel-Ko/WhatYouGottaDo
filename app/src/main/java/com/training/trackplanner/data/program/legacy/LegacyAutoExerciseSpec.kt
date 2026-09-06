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

internal enum class LegacyAutoMainArea(val label: String) {
    LOWER_ANTERIOR("Lower anterior"),
    LOWER_POSTERIOR("Lower posterior"),
    CHEST("Chest"),
    SHOULDER("Shoulder"),
    BACK("Back")
}
internal enum class LegacyAutoIntensityLabel {
    HIGH_LOW,
    MEDIUM_LOW,
    MEDIUM_MEDIUM,
    LOW_HIGH,
    DELOAD
}

internal enum class LegacyAutoAutoSlotType(val label: String) {
    MAIN("Main"),
    STRENGTH_ACCESSORY("Strength accessory"),
    BADMINTON_ACCESSORY("Badminton accessory")
}

internal enum class LegacyAutoStrengthAccessoryClass {
    PAIRED_MAIN_ACCESSORY,
    SMALL_PART_ACCESSORY
}

internal enum class LegacyAutoSmallPart(val label: String) {
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARM("Forearm"),
    CALF("Calf")
}

internal enum class LegacyAutoBadmintonCategory(val label: String) {
    STEP("Step"),
    ACCELERATION("Acceleration"),
    DECELERATION("Deceleration"),
    REACTION("Change of direction / reaction"),
    ANTI_ROTATION("Anti-rotation"),
    ROTATION_GENERATION("Rotation generation")
}

internal data class LegacyAutoExerciseSpec(
    val displayName: String,
    val slotType: LegacyAutoAutoSlotType,
    val stableKey: String,
    val mainArea: LegacyAutoMainArea? = null,
    val strengthAccessoryClass: LegacyAutoStrengthAccessoryClass? = null,
    val strengthBodyPart: LegacyAutoSmallPart? = null,
    val pairedMainArea: LegacyAutoMainArea? = null,
    val badmintonCategory: LegacyAutoBadmintonCategory? = null,
    val substitutionGroup: String? = null
) {
    val category: String
        get() = when (slotType) {
            LegacyAutoAutoSlotType.MAIN,
            LegacyAutoAutoSlotType.STRENGTH_ACCESSORY -> "근력운동"
            LegacyAutoAutoSlotType.BADMINTON_ACCESSORY -> "기능성운동"
        }
}

internal data class ResolvedLegacyAutoExercise(
    val spec: LegacyAutoExerciseSpec,
    val exerciseStableKey: String,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int,
    val stableKey: String
)

internal fun LegacyAutoExerciseSpec.resolve(exercises: List<Exercise>): ResolvedLegacyAutoExercise {
    val requestedKey = requireNotNull(stableKey.takeIf(String::isNotBlank)) {
        "Built-in program exercise '$displayName' must declare a canonical stableKey."
    }
    require(LegacyAutoCandidateAuthority.allows(requestedKey)) {
        "Built-in program exercise '$displayName' is not in the explicit LegacyAutoRuleTables candidate authority."
    }
    val matched = requireNotNull(exercises.firstOrNull { it.stableKey == requestedKey }) {
        "Built-in program exercise '$displayName' cannot resolve stableKey '$requestedKey'."
    }
    return ResolvedLegacyAutoExercise(
        spec = this,
        exerciseStableKey = matched.stableKey,
        exerciseName = matched.name,
        category = matched.category.ifBlank { category },
        restSeconds = matched.defaultRestSeconds,
        stableKey = matched.stableKey
    )
}

private fun LegacyAutoExerciseSpec.defaultRestSeconds(): Int =
    when (slotType) {
        LegacyAutoAutoSlotType.MAIN -> 120
        LegacyAutoAutoSlotType.STRENGTH_ACCESSORY -> 75
        LegacyAutoAutoSlotType.BADMINTON_ACCESSORY -> 60
    }
