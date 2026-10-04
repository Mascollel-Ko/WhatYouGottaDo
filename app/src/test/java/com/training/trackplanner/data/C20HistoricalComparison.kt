package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.StimulusSelectionProgramComparison
import com.training.trackplanner.data.personalized.StimulusProductionGenerationResult

/** Restores the exact canonical EXP rows captured before C20 activation for prior-phase audits. */
internal fun c20PreActivationComparison(
    result: StimulusProductionGenerationResult
): StimulusSelectionProgramComparison {
    val comparison = requireNotNull(result.comparison) { "C20 result has no comparison" }
    val rows = result.incumbentPlacementShadow?.productionRows?.takeIf { it.isNotEmpty() }
        ?: comparison.experimental.items
    return comparison.copy(experimental = comparison.experimental.copy(items = rows))
}
