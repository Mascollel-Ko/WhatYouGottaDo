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

internal object LegacyAutoCandidateAuthority {
    val mainStableKeysByArea: Map<LegacyAutoMainArea, Set<String>> =
        LegacyAutoRuleTables.mainExercises.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), LegacyAutoExerciseSpec::stableKey) }

    val pairedAccessoryStableKeysByArea: Map<LegacyAutoMainArea, Set<String>> =
        LegacyAutoRuleTables.pairedAccessories.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), LegacyAutoExerciseSpec::stableKey) }

    val smallAccessoryStableKeysByPart: Map<LegacyAutoSmallPart, Set<String>> =
        LegacyAutoRuleTables.smallPartAccessories.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), LegacyAutoExerciseSpec::stableKey) }

    val badmintonAccessoryStableKeysByCategory: Map<LegacyAutoBadmintonCategory, Set<String>> =
        LegacyAutoRuleTables.badmintonAccessories.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), LegacyAutoExerciseSpec::stableKey) }

    val allAllowedStableKeys: Set<String> = buildSet {
        mainStableKeysByArea.values.forEach(::addAll)
        pairedAccessoryStableKeysByArea.values.forEach(::addAll)
        smallAccessoryStableKeysByPart.values.forEach(::addAll)
        badmintonAccessoryStableKeysByCategory.values.forEach(::addAll)
    }

    fun allows(stableKey: String): Boolean =
        stableKey.isNotBlank() && stableKey in allAllowedStableKeys
}
