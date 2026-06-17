package com.training.trackplanner

data class RestTimerState(
    val runId: Long = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val endAtEpochMillis: Long = 0,
    val exerciseName: String = "",
    val nextHint: String = "",
    val hasNextTarget: Boolean = false,
    val startedAfterConfirmedSet: Boolean = false,
    val targetRecordDate: String = "",
    val targetEntryId: Long = 0,
    val targetSetId: Long = 0,
    val notificationPermissionNeeded: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val appInForeground: Boolean = true
) {
    val isActive: Boolean
        get() = isRunning || isFinished

    companion object {
        val Idle = RestTimerState()
    }
}
