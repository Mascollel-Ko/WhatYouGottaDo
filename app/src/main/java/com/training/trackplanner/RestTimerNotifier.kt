package com.training.trackplanner

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class RestTimerNotifier(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannel()
    }

    fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    fun showRunning(state: RestTimerState) {
        if (needsNotificationPermission()) return
        notificationManager.notify(
            NOTIFICATION_ID,
            baseBuilder(state)
                .setContentTitle(restTimerNotificationTitle(context, state))
                .setContentText(restTimerNotificationText(context, state))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    fun showFinished(state: RestTimerState) {
        if (needsNotificationPermission()) return
        notificationManager.notify(
            NOTIFICATION_ID,
            baseBuilder(state)
                .setContentTitle(restTimerNotificationTitle(context, state))
                .setContentText(restTimerNotificationText(context, state))
                .setOngoing(false)
                .setDefaults(Notification.DEFAULT_SOUND)
                .build()
        )
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.rest_timer_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.rest_timer_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun baseBuilder(state: RestTimerState): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
            .setSmallIcon(R.drawable.ic_launcher)
            .setCategory(Notification.CATEGORY_STATUS)
            .setShowWhen(false)
            .setContentIntent(contentIntent(state))

    private fun contentIntent(state: RestTimerState): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            RestTimerNavigation.targetIntent(context, state),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        const val CHANNEL_ID = "rest_timer"
        const val NOTIFICATION_ID = 2601
    }
}

internal fun restTimerNotificationTitle(context: Context, state: RestTimerState): String =
    context.getString(if (state.isFinished) R.string.rest_timer_finished else R.string.rest_timer_running)

internal fun restTimerNotificationText(context: Context, state: RestTimerState): String =
    if (state.isFinished) {
        state.takeIf { it.hasNextTarget }?.let { RestTimerPresentation.nextSetHint(context, it) }
            ?: context.getString(
                R.string.rest_timer_finished_notification,
                RestTimerPresentation.exerciseName(context, state)
            )
    } else {
        context.getString(
            R.string.rest_timer_running_notification,
            formatRestTimerClock(state.remainingSeconds),
            RestTimerPresentation.nextSetHint(context, state)
        )
    }
