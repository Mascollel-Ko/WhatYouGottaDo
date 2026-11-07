package com.training.trackplanner

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.BackupRestoreImpact
import com.training.trackplanner.data.BackupRestorePreparation
import com.training.trackplanner.data.ExerciseListRestoreMode
import com.training.trackplanner.data.WorkoutRestoreMode
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreDialogUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun zeroOverlapSkipsWorkoutChoiceAndUsesAppendInternally() {
        val state = BackupRestorePreparation(
            hasOverlappingWorkoutDates = false,
            impact = BackupRestoreImpact()
        ).initialUiState()

        assertTrue(state is BackupRestoreUiState.ChooseExerciseMode)
        assertEquals(
            WorkoutRestoreMode.APPEND_TO_CURRENT,
            (state as BackupRestoreUiState.ChooseExerciseMode).workoutMode
        )
    }

    @Test
    fun workoutChoiceShowsExactlyTheTwoRestoreActions() {
        content(BackupRestoreUiState.ChooseWorkoutMode(BackupRestoreImpact(overlappingWorkoutDateCount = 2)))

        compose.onNodeWithText(context.getString(R.string.restore_workout_replace)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.restore_workout_append)).assertIsDisplayed()
    }

    @Test
    fun customDefinitionReplacementIsDisclosedInSecondQuestion() {
        val impact = customReplacementImpact()
        val warning = context.getString(R.string.restore_custom_definition_warning, 2)

        content(
            BackupRestoreUiState.ChooseExerciseMode(
                WorkoutRestoreMode.APPEND_TO_CURRENT,
                impact
            )
        )
        compose.onNodeWithText(warning).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.restore_exercise_preserve)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.restore_exercise_apply)).assertIsDisplayed()
    }

    @Test
    fun customDefinitionReplacementIsDisclosedAgainInFinalConfirmation() {
        val warning = context.getString(R.string.restore_custom_definition_warning, 2)
        content(
            BackupRestoreUiState.Confirm(
                WorkoutRestoreMode.APPEND_TO_CURRENT,
                ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES,
                customReplacementImpact()
            )
        )
        compose.onNodeWithText(warning).assertIsDisplayed()
        compose.onAllNodesWithText(
            context.getString(R.string.restore_metadata_reset_warning, 0)
        ).assertCountEquals(0)
    }

    @Test
    fun finalConfirmationDisclosesMetadataResetAndSameSourceDivergence() {
        val impact = BackupRestoreImpact(
            currentMetadataOverrideFieldsThatWouldBeRemovedCount = 3,
            sameSourceIdentityDifferentContentCount = 2
        )
        content(
            BackupRestoreUiState.Confirm(
                WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES,
                ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST,
                impact
            )
        )

        compose.onNodeWithText(
            context.getString(R.string.restore_metadata_reset_warning, 3)
        ).assertIsDisplayed()
        compose.onNodeWithText(
            context.getString(R.string.restore_same_source_divergence_note, 2)
        ).assertIsDisplayed()
    }

    @Test
    fun malformedRestoreFailureUsesLocalizedMessageInsteadOfInternalDiagnostics() {
        content(BackupRestoreUiState.Failed(BackupRestoreFailureReason.MALFORMED_BACKUP))
        compose.onNodeWithText(context.getString(R.string.restore_malformed_backup)).assertIsDisplayed()
        compose.onAllNodesWithText(
            "Backup contains contradictory workout rows for one immutable source identity."
        ).assertCountEquals(0)
    }

    @Test
    fun stalePreflightFailureUsesLocalizedMessage() {
        content(BackupRestoreUiState.Failed(BackupRestoreFailureReason.STALE_PREFLIGHT))
        compose.onNodeWithText(context.getString(R.string.restore_stale_preflight)).assertIsDisplayed()
    }


    @Test
    fun cloudConflictCancelIsBelowBothFullWidthChoices() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                CloudBackupConflictDialog(
                    cloudTime = "2026-11-07 21:36",
                    cloudCurrentAvailable = true,
                    onKeepLocal = {},
                    onKeepCloud = {},
                    onCancel = {}
                )
            }
        }
        assertBelow(
            R.string.restore_cancel,
            R.string.cloud_conflict_local_wins,
            R.string.cloud_conflict_cloud_wins
        )
    }

    @Test
    fun workoutRestoreCancelIsBelowBothChoices() {
        content(BackupRestoreUiState.ChooseWorkoutMode(BackupRestoreImpact(overlappingWorkoutDateCount = 2)))
        assertBelow(
            R.string.restore_cancel,
            R.string.restore_workout_replace,
            R.string.restore_workout_append
        )
    }

    @Test
    fun exerciseRestoreCancelIsBelowChoicesAndBack() {
        content(
            BackupRestoreUiState.ChooseExerciseMode(
                WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES,
                customReplacementImpact()
            )
        )
        assertBelow(
            R.string.restore_cancel,
            R.string.restore_exercise_preserve,
            R.string.restore_exercise_apply,
            R.string.restore_back
        )
    }

    @Test
    fun finalRestoreCancelIsBelowConfirmAndBack() {
        content(
            BackupRestoreUiState.Confirm(
                WorkoutRestoreMode.APPEND_TO_CURRENT,
                ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES,
                customReplacementImpact()
            )
        )
        assertBelow(R.string.restore_cancel, R.string.restore_confirm, R.string.restore_back)
    }

    private fun assertBelow(cancelTextRes: Int, vararg precedingTextRes: Int) {
        val cancel = compose.onNodeWithText(context.getString(cancelTextRes))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        precedingTextRes.forEach { res ->
            val action = compose.onNodeWithText(context.getString(res))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Cancel must be completely below ${context.getString(res)}, not on its row",
                cancel.top >= action.bottom
            )
        }
    }

    private fun customReplacementImpact() = BackupRestoreImpact(
        overlappingWorkoutDateCount = 2,
        representedExerciseCount = 7,
        backupOverrideFieldsThatWouldReplaceCurrentCount = 3,
        sameStableKeyCustomDefinitionsThatWouldBeReplacedCount = 2
    )

    private fun content(state: BackupRestoreUiState) {
        compose.setContent {
            TrainingTrackPlannerTheme {
                BackupRestoreDialogHost(
                    state = state,
                    onWorkoutMode = {},
                    onExerciseMode = {},
                    onConfirm = {},
                    onBackToWorkout = {},
                    onBackToExercise = {},
                    onCancel = {}
                )
            }
        }
    }
}
