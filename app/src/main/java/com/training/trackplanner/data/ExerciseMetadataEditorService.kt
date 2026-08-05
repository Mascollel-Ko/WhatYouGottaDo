package com.training.trackplanner.data

import androidx.room.withTransaction

private const val USER_KEY_RETRY_LIMIT = 8

internal class ExerciseMetadataEditorService(
    private val db: TrainingDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
    private val seedExercisesByStableKey: () -> Map<String, Exercise>
) {
    suspend fun exerciseEditorData(exerciseStableKey: String?): ExerciseRuntimeMetadataEditorData {
        val persistedRows = runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        val resolver = RuntimeExerciseMetadataResolver(canonicalRuntimeMetadataCatalog, persistedRows)
        val options = RuntimeMetadataEditorOptions.from(
            canonicalRuntimeMetadataCatalog.all() + persistedRows
        )
        val exercise = exerciseStableKey?.let { exerciseDao.findByStableKey(it) }
            ?: Exercise(
                name = "",
                category = "\uADFC\uB825\uC6B4\uB3D9",
                stableKey = "",
                isCustom = true
            )
        val metadata = if (exerciseStableKey == null) {
            RuntimeExerciseMetadataDefaults.forIdentity("", "")
        } else {
            resolver.resolve(exercise)
        }
        val copySources = exerciseDao.allExercises()
            .asSequence()
            .filter { source -> source.stableKey != exercise.stableKey && source.name.isNotBlank() }
            .sortedBy { source -> source.name }
            .map { source -> ExerciseMetadataCopySource(source, resolver.resolve(source)) }
            .toList()
        return ExerciseRuntimeMetadataEditorData(exercise, metadata, options, copySources)
    }

    suspend fun saveExerciseEditor(data: ExerciseRuntimeMetadataEditorData): String {
        require(data.exercise.name.isNotBlank()) { "\uC6B4\uB3D9 \uC774\uB984\uC744 \uC785\uB825\uD558\uC138\uC694." }
        require(data.exercise.category.isNotBlank()) { "\uBD84\uB958\uB97C \uC785\uB825\uD558\uC138\uC694." }
        require(data.exercise.defaultRestSeconds in 0..3600) { "\uD734\uC2DD \uC2DC\uAC04\uC740 0~3600\uCD08\uB85C \uC785\uB825\uD558\uC138\uC694." }
        return db.withTransaction {
            val existing = data.exercise.stableKey
                .takeIf(String::isNotBlank)
                ?.let { exerciseDao.findByStableKey(it) }
            val savedExercise = if (existing == null) {
                insertUserExerciseWithUniqueKey(data.exercise)
            } else {
                val stableKey = existing.stableKey.ifBlank {
                    uniqueUserExerciseStableKey()
                }
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
            savedExercise.stableKey
        }
    }

    suspend fun resetExerciseMetadataOverride(exerciseStableKey: String): Boolean =
        db.withTransaction {
            val exercise = exerciseDao.findByStableKey(exerciseStableKey) ?: return@withTransaction false
            runtimeExerciseMetadataDao.deleteByStableKey(exercise.stableKey)
            val seed = seedExercisesByStableKey()[ExerciseMetadataOverrideBackupMapper.overrideKey(exercise.stableKey)]
            if (seed != null) {
                exerciseDao.updateExercise(
                    seed.copy(
                        stableKey = exercise.stableKey,
                        imageAssetName = seed.imageAssetName.ifBlank { exercise.imageAssetName },
                        isActive = exercise.isActive,
                        archivedAt = exercise.archivedAt,
                        isCustom = false,
                        needsReview = exercise.needsReview || seed.needsReview
                    )
                )
            }
            true
        }

    suspend fun resolveRuntimeMetadata(exercise: Exercise): RuntimeExerciseMetadata =
        RuntimeExerciseMetadataResolver(
            canonicalRuntimeMetadataCatalog,
            runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        ).resolve(exercise)

    suspend fun resolvedRuntimeMetadataByExerciseStableKey(): Map<String, RuntimeExerciseMetadata> {
        val exercises = exerciseDao.allExercises()
        val catalog = resolvedRuntimeMetadataCatalog(exercises)
        return exercises.associate { exercise ->
            exercise.stableKey to (catalog.resolve(exercise) ?: RuntimeExerciseMetadataDefaults.forExercise(exercise))
        }
    }

    suspend fun resolvedRuntimeMetadataCatalog(
        exercises: List<Exercise>
    ): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataResolver(
            canonicalRuntimeMetadataCatalog,
            runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        ).catalog(exercises)

    suspend fun setExerciseActive(exerciseStableKey: String, active: Boolean) {
        val exercise = exerciseDao.findByStableKey(exerciseStableKey) ?: return
        if (active && seedExercisesByStableKey()[ExerciseMetadataOverrideBackupMapper.overrideKey(exerciseStableKey)]
                ?.planningEligibility == "HISTORY_ONLY") return
        exerciseDao.updateExercise(
            exercise.copy(
                isActive = active,
                archivedAt = if (active) null else System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteExerciseIfUnused(exerciseStableKey: String): ExerciseDeleteResult =
        db.withTransaction {
            val exercise = exerciseDao.findByStableKey(exerciseStableKey) ?: return@withTransaction ExerciseDeleteResult(
                deleted = false,
                referenced = false
            )
            val referenced = workoutDao.countEntriesForExercise(exerciseStableKey) > 0 ||
                programDao.countProgramItemsForExercise(exerciseStableKey) > 0
            if (referenced || !exercise.isCustom) {
                return@withTransaction ExerciseDeleteResult(deleted = false, referenced = true)
            }
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

    private suspend fun insertUserExerciseWithUniqueKey(draft: Exercise): Exercise {
        val candidate = draft.copy(
            stableKey = uniqueUserExerciseStableKey(),
            isCustom = true
        )
        exerciseDao.insertExercise(candidate)
        return candidate
    }
}
