package com.training.trackplanner.data

import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatusBuilder
import com.training.trackplanner.analysis.readiness.TodayReadinessEngine
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TodayStatusSummaryService(
    private val dailyReadinessInputService: DailyReadinessInputService
) {
    suspend fun todayReadinessSummary(): TodayReadinessSummary = withContext(Dispatchers.IO) {
        TodayReadinessEngine().analyze(dailyReadinessInputService.build())
    }

    suspend fun phaseAwareTodayStatus(): PhaseAwareTodayStatus = withContext(Dispatchers.IO) {
        PhaseAwareTodayStatusBuilder().build(dailyReadinessInputService.build())
    }
}

