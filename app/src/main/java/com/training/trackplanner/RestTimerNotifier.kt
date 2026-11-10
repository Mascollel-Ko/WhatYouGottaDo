package com.training.trackplanner

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings

class RestTimerNotifier(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannels()
    }

    fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun canPost(channelId: String): Boolean {
        if (needsNotificationPermission() || !notificationManager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return false
        return true
    }

    fun showRunning(state: RestTimerState) {
        if (!canPost(CHANNEL_RUNNING)) return
        notificationManager.notify(
            NOTIFICATION_RUNNING,
            baseBuilder(state, CHANNEL_RUNNING)
                .setContentTitle(restTimerNotificationTitle(context, state))
                .setContentText(restTimerNotificationText(context, state))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    /** Separate, non-ongoing alerts can be bridged to Wear OS by the phone. */
    fun showWarning(state: RestTimerState): Boolean =
        postEvent(
            NOTIFICATION_WARNING,
            CHANNEL_WARNING,
            state,
            context.getString(R.string.rest_timer_warning_title),
            RestTimerPresentation.nextSetHint(context, state)
        )

    fun showFinished(state: RestTimerState): Boolean =
        postEvent(
            NOTIFICATION_FINISHED,
            CHANNEL_FINISHED,
            state,
            restTimerNotificationTitle(context, state),
            restTimerNotificationText(context, state)
        )

    private fun postEvent(
        id: Int,
        channel: String,
        state: RestTimerState,
        title: String,
        body: String
    ): Boolean {
        if (!canPost(channel)) return false
        return runCatching {
            notificationManager.notify(
                id,
                baseBuilder(state, channel)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setOngoing(false)
                    .setLocalOnly(false)
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .build()
            )
        }.isSuccess
    }

    fun cancelRunning() {
        notificationManager.cancel(NOTIFICATION_RUNNING)
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_RUNNING)
        notificationManager.cancel(NOTIFICATION_WARNING)
        notificationManager.cancel(NOTIFICATION_FINISHED)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RUNNING,
                context.getString(R.string.rest_timer_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.rest_timer_channel_description)
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WARNING,
                context.getString(R.string.rest_timer_warning_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 90, 70, 90)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
                )
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FINISHED,
                context.getString(R.string.rest_timer_finished_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 280, 130, 280, 130, 280)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
                )
            }
        )
    }

    private fun baseBuilder(state: RestTimerState, channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channel)
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
        // Keep the previous channel ID to respect user-customized running settings.
        const val CHANNEL_RUNNING = "rest_timer"
        const val CHANNEL_WARNING = "rest_timer_warning_v1"
        const val CHANNEL_FINISHED = "rest_timer_finished_v1"
        const val NOTIFICATION_RUNNING = 2601
        const val NOTIFICATION_WARNING = 2602
        const val NOTIFICATION_FINISHED = 2603
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
