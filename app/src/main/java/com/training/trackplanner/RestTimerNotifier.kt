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
                .setContentTitle("휴식 중")
                .setContentText("${formatSeconds(state.remainingSeconds)} 남음 · ${state.nextHint}")
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
                .setContentTitle("휴식 종료")
                .setContentText(state.nextHint.ifBlank { "${state.exerciseName} 다음 세트를 준비하세요." })
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
            "휴게 타이머",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "세트 사이 휴식 종료를 알려줍니다."
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
