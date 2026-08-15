package com.training.trackplanner.data

import androidx.room.withTransaction

private const val USER_KEY_RETRY_LIMIT = 8

internal class ExerciseMetadataEditorService(
    private val db: TrainingDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val exerciseRoleRelationDao: ExerciseRoleRelationDao,
    private val overrideDao: ExerciseMetadataUserOverrideDao,
    private val canonicalMetadataRepository: CanonicalExerciseMetadataRepository,
    private val seedExercisesByStableKey: () -> Map<String, Exercise>,
    private val semanticRevision: () -> String
) {
    suspend fun exerciseEditorData(exerciseStableKey: String?): ExerciseRuntimeMetadataEditorData {
        val exercises = exerciseDao.allExercises()
        val persistedByKey = runtimeExerciseMetadataDao.all().associateBy(RuntimeExerciseMetadataEntity::stableKey)
        val overridesByKey = overrideDao.all().groupBy(ExerciseMetadataUserOverrideEntity::stableKey)
        val rolesByKey = exerciseRoleRelationDao.allTrainingRoles()
            .groupBy(ExerciseTrainingRoleRelation::exerciseStableKey)
        val slotsByKey = exerciseRoleRelationDao.allProgramSlotCapabilities()
            .groupBy(ExerciseProgramSlotCapabilityRelation::exerciseStableKey)
        val resolver = effectiveResolver()
        val effectiveByKey = exercises.associate { exercise ->
            exercise.stableKey to resolver.resolve(
                materializedExercise = exercise,
                materializedRuntimeMetadata = persistedByKey[exercise.stableKey]?.toRuntimeMetadata(),
                materializedTrainingRoles = rolesByKey[exercise.stableKey].orEmpty()
                    .mapTo(sortedSetOf(), ExerciseTrainingRoleRelation::trainingRoleCode),
                materializedProgramSlotCapabilities = slotsByKey[exercise.stableKey].orEmpty()
                    .mapTo(sortedSetOf(), ExerciseProgramSlotCapabilityRelation::capabilityCode),
                overrides = overridesByKey[exercise.stableKey].orEmpty()
            )
        }
        val state = exerciseStableKey?.let(effectiveByKey::get)
        val exercise = state?.exercise ?: Exercise(
            name = "",
            category = "\uADFC\uB825\uC6B4\uB3D9",
            stableKey = "",
            isCustom = true
        )
        val metadata = state?.runtimeMetadata ?: RuntimeExerciseMetadataDefaults.forIdentity("", "")
        val options = RuntimeMetadataEditorOptions.from(
            canonicalMetadataRepository.runtimeMetadataCatalog().all() + effectiveByKey.values.map { it.runtimeMetadata }
        )
        val copySources = effectiveByKey.values
            .asSequence()
            .filter { source -> source.exercise.stableKey != exercise.stableKey && source.exercise.name.isNotBlank() }
            .sortedBy { source -> source.exercise.name }
            .map { source -> ExerciseMetadataCopySource(source.exercise, source.runtimeMetadata) }
            .toList()
        return ExerciseRuntimeMetadataEditorData(
            exercise = exercise,
            metadata = metadata,
            options = options,
            copySources = copySources,
            originalExercise = exercise,
            originalMetadata = metadata
        )
    }

    suspend fun saveExerciseEditor(data: ExerciseRuntimeMetadataEditorData): String = db.withTransaction {
        val existing = data.exercise.stableKey
            .takeIf(String::isNotBlank)
            ?.let { exerciseDao.findByStableKey(it) }
        val canonical = existing?.stableKey?.let { seedExercisesByStableKey()[it] }
        val submittedName = canonical?.name ?: data.exercise.name
        require(submittedName.isNotBlank()) { "\uC6B4\uB3D9 \uC774\uB984\uC744 \uC785\uB825\uD558\uC138\uC694." }
        require(data.exercise.category.isNotBlank()) { "\uBD84\uB958\uB97C \uC785\uB825\uD558\uC138\uC694." }
        require(data.exercise.defaultRestSeconds in 0..3600) {
            "\uD734\uC2DD \uC2DC\uAC04\uC740 0~3600\uCD08\uB85C \uC785\uB825\uD558\uC138\uC694."
        }
        if (existing == null || canonical == null || existing.isCustom) {
            val savedExercise = if (existing == null) {
                insertUserExerciseWithUniqueKey(data.exercise)
            } else {
                val stableKey = existing.stableKey.ifBlank { uniqueUserExerciseStableKey() }
                ExerciseStableKeyPolicy.preserveOnEdit(existing, data.exercise, stableKey)
                    .also { exerciseDao.updateExercise(it) }
            }
            runtimeExerciseMetadataDao.upsert(
                data.metadata.copy(
                    stableKey = savedExercise.stableKey,
                    exerciseName = savedExercise.name,
                    safeForSeedMutation = false
                ).toEntity()
            )
            return@withTransaction savedExercise.stableKey
        }

        val submittedExercise = data.exercise.copy(
            stableKey = canonical.stableKey,
            name = canonical.name,
            isCustom = false,
            isActive = existing.isActive,
            archivedAt = existing.archivedAt,
            needsReview = existing.needsReview
        )
        val submittedMetadata = data.metadata.copy(
            stableKey = canonical.stableKey,
            exerciseName = canonical.name,
            safeForSeedMutation = false
        )
        val originalRows = editableRows(data.originalExercise, data.originalMetadata)
        val submittedRows = editableRows(submittedExercise, submittedMetadata)
        val canonicalRuntime = requireNotNull(canonicalMetadataRepository.runtimeMetadataCatalog().resolve(canonical)) {
            "Canonical runtime metadata is missing for ${canonical.stableKey}."
        }
        val canonicalRows = editableRows(canonical, canonicalRuntime)
        submittedRows.forEach { (fieldKey, submitted) ->
            if (submitted.value == originalRows[fieldKey]?.value) return@forEach
            val definition = checkNotNull(ExerciseMetadataFieldPolicyRegistry.definition(fieldKey))
            if (submitted.value == canonicalRows[fieldKey]?.value) {
                overrideDao.deleteField(canonical.stableKey, definition.fieldScope.name, fieldKey)
            } else {
                overrideDao.upsert(
                    ExerciseMetadataUserOverrideEntity(
                        stableKey = canonical.stableKey,
                        fieldScope = definition.fieldScope.name,
                        fieldKey = fieldKey,
                        valueEncoding = definition.valueEncoding.name,
                        value = submitted.value,
                        isExplicitEmpty = submitted.value.isEmpty(),
                        source = ExerciseMetadataOverrideSource.USER_EDIT.name,
                        semanticCanonicalRevisionAtEdit = semanticRevision()
                    )
                )
            }
        }
        materializeBuiltIn(existing, overrideDao.findByStableKey(canonical.stableKey))
        canonical.stableKey
    }

    suspend fun resetExerciseMetadataOverride(exerciseStableKey: String): Boolean = db.withTransaction {
        val exercise = exerciseDao.findByStableKey(exerciseStableKey) ?: return@withTransaction false
        overrideDao.deleteForStableKey(exerciseStableKey)
        val canonical = seedExercisesByStableKey()[exerciseStableKey]
        if (canonical == null || exercise.isCustom) {
            runtimeExerciseMetadataDao.deleteByStableKey(exerciseStableKey)
        } else {
            materializeBuiltIn(exercise, emptyList())
        }
        true
    }

    suspend fun resolveRuntimeMetadata(exercise: Exercise): RuntimeExerciseMetadata =
        effectiveResolver().resolve(
            materializedExercise = exercise,
            materializedRuntimeMetadata = runtimeExerciseMetadataDao.findByStableKey(exercise.stableKey)
                ?.toRuntimeMetadata(),
            overrides = overrideDao.findByStableKey(exercise.stableKey)
        ).runtimeMetadata

    suspend fun resolvedRuntimeMetadataByExerciseStableKey(): Map<String, RuntimeExerciseMetadata> {
        val exercises = exerciseDao.allExercises()
        val catalog = resolvedRuntimeMetadataCatalog(exercises)
        return exercises.associate { exercise ->
            exercise.stableKey to (catalog.resolve(exercise) ?: RuntimeExerciseMetadataDefaults.forExercise(exercise))
        }
    }

    suspend fun resolvedRuntimeMetadataCatalog(exercises: List<Exercise>): RuntimeExerciseMetadataCatalog {
        val persistedByKey = runtimeExerciseMetadataDao.all().associateBy(RuntimeExerciseMetadataEntity::stableKey)
        val overridesByKey = overrideDao.all().groupBy(ExerciseMetadataUserOverrideEntity::stableKey)
        val resolver = effectiveResolver()
        return RuntimeExerciseMetadataCatalog.of(exercises.map { exercise ->
            resolver.resolve(
                materializedExercise = exercise,
                materializedRuntimeMetadata = persistedByKey[exercise.stableKey]?.toRuntimeMetadata(),
                overrides = overridesByKey[exercise.stableKey].orEmpty()
            ).runtimeMetadata
        })
    }

    suspend fun setExerciseActive(exerciseStableKey: String, active: Boolean) {
        val exercise = exerciseDao.findByStableKey(exerciseStableKey) ?: return
        if (active && seedExercisesByStableKey()[exerciseStableKey]?.planningEligibility == "HISTORY_ONLY") return
        exerciseDao.updateExercise(
            exercise.copy(
                isActive = active,
                archivedAt = if (active) null else System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteExerciseIfUnused(exerciseStableKey: String): ExerciseDeleteResult = db.withTransaction {
        val exercise = exerciseDao.findByStableKey(exerciseStableKey)
            ?: return@withTransaction ExerciseDeleteResult(deleted = false, referenced = false)
        val referenced = workoutDao.countEntriesForExercise(exerciseStableKey) > 0 ||
            programDao.countProgramItemsForExercise(exerciseStableKey) > 0
        if (referenced || !exercise.isCustom) {
            return@withTransaction ExerciseDeleteResult(deleted = false, referenced = true)
        }
        overrideDao.deleteForStableKey(exercise.stableKey)
        runtimeExerciseMetadataDao.deleteByStableKey(exercise.stableKey)
        exerciseDao.deleteExercise(exercise)
        ExerciseDeleteResult(deleted = true, referenced = false)
    }

    suspend fun uniqueUserExerciseStableKey(): String {
        repeat(USER_KEY_RETRY_LIMIT) {
            val candidate = UserExerciseStableKeyGenerator.generate()
            if (exerciseDao.findByStableKey(candidate) == null) return candidate
        }
        error("\uC0AC\uC6A9\uC790 \uC6B4\uB3D9 \uC2DD\uBCC4\uC790 \uCDA9\uB3CC\uC744 \uD574\uACB0\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.")
    }

    private suspend fun materializeBuiltIn(
        existing: Exercise,
        overrides: List<ExerciseMetadataUserOverrideEntity>
    ) {
        val state = effectiveResolver().resolve(
            materializedExercise = existing,
            materializedRuntimeMetadata = runtimeExerciseMetadataDao.findByStableKey(existing.stableKey)
                ?.toRuntimeMetadata(),
            overrides = overrides
        )
        exerciseDao.updateExercise(state.exercise)
        runtimeExerciseMetadataDao.upsert(state.runtimeMetadata.toEntity())
        exerciseRoleRelationDao.deleteTrainingRoles(existing.stableKey)
        exerciseRoleRelationDao.deleteProgramSlotCapabilities(existing.stableKey)
        exerciseRoleRelationDao.upsertTrainingRoles(state.trainingRoles.map { role ->
            ExerciseTrainingRoleRelation(existing.stableKey, role, "CANONICAL_EFFECTIVE", "APPROVED")
        })
        exerciseRoleRelationDao.upsertProgramSlotCapabilities(state.programSlotCapabilities.map { capability ->
            ExerciseProgramSlotCapabilityRelation(existing.stableKey, capability, "CANONICAL_EFFECTIVE", "APPROVED")
        })
    }

    private fun effectiveResolver(): ExerciseMetadataEffectiveStateResolver =
        ExerciseMetadataEffectiveStateResolver(
            canonicalExercisesByStableKey = seedExercisesByStableKey(),
            canonicalRuntimeMetadataCatalog = canonicalMetadataRepository.runtimeMetadataCatalog(),
            canonicalTrainingRolesByStableKey = canonicalMetadataRepository.trainingRoleRelations()
                .groupBy(ExerciseTrainingRoleRelation::exerciseStableKey)
                .mapValues { (_, rows) -> rows.mapTo(sortedSetOf(), ExerciseTrainingRoleRelation::trainingRoleCode) },
            canonicalProgramSlotsByStableKey = canonicalMetadataRepository.programSlotCapabilityRelations()
                .groupBy(ExerciseProgramSlotCapabilityRelation::exerciseStableKey)
                .mapValues { (_, rows) ->
                    rows.mapTo(sortedSetOf(), ExerciseProgramSlotCapabilityRelation::capabilityCode)
                }
        )

    private fun editableRows(
        exercise: Exercise,
        metadata: RuntimeExerciseMetadata
    ): Map<String, ExerciseMetadataSnapshotRow> = ExerciseMetadataFieldPolicyRegistry.snapshot(
        ExerciseMetadataSnapshotSource(exercise, metadata, emptySet(), emptySet())
    ).filter { row -> ExerciseMetadataFieldPolicyRegistry.definition(row.fieldKey)?.editorWritable == true }
        .associateBy(ExerciseMetadataSnapshotRow::fieldKey)

    private suspend fun insertUserExerciseWithUniqueKey(draft: Exercise): Exercise {
        val candidate = draft.copy(stableKey = uniqueUserExerciseStableKey(), isCustom = true)
        exerciseDao.insertExercise(candidate)
        return candidate
    }
}
