package com.training.trackplanner.analysis.coach

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachingSignalsBuilderTest {
    @Test
    fun builderReturnsSleepSignalWhenOtherInputsAreMissing() {
        val today = LocalDate.of(2026, 6, 23)
        val summary = CoachingSignalsBuilder().build(
            today = today,
            dailyMetrics = listOf(DailyMetric(today.toString(), sleepHours = 4.5)),
            checkIns = emptyList(),
            entriesWithSets = emptyList(),
            exercises = emptyList(),
            runtimeMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
            history = emptyList()
        )

        assertEquals(CoachingSignalSeverity.CAUTION, summary.sleep.severity)
        assertEquals(null, summary.rpe)
        assertEquals(null, summary.jointTendon)
        assertEquals(null, summary.courtRecovery)
    }
}
