package com.training.trackplanner.data

internal enum class ProgramMainArea(val label: String) {
    LOWER_ANTERIOR("Lower anterior"),
    LOWER_POSTERIOR("Lower posterior"),
    CHEST("Chest"),
    SHOULDER("Shoulder"),
    BACK("Back")
}

internal enum class ProgramIntensityLabel {
    HIGH_LOW,
    MEDIUM_LOW,
    MEDIUM_MEDIUM,
    LOW_HIGH,
    DELOAD
}

internal enum class ProgramAutoSlotType(val label: String) {
    MAIN("Main"),
    STRENGTH_ACCESSORY("Strength accessory"),
    BADMINTON_ACCESSORY("Badminton accessory")
}

internal enum class ProgramStrengthAccessoryClass {
    PAIRED_MAIN_ACCESSORY,
    SMALL_PART_ACCESSORY
}

internal enum class ProgramSmallPart(val label: String) {
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARM("Forearm"),
    CALF("Calf")
}

internal enum class ProgramBadmintonCategory(val label: String) {
    STEP("Step"),
    ACCELERATION("Acceleration"),
    DECELERATION("Deceleration"),
    REACTION("Change of direction / reaction"),
    ANTI_ROTATION("Anti-rotation"),
    ROTATION_GENERATION("Rotation generation")
}

internal data class ProgramExerciseSpec(
    val displayName: String,
    val slotType: ProgramAutoSlotType,
    val stableKey: String? = null,
    val mainArea: ProgramMainArea? = null,
    val strengthAccessoryClass: ProgramStrengthAccessoryClass? = null,
    val strengthBodyPart: ProgramSmallPart? = null,
    val pairedMainArea: ProgramMainArea? = null,
    val badmintonCategory: ProgramBadmintonCategory? = null,
    val substitutionGroup: String? = null
) {
    val category: String
        get() = when (slotType) {
            ProgramAutoSlotType.MAIN,
            ProgramAutoSlotType.STRENGTH_ACCESSORY -> "근력운동"
            ProgramAutoSlotType.BADMINTON_ACCESSORY -> "기능성운동"
        }
}

internal data class ResolvedProgramExercise(
    val spec: ProgramExerciseSpec,
    val exerciseId: Long,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int,
    val stableKey: String
)

internal fun ProgramExerciseSpec.resolve(exercises: List<Exercise>): ResolvedProgramExercise {
    val matched = stableKey?.let { key -> exercises.firstOrNull { it.stableKey == key } }
        ?: exercises.firstOrNull { it.name.normalizedExerciseName() == displayName.normalizedExerciseName() }
        ?: exercises.firstOrNull {
            val actual = it.name.normalizedExerciseName()
            val wanted = displayName.normalizedExerciseName()
            actual.contains(wanted) || wanted.contains(actual)
        }
    return ResolvedProgramExercise(
        spec = this,
        exerciseId = matched?.id ?: 0L,
        exerciseName = matched?.name ?: displayName,
        category = matched?.category?.ifBlank { category } ?: category,
        restSeconds = matched?.defaultRestSeconds ?: defaultRestSeconds(),
        stableKey = matched?.stableKey?.ifBlank { stableKey.orEmpty() } ?: stableKey.orEmpty()
    )
}

private fun ProgramExerciseSpec.defaultRestSeconds(): Int =
    when (slotType) {
        ProgramAutoSlotType.MAIN -> 120
        ProgramAutoSlotType.STRENGTH_ACCESSORY -> 75
        ProgramAutoSlotType.BADMINTON_ACCESSORY -> 60
    }

private fun String.normalizedExerciseName(): String =
    lowercase()
        .replace(" ", "")
        .replace("/", "")
        .replace("-", "")
        .replace("·", "")
