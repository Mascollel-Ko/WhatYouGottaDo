package com.training.trackplanner.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupAppMetaPolicyTest {
    @Test
    fun generatedInventoryIsDeterministicAndOnlyPlannerUserStateIsPortable() {
        val artifact = File("../docs/generated/app_meta_portability_classification.csv")
        val rows = artifact.readLines(Charsets.UTF_8).filter(String::isNotBlank)
        assertEquals("keyPattern,authority,purpose", rows.first())
        assertEquals(15, rows.drop(1).size)
        assertEquals(rows.drop(1).sorted(), rows.drop(1))
        assertEquals(setOf("LOCAL_INFRASTRUCTURE_STATE", "PORTABLE_USER_STATE"), rows.drop(1).map { it.split(',')[1] }.toSet())

        listOf(
            PersonalizedProgramPlanningService.PREFERENCES_KEY,
            "${PersonalizedProgramPlanningService.DECISION_PREFIX}example"
        ).forEach { key ->
            assertEquals(BackupAppMetaAuthority.PORTABLE_USER_STATE, BackupAppMetaPolicy.authority(key))
        }

        val representativeKeys = listOf(
            "unknown_future_key",
            "data_transfer_report_example",
            "exercise_seed_version",
            ExerciseMetadataReconciliationService.COMPLETED_KEY,
            ExerciseMetadataReconciliationService.REQUIRED_KEY,
            "program_seed_version",
            "program_stable_key_repair_version",
            WorkoutSourceIdentityProvider.SOURCE_DATABASE_LINEAGE_ID,
            StrengthModelRevisionPolicy.REBUILD_MARKER_KEY,
            StrengthPosteriorUpdateCoordinator.REBUILD_REQUIRED_KEY,
            StrengthModelRevisionPolicy.OBSOLETE_REBUILD_MARKER_KEY,
            StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY,
            StrengthPosteriorUpdateCoordinator.RESTORE_PROVENANCE_KEY
        )
        representativeKeys.forEach { key ->
            assertEquals(BackupAppMetaAuthority.LOCAL_INFRASTRUCTURE_STATE, BackupAppMetaPolicy.authority(key))
            assertFalse(BackupAppMetaPolicy.isSourceOverwriteAllowed(key))
        }
    }
}
