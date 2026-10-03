package com.training.trackplanner.data

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class StrengthPersonalCurveThetaAsOfTest {
    @Test
    fun posteriorUpdatedAfterPlanningCutoffIsNotVisibleToCapacityProposal() {
        val cutoff = LocalDate.of(2026, 9, 20)
        val zone = ZoneOffset.UTC
        val sameDay = cutoff.atTime(23, 59).toInstant(zone).toEpochMilli()
        val afterCutoff = cutoff.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val theta = strengthPersonalCurveThetaAsOf(
            revisionKey = "rev-1",
            cutoff = cutoff,
            zoneId = zone,
            records = listOf(
                StrengthPersonalCurveThetaRecord("rev-1|exercise:bench", sameDay, 0.025),
                StrengthPersonalCurveThetaRecord("rev-1|exercise:future", afterCutoff, 0.20),
                StrengthPersonalCurveThetaRecord("rev-2|exercise:other", sameDay, 0.10),
                StrengthPersonalCurveThetaRecord("rev-1|exercise:invalid", sameDay, Double.NaN)
            )
        )

        assertEquals(mapOf("exercise:bench" to 0.025), theta)
    }
}
