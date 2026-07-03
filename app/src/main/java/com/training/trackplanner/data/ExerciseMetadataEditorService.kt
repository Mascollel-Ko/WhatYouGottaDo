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
    suspend fun exerciseEditorData(exerciseId: Long?): ExerciseRuntimeMetadataEditorData {
        val persistedRows = runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        val resolver = RuntimeExerciseMetadataResolver(canonicalRuntimeMetadataCatalog, persistedRows)
        val options = RuntimeMetadataEditorOptions.from(
            canonicalRuntimeMetadataCatalog.all() + persistedRows
        )
        val exercise = exerciseId?.let { exerciseDao.findById(it) }
            ?: Exercise(
                name = "",
                category = "\uADFC\uB825\uC6B4\uB3D9",
                stableKey = "",
                isCustom = true
            )
        val metadata = if (exerciseId == null) {
            RuntimeExerciseMetadataDefaults.forIdentity("", "")
        } else {
            resolver.resolve(exercise)
        }
        val copySources = exerciseDao.allExercises()
            .asSequence()
            .filter { source -> source.id != exercise.id && source.name.isNotBlank() }
            .sortedBy { source -> source.name }
            .map { source -> ExerciseMetadataCopySource(source, resolver.resolve(source)) }
            .toList()
        return ExerciseRuntimeMetadataEditorData(exercise, metadata, options, copySources)
    }

    suspend fun saveExerciseEditor(data: ExerciseRuntimeMetadataEditorData): Long {
        require(data.exercise.name.isNotBlank()) { "\uC6B4\uB3D9 \uC774\uB984\uC744 \uC785\uB825\uD558\uC138\uC694." }
        require(data.exercise.category.isNotBlank()) { "\uBD84\uB958\uB97C \uC785\uB825\uD558\uC138\uC694." }
        require(data.exercise.defaultRestSeconds in 0..3600) { "\uD734\uC2DD \uC2DC\uAC04\uC740 0~3600\uCD08\uB85C \uC785\uB825\uD558\uC138\uC694." }
        return db.withTransaction {
            val existing = data.exercise.id.takeIf { it > 0 }?.let { exerciseDao.findById(it) }
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
            savedExercise.id
        }
    }

    suspend fun resetExerciseMetadataOverride(exerciseId: Long): Boolean =
        db.withTransaction {
            val exercise = exerciseDao.findById(exerciseId) ?: return@withTransaction false
            runtimeExerciseMetadataDao.deleteByStableKey(exercise.stableKey)
            val seed = seedExercisesByStableKey()[ExerciseMetadataOverrideBackupMapper.overrideKey(exercise.stableKey)]
            if (seed != null) {
                exerciseDao.updateExercise(
                    seed.copy(
                        id = exercise.id,
                        stableKey = seed.stableKey,
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

    suspend fun resolvedRuntimeMetadataByExerciseId(): Map<Long, RuntimeExerciseMetadata> {
        val exercises = exerciseDao.allExercises()
        val catalog = resolvedRuntimeMetadataCatalog(exercises)
        return exercises.associate { exercise ->
            exercise.id to (catalog.resolve(exercise) ?: RuntimeExerciseMetadataDefaults.forExercise(exercise))
        }
    }

    suspend fun resolvedRuntimeMetadataCatalog(
        exercises: List<Exercise>
    ): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataResolver(
            canonicalRuntimeMetadataCatalog,
            runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        ).catalog(exercises)

    suspend fun setExerciseActive(exerciseId: Long, active: Boolean) {
        val exercise = exerciseDao.findById(exerciseId) ?: return
        exerciseDao.updateExercise(
            exercise.copy(
                isActive = active,
                archivedAt = if (active) null else System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteExerciseIfUnused(exerciseId: Long): ExerciseDeleteResult =
        db.withTransaction {
            val exercise = exerciseDao.findById(exerciseId) ?: return@withTransaction ExerciseDeleteResult(
                deleted = false,
                referenced = false
            )
            val referenced = workoutDao.countEntriesForExercise(exerciseId) > 0 ||
                programDao.countProgramItemsForExercise(exerciseId) > 0
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
        repeat(USER_KEY_RETRY_LIMIT) {
            val candidate = draft.copy(
                id = 0,
                stableKey = uniqueUserExerciseStableKey(),
                isCustom = true
            )
            val id = exerciseDao.insertExercise(candidate)
            if (id > 0) return candidate.copy(id = id)
        }
        error("\uC0AC\uC6A9\uC790 \uC6B4\uB3D9 \uC2DD\uBCC4\uC790\uB97C \uC0DD\uC131\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.")
    }
}
