package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise

enum class BEquipmentIncrementKind { BARBELL, DUMBBELL, MACHINE, UNKNOWN }

/**
 * Units use the weight displayed by the app: a barbell adds one plate per
 * side; dumbbells and selectorized machines use user-entered increments
 * directly. Unreviewed equipment does not silently inherit barbell rules.
 */
internal data class BBlockEquipmentIncrements(
    val plateOneSideKg: Double,
    val dumbbellDisplayedKg: Double,
    val machineDisplayedKg: Double
) {
    init {
        require(plateOneSideKg.isFinite() && plateOneSideKg in 0.1..25.0)
        require(dumbbellDisplayedKg.isFinite() && dumbbellDisplayedKg in 0.1..50.0)
        require(machineDisplayedKg.isFinite() && machineDisplayedKg in 0.1..100.0)
    }
    fun incrementFor(kind: BEquipmentIncrementKind): Double? = when (kind) {
        BEquipmentIncrementKind.BARBELL -> plateOneSideKg * 2.0
        BEquipmentIncrementKind.DUMBBELL -> dumbbellDisplayedKg
        BEquipmentIncrementKind.MACHINE -> machineDisplayedKg
        BEquipmentIncrementKind.UNKNOWN -> null
    }

    companion object {
        fun kind(key: String, exercise: Exercise?): BEquipmentIncrementKind {
            val tags = listOfNotNull(exercise?.equipment, exercise?.equipmentTags)
                .joinToString(" ").uppercase()
            // Exclude genuinely ambiguous equipment instead of assuming a step.
            val barbell = "BARBELL" in tags || "바벨" in tags || key.startsWith("barbell_")
            val dumbbell = "DUMBBELL" in tags || "덤벨" in tags || key.startsWith("dumbbell_")
            val machine = "MACHINE" in tags || "머신" in tags || "CABLE" in tags ||
                "케이블" in tags || "SELECTORIZED" in tags || key.startsWith("machine_")
            return when {
                listOf(barbell, dumbbell, machine).count { it } != 1 -> BEquipmentIncrementKind.UNKNOWN
                barbell -> BEquipmentIncrementKind.BARBELL
                dumbbell -> BEquipmentIncrementKind.DUMBBELL
                else -> BEquipmentIncrementKind.MACHINE
            }
        }
    }
}
