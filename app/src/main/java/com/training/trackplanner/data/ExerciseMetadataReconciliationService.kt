package com.training.trackplanner.data

import android.content.Context
import androidx.room.withTransaction

internal class ExerciseMetadataReconciliationService(
    private val context: Context,
    private val db: TrainingDatabase,
    private val exerciseDao: ExerciseDao,
    private val runtimeMetadataDao: RuntimeExerciseMetadataDao,
    private val relationDao: ExerciseRoleRelationDao,
    private val overrideDao: ExerciseMetadataUserOverrideDao,
    private val appMetaDao: AppMetaDao,
    private val canonicalRepository: CanonicalExerciseMetadataRepository,
    private val seedExercisesByStableKey: () -> Map<String, Exercise>
) {
    suspend fun markRequiredIfNeeded(): String {
        val revision = ExerciseMetadataRevisionPolicy.project(context, canonicalRepository)
            .semanticCanonicalMetadataRevision
        if (appMetaDao.value(COMPLETED_KEY) != revision) {
            appMetaDao.upsert(AppMeta(REQUIRED_KEY, revision))
        }
        return revision
    }

    suspend fun reconcileIfRequired(expectedRevision: String? = null) {
        val revision = expectedRevision ?: markRequiredIfNeeded()
        if (appMetaDao.value(COMPLETED_KEY) == revision) return
        db.withTransaction {
            require(appMetaDao.value(REQUIRED_KEY) == revision) {
                "Metadata reconciliation marker changed before reconciliation."
            }
            val seeds = seedExercisesByStableKey()
            val runtimeByKey = runtimeMetadataDao.all().associateBy(RuntimeExerciseMetadataEntity::stableKey)
            val rolesByKey = relationDao.allTrainingRoles()
                .groupBy(ExerciseTrainingRoleRelation::exerciseStableKey)
            val slotsByKey = relationDao.allProgramSlotCapabilities()
                .groupBy(ExerciseProgramSlotCapabilityRelation::exerciseStableKey)
            val canonicalRoles = canonicalRepository.trainingRoleRelations()
                .groupBy(ExerciseTrainingRoleRelation::exerciseStableKey)
                .mapValues { (_, rows) -> rows.mapTo(sortedSetOf(), ExerciseTrainingRoleRelation::trainingRoleCode) }
            val canonicalSlots = canonicalRepository.programSlotCapabilityRelations()
                .groupBy(ExerciseProgramSlotCapabilityRelation::exerciseStableKey)
                .mapValues { (_, rows) -> rows.mapTo(sortedSetOf(), ExerciseProgramSlotCapabilityRelation::capabilityCode) }
            val resolver = ExerciseMetadataEffectiveStateResolver(
                canonicalExercisesByStableKey = seeds,
                canonicalRuntimeMetadataCatalog = canonicalRepository.runtimeMetadataCatalog(),
                canonicalTrainingRolesByStableKey = canonicalRoles,
                canonicalProgramSlotsByStableKey = canonicalSlots
            )
            exerciseDao.allExercises().forEach { exercise ->
                if (exercise.stableKey !in seeds) return@forEach
                val state = resolver.resolve(
                    materializedExercise = exercise,
                    materializedRuntimeMetadata = runtimeByKey[exercise.stableKey]?.toRuntimeMetadata(),
                    materializedTrainingRoles = rolesByKey[exercise.stableKey].orEmpty()
                        .mapTo(sortedSetOf(), ExerciseTrainingRoleRelation::trainingRoleCode),
                    materializedProgramSlotCapabilities = slotsByKey[exercise.stableKey].orEmpty()
                        .mapTo(sortedSetOf(), ExerciseProgramSlotCapabilityRelation::capabilityCode),
                    overrides = overrideDao.findByStableKey(exercise.stableKey)
                )
                exerciseDao.updateExercise(state.exercise)
                runtimeMetadataDao.upsert(state.runtimeMetadata.toEntity())
                relationDao.deleteTrainingRoles(exercise.stableKey)
                relationDao.deleteProgramSlotCapabilities(exercise.stableKey)
                relationDao.upsertTrainingRoles(state.trainingRoles.map { role ->
                    ExerciseTrainingRoleRelation(
                        exerciseStableKey = exercise.stableKey,
                        trainingRoleCode = role,
                        provenance = "CANONICAL_EFFECTIVE",
                        reviewStatus = "APPROVED"
                    )
                })
                relationDao.upsertProgramSlotCapabilities(state.programSlotCapabilities.map { capability ->
                    ExerciseProgramSlotCapabilityRelation(
                        exerciseStableKey = exercise.stableKey,
                        capabilityCode = capability,
                        provenance = "CANONICAL_EFFECTIVE",
                        reviewStatus = "APPROVED"
                    )
                })
            }
            appMetaDao.upsert(AppMeta(COMPLETED_KEY, revision))
        }
    }

    companion object {
        const val REQUIRED_KEY = "metadata_override_reconciliation_required"
        const val COMPLETED_KEY = "metadata_override_reconciliation_completed"
    }
}
