package com.training.trackplanner

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RestTimerSessionController(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val notifier = RestTimerNotifier(appContext)
    private val overlayController = RestTimerOverlayController(appContext)
    private val soundVibration = RestTimerSoundVibration(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(RestTimerState.Idle)
    private var timerJob: Job? = null
    private var backgroundRetryJob: Job? = null
    private var appInForeground = true
    private var nextRunId = 1L

    val state: StateFlow<RestTimerState> = _state

    init {
        restoreRestTimerState()
    }

    fun start(
        durationSeconds: Int,
        exerciseName: String,
        nextHint: String,
        targetRecordDate: String,
        targetEntryId: Long,
        targetSetId: Long = 0
    ) {
        if (durationSeconds <= 0) return

        timerJob?.cancel()
        backgroundRetryJob?.cancel()
        overlayController.resetDismissedForCurrentAwaySession()

        val runId = nextRunId++
        val endAt = System.currentTimeMillis() + durationSeconds * 1000L
        val started = RestTimerState(
            runId = runId,
            isRunning = true,
            remainingSeconds = durationSeconds,
            totalSeconds = durationSeconds,
            endAtEpochMillis = endAt,
            exerciseName = exerciseName,
            nextHint = nextHint,
            targetRecordDate = targetRecordDate,
            targetEntryId = targetEntryId,
            targetSetId = targetSetId
        )
        persist(started)
        publish(started, notifyRunning = true)
        launchCountdown(endAt)
    }

    fun stop() {
        timerJob?.cancel()
        backgroundRetryJob?.cancel()
        timerJob = null
        backgroundRetryJob = null
        preferences.edit().clear().apply()
        _state.value = RestTimerState.Idle.copy(
            notificationPermissionNeeded = notifier.needsNotificationPermission(),
            overlayPermissionGranted = overlayController.canDrawOverlays(),
            appInForeground = appInForeground
        )
        notifier.cancel()
        overlayController.remove()
    }

    fun onResume() {
        appInForeground = true
        backgroundRetryJob?.cancel()
        overlayController.resetDismissedForCurrentAwaySession()
        overlayController.remove()
        refreshPermissions()
    }

    fun onPause() {
        appInForeground = false
        refreshPermissions()
        scheduleBackgroundOverlayRetry()
    }

    fun onDestroy() {
        backgroundRetryJob?.cancel()
        overlayController.remove()
    }

    fun refreshPermissions() {
        _state.value = enrich(_state.value)
    }

    private fun restoreRestTimerState() {
        val endAt = preferences.getLong(KEY_REST_END_AT, 0L)
        val next = preferences.getString(KEY_REST_NEXT, "").orEmpty()
        val targetDate = preferences.getString(KEY_REST_TARGET_DATE, "").orEmpty()
        val targetEntryId = preferences.getLong(KEY_REST_TARGET_ENTRY_ID, 0L)
        val targetSetId = preferences.getLong(KEY_REST_TARGET_SET_ID, 0L)
        val finished = preferences.getBoolean(KEY_REST_FINISHED, false)
        if (endAt <= 0L && !finished) {
            refreshPermissions()
            return
        }

        val remaining = remainingSecondsUntil(endAt)
        val restored = RestTimerState(
            runId = nextRunId++,
            isRunning = !finished && remaining > 0,
            isFinished = finished || remaining <= 0,
            remainingSeconds = remaining,
            totalSeconds = remaining,
            endAtEpochMillis = endAt,
            nextHint = next,
            targetRecordDate = targetDate,
            targetEntryId = targetEntryId,
            targetSetId = targetSetId
        )
        publish(restored, notifyRunning = restored.isRunning)
        if (restored.isRunning) {
            launchCountdown(endAt)
        }
    }

    private fun launchCountdown(endAt: Long) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                val remaining = remainingSecondsUntil(endAt)
                if (remaining <= 0) {
                    finish()
                    break
                }
                publish(
                    _state.value.copy(
                        isRunning = true,
                        isFinished = false,
                        remainingSeconds = remaining
                    ),
                    notifyRunning = true
                )
                delay(1_000L)
            }
        }
    }

    private fun finish() {
        timerJob?.cancel()
        timerJob = null
        val finished = _state.value.copy(
            isRunning = false,
            isFinished = true,
            remainingSeconds = 0
        )
        persist(finished)
        publish(finished, notifyRunning = false)
        notifier.showFinished(_state.value)
        soundVibration.playFinishAlert()
    }

    private fun publish(state: RestTimerState, notifyRunning: Boolean) {
        val enriched = enrich(state)
        _state.value = enriched
        if (notifyRunning && enriched.isRunning) {
            notifier.showRunning(enriched)
        }
        if (!appInForeground && enriched.isActive) {
            overlayController.showOrUpdate(enriched)
        } else {
            overlayController.remove()
        }
    }

    private fun scheduleBackgroundOverlayRetry() {
        backgroundRetryJob?.cancel()
        backgroundRetryJob = scope.launch {
            delay(BACKGROUND_OVERLAY_RETRY_DELAY_MS)
            val current = _state.value
            if (!appInForeground && current.isActive) {
                overlayController.showOrUpdate(current)
            }
        }
    }

    private fun persist(state: RestTimerState) {
        preferences.edit()
            .putLong(KEY_REST_END_AT, state.endAtEpochMillis)
            .putString(KEY_REST_NEXT, state.nextHint)
            .putString(KEY_REST_TARGET_DATE, state.targetRecordDate)
            .putLong(KEY_REST_TARGET_ENTRY_ID, state.targetEntryId)
            .putLong(KEY_REST_TARGET_SET_ID, state.targetSetId)
            .putBoolean(KEY_REST_FINISHED, state.isFinished)
            .apply()
    }

    private fun enrich(state: RestTimerState): RestTimerState =
        state.copy(
            notificationPermissionNeeded = notifier.needsNotificationPermission(),
            overlayPermissionGranted = overlayController.canDrawOverlays(),
            appInForeground = appInForeground
        )

    private fun remainingSecondsUntil(endAtEpochMillis: Long): Int {
        val remainingMillis = endAtEpochMillis - System.currentTimeMillis()
        return ((remainingMillis + 999L) / 1000L).toInt().coerceAtLeast(0)
    }

    private companion object {
        const val PREFERENCES_NAME = "rest_timer"
        const val KEY_REST_END_AT = "rest_end_at"
        const val KEY_REST_NEXT = "rest_next"
        const val KEY_REST_TARGET_DATE = "rest_target_date"
        const val KEY_REST_TARGET_ENTRY_ID = "rest_target_entry_id"
        const val KEY_REST_TARGET_SET_ID = "rest_target_set_id"
        const val KEY_REST_FINISHED = "rest_finished"
        const val BACKGROUND_OVERLAY_RETRY_DELAY_MS = 450L
    }
}
