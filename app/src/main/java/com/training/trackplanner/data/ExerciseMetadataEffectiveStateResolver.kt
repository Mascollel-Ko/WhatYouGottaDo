package com.training.trackplanner.data

data class ExerciseMetadataEffectiveState(
    val exercise: Exercise,
    val runtimeMetadata: RuntimeExerciseMetadata,
    val trainingRoles: Set<String>,
    val programSlotCapabilities: Set<String>
)

internal class ExerciseMetadataEffectiveStateResolver(
    private val canonicalExercisesByStableKey: Map<String, Exercise>,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
    private val canonicalTrainingRolesByStableKey: Map<String, Set<String>> = emptyMap(),
    private val canonicalProgramSlotsByStableKey: Map<String, Set<String>> = emptyMap()
) {
    fun resolve(
        materializedExercise: Exercise,
        materializedRuntimeMetadata: RuntimeExerciseMetadata?,
        materializedTrainingRoles: Set<String> = emptySet(),
        materializedProgramSlotCapabilities: Set<String> = emptySet(),
        overrides: List<ExerciseMetadataUserOverrideEntity> = emptyList()
    ): ExerciseMetadataEffectiveState {
        val canonicalExercise = canonicalExercisesByStableKey[materializedExercise.stableKey]
            ?: return ExerciseMetadataEffectiveState(
                exercise = materializedExercise,
                runtimeMetadata = materializedRuntimeMetadata
                    ?: RuntimeExerciseMetadataDefaults.forExercise(materializedExercise),
                trainingRoles = materializedTrainingRoles,
                programSlotCapabilities = materializedProgramSlotCapabilities
            )
        val validatedOverrides = overrides
            .onEach(ExerciseMetadataUserOverrideEntity::validated)
            .sortedWith(compareBy({ it.fieldScope }, { it.fieldKey }))
        require(validatedOverrides.all { it.stableKey == canonicalExercise.stableKey }) {
            "Metadata override stableKey does not match effective-state identity."
        }
        val baseExercise = canonicalExercise.copy(
            isActive = materializedExercise.isActive,
            archivedAt = materializedExercise.archivedAt,
            isCustom = false,
            needsReview = materializedExercise.needsReview || canonicalExercise.needsReview
        )
        val canonicalRuntime = canonicalRuntimeMetadataCatalog.resolve(canonicalExercise)
            ?: RuntimeExerciseMetadataDefaults.forExercise(canonicalExercise)
        val restored = ExerciseMetadataFieldPolicyRegistry.restore(
            exercise = baseExercise,
            runtimeMetadata = canonicalRuntime,
            rows = validatedOverrides
                .filter { row ->
                    row.fieldScope != ExerciseMetadataFieldScope.TRAINING_ROLE_RELATION.name &&
                        row.fieldScope != ExerciseMetadataFieldScope.PROGRAM_SLOT_CAPABILITY_RELATION.name
                }
                .map(ExerciseMetadataUserOverrideEntity::toSnapshotRow)
        )
        val historyOnly = canonicalExercise.planningEligibility == "HISTORY_ONLY" ||
            canonicalRuntime.planningEligibility == "HISTORY_ONLY"
        val effectiveExercise = restored.exercise.copy(
            stableKey = canonicalExercise.stableKey,
            name = canonicalExercise.name,
            planningEligibility = if (historyOnly) "HISTORY_ONLY" else restored.exercise.planningEligibility,
            isActive = if (historyOnly) false else materializedExercise.isActive,
            archivedAt = materializedExercise.archivedAt,
            isCustom = false,
            needsReview = materializedExercise.needsReview || canonicalExercise.needsReview
        )
        val effectiveRuntime = restored.runtimeMetadata.copy(
            stableKey = canonicalExercise.stableKey,
            exerciseName = canonicalExercise.name,
            planningEligibility = if (historyOnly) "HISTORY_ONLY" else restored.runtimeMetadata.planningEligibility,
            safeForSeedMutation = false,
            appCueProfile = canonicalRuntime.appCueProfile
        )
        val overrideRows = validatedOverrides.map(ExerciseMetadataUserOverrideEntity::toSnapshotRow)
        return ExerciseMetadataEffectiveState(
            exercise = effectiveExercise,
            runtimeMetadata = effectiveRuntime,
            trainingRoles = ExerciseMetadataFieldPolicyRegistry.relationValues(
                overrideRows,
                "relation.trainingRoles"
            ) ?: canonicalTrainingRolesByStableKey[canonicalExercise.stableKey].orEmpty(),
            programSlotCapabilities = ExerciseMetadataFieldPolicyRegistry.relationValues(
                overrideRows,
                "relation.programSlotCapabilities"
            ) ?: canonicalProgramSlotsByStableKey[canonicalExercise.stableKey].orEmpty()
        )
    }
}
