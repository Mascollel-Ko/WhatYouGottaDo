package com.training.trackplanner

import android.content.Context
import com.training.trackplanner.localization.LocalizedPresentation

data class RestTimerState(
    val runId: Long = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val endAtEpochMillis: Long = 0,
    val exerciseStableKey: String = "",
    val storedExerciseName: String = "",
    val nextSetNumber: Int? = null,
    // Legacy presentation fields remain for previously persisted active timers.
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

internal object RestTimerPresentation {
    fun exerciseName(context: Context, state: RestTimerState): String {
        val fallback = state.storedExerciseName.ifBlank { state.exerciseName }
        return if (state.exerciseStableKey.isNotBlank()) {
            LocalizedPresentation.exerciseName(context, state.exerciseStableKey, fallback)
        } else {
            LocalizedPresentation.uiText(context, fallback)
        }
    }

    fun nextSetHint(context: Context, state: RestTimerState): String {
        val exercise = exerciseName(context, state)
        state.nextSetNumber?.takeIf { it > 0 }?.let { setNumber ->
            return context.getString(R.string.rest_timer_overlay_next_set, exercise, setNumber)
        }
        val legacy = state.nextHint
        val match = Regex("""^(.+)\s+(\d+)세트 준비$""").matchEntire(legacy)
        if (match != null) {
            val legacyName = if (state.exerciseStableKey.isNotBlank()) exercise
            else LocalizedPresentation.uiText(context, match.groupValues[1])
            return context.getString(R.string.rest_timer_overlay_next_set, legacyName, match.groupValues[2].toInt())
        }
        return LocalizedPresentation.uiText(context, legacy).ifBlank { exercise }
    }
}
