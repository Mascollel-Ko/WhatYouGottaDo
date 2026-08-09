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
