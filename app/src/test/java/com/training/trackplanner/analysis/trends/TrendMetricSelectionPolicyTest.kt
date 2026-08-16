package com.training.trackplanner.analysis.trends

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendMetricSelectionPolicyTest {
    private val available = listOf(
        TrendMetricId.BADMINTON_PRACTICE_LOAD,
        TrendMetricId.FATIGUE_COMPOSITE
    )

    @Test
    fun retiredBadmintonSelectionsResetWithoutSemanticRemapping() {
        listOf(
            "BADMINTON_TRAINING",
            "COURT_VOLUME",
            "FOOTWORK_REACTIVE",
            "BADMINTON_SUPPORT"
        ).forEach { saved ->
            assertEquals(
                TrendMetricId.BADMINTON_PRACTICE_LOAD,
                TrendMetricSelectionPolicy.restore(
                    savedName = saved,
                    available = available,
                    preferred = TrendMetricId.BADMINTON_PRACTICE_LOAD
                )
            )
        }
    }

    @Test
    fun supportedSelectionRestoresAndUnknownSelectionFallsBack() {
        assertEquals(
            TrendMetricId.FATIGUE_COMPOSITE,
            TrendMetricSelectionPolicy.restore(
                savedName = "FATIGUE_COMPOSITE",
                available = available,
                preferred = TrendMetricId.BADMINTON_PRACTICE_LOAD
            )
        )
        assertEquals(
            TrendMetricId.BADMINTON_PRACTICE_LOAD,
            TrendMetricSelectionPolicy.restore(
                savedName = "REMOVED_OR_UNKNOWN",
                available = available,
                preferred = TrendMetricId.BADMINTON_PRACTICE_LOAD
            )
        )
    }
}
