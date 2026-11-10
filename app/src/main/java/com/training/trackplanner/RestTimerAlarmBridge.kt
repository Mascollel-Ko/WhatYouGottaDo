package com.training.trackplanner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.lang.SecurityException

/**
 * Alarm delivery is independent of MainActivity and its coroutine countdown.
 * Android may still defer allow-while-idle alarms, especially two alarms 10s apart.
 */
internal object RestTimerAlarmPolicy {
    const val PREWARNING_MILLIS = 10_000L

    fun warningAt(endAtEpochMillis: Long, totalSeconds: Int): Long? =
        if (totalSeconds > 10) endAtEpochMillis - PREWARNING_MILLIS else null

    fun warningDue(nowMillis: Long, endAtEpochMillis: Long, totalSeconds: Int): Boolean =
        warningAt(endAtEpochMillis, totalSeconds)?.let { nowMillis in it until endAtEpochMillis } == true

    fun finishDue(nowMillis: Long, endAtEpochMillis: Long): Boolean =
        endAtEpochMillis > 0L && nowMillis >= endAtEpochMillis
}

internal class RestTimerAlarmBridge(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun needsExactAlarmPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()

    fun schedule(state: RestTimerState) {
        cancelScheduled()
        if (!state.isRunning || state.runId == 0L) return
        val now = System.currentTimeMillis()
        if (state.endAtEpochMillis <= now) return
        RestTimerAlarmPolicy.warningAt(state.endAtEpochMillis, state.totalSeconds)
            ?.takeIf { it > now }
            ?.let { scheduleAt(it, ACTION_WARNING, REQUEST_WARNING, state.runId) }
        scheduleAt(state.endAtEpochMillis, ACTION_FINISH, REQUEST_FINISH, state.runId)
    }

    fun cancelScheduled() {
        for ((action, request) in listOf(
            ACTION_WARNING to REQUEST_WARNING,
            ACTION_FINISH to REQUEST_FINISH
        )) {
            val pending = pendingIntent(action, request, 0L, PendingIntent.FLAG_NO_CREATE)
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun scheduleAt(atMillis: Long, action: String, request: Int, runId: Long) {
        val pending = pendingIntent(action, request, runId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (action == ACTION_WARNING) {
                // A prewarning must NOT consume the app's limited allow-while-idle
                // quota just 10 seconds before the more important completion.
                if (exactAllowed) alarmManager.setExact(AlarmManager.RTC_WAKEUP, atMillis, pending)
                else alarmManager.set(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                // Fallback is not second-precise. Settings UI explains this limitation.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } catch (_: SecurityException) {
            if (action == ACTION_WARNING) alarmManager.set(AlarmManager.RTC_WAKEUP, atMillis, pending)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun pendingIntent(action: String, request: Int, runId: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            appContext,
            request,
            Intent(appContext, RestTimerAlarmReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_RUN_ID, runId),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Claims are serialized across the UI countdown and receiver in this app process.
     * Persist the claim before side effects to prevent duplicate notifications.
     */
    fun deliverWarning(runId: Long) = deliver(runId, warning = true)

    fun deliverFinish(runId: Long) = deliver(runId, warning = false)

    private fun deliver(runId: Long, warning: Boolean) {
        val snapshot = synchronized(DELIVERY_LOCK) {
            val storedRunId = preferences.getLong(KEY_RUN_ID, 0L)
            val endAt = preferences.getLong(KEY_END_AT, 0L)
            val total = preferences.getInt(KEY_TOTAL_SECONDS, 0)
            val now = System.currentTimeMillis()
            if (runId <= 0L || storedRunId != runId || preferences.getBoolean(KEY_FINISHED, false)) {
                return@synchronized null
            }
            val deliveredKey = if (warning) KEY_WARNING_DELIVERED else KEY_FINISH_DELIVERED
            if (preferences.getBoolean(deliveredKey, false)) return@synchronized null
            val due = if (warning) RestTimerAlarmPolicy.warningDue(now, endAt, total)
                else RestTimerAlarmPolicy.finishDue(now, endAt)
            if (!due) return@synchronized null

            val state = RestTimerState(
                runId = storedRunId,
                isRunning = warning,
                isFinished = !warning,
                remainingSeconds = if (warning) ((endAt - now + 999L) / 1000L).toInt().coerceAtLeast(0) else 0,
                totalSeconds = total,
                endAtEpochMillis = endAt,
                exerciseStableKey = preferences.getString(KEY_EXERCISE_KEY, "").orEmpty(),
                storedExerciseName = preferences.getString(KEY_STORED_NAME, "").orEmpty(),
                exerciseName = preferences.getString(KEY_LEGACY_NAME, "").orEmpty(),
                nextSetNumber = preferences.getInt(KEY_NEXT_SET, 0).takeIf { it > 0 },
                nextHint = preferences.getString(KEY_NEXT_HINT, "").orEmpty(),
                hasNextTarget = preferences.getBoolean(KEY_HAS_NEXT, false),
                startedAfterConfirmedSet = preferences.getBoolean(KEY_CONFIRMED, false),
                targetRecordDate = preferences.getString(KEY_TARGET_DATE, "").orEmpty(),
                targetEntryId = preferences.getLong(KEY_TARGET_ENTRY, 0L),
                targetSetId = preferences.getLong(KEY_TARGET_SET, 0L)
            )
            val editor = preferences.edit().putBoolean(deliveredKey, true)
            if (!warning) editor.putBoolean(KEY_FINISHED, true)
            if (!editor.commit()) return@synchronized null
            state
        } ?: return

        val notifier = RestTimerNotifier(appContext)
        if (warning) {
            if (!notifier.showWarning(snapshot)) RestTimerSoundVibration(appContext).playWarningAlert()
        } else {
            cancelScheduled()
            notifier.cancelRunning()
            if (!notifier.showFinished(snapshot)) RestTimerSoundVibration(appContext).playFinishAlert()
        }
    }

    /** Reinstates only still-relevant alarms after reboot, update, or a permission grant. */
    fun restorePending() {
        val endAt = preferences.getLong(KEY_END_AT, 0L)
        val runId = preferences.getLong(KEY_RUN_ID, 0L)
        if (runId <= 0L || endAt <= 0L || preferences.getBoolean(KEY_FINISHED, false)) return
        val remaining = endAt - System.currentTimeMillis()
        if (remaining <= 0L) {
            // Never wake the user with an old reminder when a device restarts.
            preferences.edit().putBoolean(KEY_FINISHED, true).putBoolean(KEY_FINISH_DELIVERED, true).commit()
            cancelScheduled()
            return
        }
        schedule(
            RestTimerState(
                runId = runId,
                isRunning = true,
                totalSeconds = preferences.getInt(KEY_TOTAL_SECONDS, 0),
                endAtEpochMillis = endAt
            )
        )
    }

    companion object {
        const val ACTION_WARNING = "com.whatyougottado.rest.WARNING"
        const val ACTION_FINISH = "com.whatyougottado.rest.FINISH"
        const val EXTRA_RUN_ID = "run_id"
        const val KEY_RUN_ID = "rest_run_id"
        private const val PREFERENCES_NAME = "rest_timer"
        private const val KEY_END_AT = "rest_end_at"
        private const val KEY_TOTAL_SECONDS = "rest_total_seconds"
        private const val KEY_FINISHED = "rest_finished"
        private const val KEY_WARNING_DELIVERED = "rest_warning_delivered"
        private const val KEY_FINISH_DELIVERED = "rest_finish_delivered"
        private const val KEY_EXERCISE_KEY = "rest_exercise_stable_key"
        private const val KEY_STORED_NAME = "rest_stored_exercise_name"
        private const val KEY_LEGACY_NAME = "rest_exercise_name"
        private const val KEY_NEXT_SET = "rest_next_set_number"
        private const val KEY_NEXT_HINT = "rest_next"
        private const val KEY_HAS_NEXT = "rest_has_next_target"
        private const val KEY_CONFIRMED = "rest_started_after_confirmed_set"
        private const val KEY_TARGET_DATE = "rest_target_date"
        private const val KEY_TARGET_ENTRY = "rest_target_entry_id"
        private const val KEY_TARGET_SET = "rest_target_set_id"
        private const val REQUEST_WARNING = 2602
        private const val REQUEST_FINISH = 2603
        private val DELIVERY_LOCK = Any()
    }
}

class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bridge = RestTimerAlarmBridge(context)
        when (intent.action) {
            RestTimerAlarmBridge.ACTION_WARNING ->
                bridge.deliverWarning(intent.getLongExtra(RestTimerAlarmBridge.EXTRA_RUN_ID, 0L))
            RestTimerAlarmBridge.ACTION_FINISH ->
                bridge.deliverFinish(intent.getLongExtra(RestTimerAlarmBridge.EXTRA_RUN_ID, 0L))
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                bridge.restorePending()
        }
    }
}
