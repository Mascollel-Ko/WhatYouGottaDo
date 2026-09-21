package com.training.trackplanner.data.personalized

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseRoleRelationCatalog
import com.training.trackplanner.data.RuntimeExerciseMetadata
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlannerActivityDomainResolverEquivalenceTest {
    @Test
    fun snapshotAndExplicitResolverPathsAgreeForEveryCanonicalMetadataEntry() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = CanonicalExerciseMetadataRepository(context)
        val exercises = repository.exercises(includeHistory = true).associateBy(Exercise::stableKey)
        val metadata = repository.runtimeMetadataCatalog().all().associateBy(RuntimeExerciseMetadata::stableKey)
        val roleCatalog = ExerciseRoleRelationCatalog.of(
            repository.trainingRoleRelations(),
            repository.programSlotCapabilityRelations()
        )
        val supportiveObjectives = repository.badmintonObjectiveCatalog().allRelations()
            .filter { it.transferLevel == BadmintonObjectiveTransferLevel.SUPPORTIVE }
            .groupBy { it.exerciseStableKey }
            .mapValues { (_, relations) -> relations.mapTo(mutableSetOf()) { it.objective.name } }

        metadata.forEach { (mapKey, value) ->
            assertEquals("production metadata map key must equal RuntimeExerciseMetadata.stableKey", mapKey, value.stableKey)
        }
        val snapshot = PlanningHistorySnapshot(
            cutoff = LocalDate.of(2026, 9, 20),
            allConfirmedSets = emptyList(),
            exercises = exercises,
            metadata = metadata,
            badmintonObjectives = emptyMap(),
            profilePrimaryGoal = "GENERAL_FITNESS",
            strengthTrainingYears = 0.0,
            badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences(),
            badmintonSupportiveObjectives = supportiveObjectives,
            exerciseRoleCatalog = roleCatalog
        )

        val resolver = PlannerActivityDomainResolver()
        metadata.keys.forEach { stableKey ->
            val exercise = requireNotNull(exercises[stableKey]) { "canonical exercise missing for $stableKey" }
            val snapshotResult = resolver.resolve(snapshot, stableKey)
            val explicitResult = resolver.resolve(
                exercise = exercise,
                metadata = metadata.getValue(stableKey),
                exerciseRoleCatalog = roleCatalog,
                supportiveObjectives = supportiveObjectives[stableKey].orEmpty()
            )
            assertEquals("resolver paths differ for $stableKey", snapshotResult, explicitResult)
        }
    }
}
