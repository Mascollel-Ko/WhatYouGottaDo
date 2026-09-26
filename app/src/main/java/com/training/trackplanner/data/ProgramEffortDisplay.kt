package com.training.trackplanner.data

import java.math.BigDecimal

/** Compact, deterministic display semantics for planned effort targets. */
data class ProgramEffortDisplay(
    val commonTargetRpeMin: Double?,
    val perSetTargetRpeMin: List<Double?>
)

internal fun programEffortDisplay(sets: List<ProgramSetPrescription>): ProgramEffortDisplay {
    val targets = sets.sortedBy(ProgramSetPrescription::setIndex).map { it.targetRpeMin.validatedTargetRpeMin() }
    val common = targets.distinct().singleOrNull()?.takeIf { targets.all { target -> target != null } }
    return ProgramEffortDisplay(common, if (common == null) targets else List(targets.size) { null })
}

internal fun plannedRpeLabel(targetRpeMin: Double?): String? = targetRpeMin.validatedTargetRpeMin()?.let {
    "RPE ${BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()}+"
}
