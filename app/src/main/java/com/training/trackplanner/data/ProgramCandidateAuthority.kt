package com.training.trackplanner.data

internal object ProgramCandidateAuthority {
    val mainStableKeysByArea: Map<ProgramMainArea, Set<String>> =
        ProgramRuleTables.mainExercises.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), ProgramExerciseSpec::stableKey) }

    val pairedAccessoryStableKeysByArea: Map<ProgramMainArea, Set<String>> =
        ProgramRuleTables.pairedAccessories.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), ProgramExerciseSpec::stableKey) }

    val smallAccessoryStableKeysByPart: Map<ProgramSmallPart, Set<String>> =
        ProgramRuleTables.smallPartAccessories.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), ProgramExerciseSpec::stableKey) }

    val badmintonAccessoryStableKeysByCategory: Map<ProgramBadmintonCategory, Set<String>> =
        ProgramRuleTables.badmintonAccessories.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), ProgramExerciseSpec::stableKey) }

    val allAllowedStableKeys: Set<String> = buildSet {
        mainStableKeysByArea.values.forEach(::addAll)
        pairedAccessoryStableKeysByArea.values.forEach(::addAll)
        smallAccessoryStableKeysByPart.values.forEach(::addAll)
        badmintonAccessoryStableKeysByCategory.values.forEach(::addAll)
    }

    fun allows(stableKey: String): Boolean =
        stableKey.isNotBlank() && stableKey in allAllowedStableKeys
}
