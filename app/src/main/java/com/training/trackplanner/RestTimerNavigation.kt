package com.training.trackplanner

import android.content.Context
import android.content.Intent

data class RestTimerTarget(
    val recordDate: String,
    val entryId: Long
)

object RestTimerNavigation {
    const val ACTION_OPEN_RECORD_TARGET = "com.whatyougottatrain.action.OPEN_RECORD_TARGET"
    const val EXTRA_RECORD_DATE = "rest_timer_record_date"
    const val EXTRA_ENTRY_ID = "rest_timer_entry_id"

    fun targetIntent(context: Context, state: RestTimerState): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_RECORD_TARGET
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RECORD_DATE, state.targetRecordDate)
            putExtra(EXTRA_ENTRY_ID, state.targetEntryId)
        }

    fun targetFromIntent(intent: Intent?): RestTimerTarget? {
        if (intent?.action != ACTION_OPEN_RECORD_TARGET) return null
        val date = intent.getStringExtra(EXTRA_RECORD_DATE).orEmpty()
        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, 0L)
        if (date.isBlank()) return null
        return RestTimerTarget(date, entryId)
    }
}
