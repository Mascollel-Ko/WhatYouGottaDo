package com.training.trackplanner

import com.training.trackplanner.data.BackupRestoreImpact
import com.training.trackplanner.data.BackupRestorePreparation
import com.training.trackplanner.data.ExerciseListRestoreMode
import com.training.trackplanner.data.WorkoutRestoreMode

sealed interface BackupRestoreUiState {
    data object Idle : BackupRestoreUiState
    data object Preparing : BackupRestoreUiState
    data class ChooseWorkoutMode(val impact: BackupRestoreImpact) : BackupRestoreUiState
    data class ChooseExerciseMode(
        val workoutMode: WorkoutRestoreMode,
        val impact: BackupRestoreImpact
    ) : BackupRestoreUiState
    data class Confirm(
        val workoutMode: WorkoutRestoreMode,
        val exerciseMode: ExerciseListRestoreMode,
        val impact: BackupRestoreImpact
    ) : BackupRestoreUiState
    data object Restoring : BackupRestoreUiState
    data class Failed(val reason: BackupRestoreFailureReason) : BackupRestoreUiState
}

enum class BackupRestoreFailureReason {
    MALFORMED_BACKUP,
    STALE_PREFLIGHT
}

internal fun BackupRestorePreparation.initialUiState(): BackupRestoreUiState =
    if (hasOverlappingWorkoutDates) {
        BackupRestoreUiState.ChooseWorkoutMode(impact)
    } else {
        BackupRestoreUiState.ChooseExerciseMode(
            workoutMode = WorkoutRestoreMode.APPEND_TO_CURRENT,
            impact = impact
        )
    }
