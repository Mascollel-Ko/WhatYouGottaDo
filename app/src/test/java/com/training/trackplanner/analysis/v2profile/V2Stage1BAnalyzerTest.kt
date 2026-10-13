package com.training.trackplanner.analysis.v2profile

import com.training.trackplanner.data.ProgramLoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class V2Stage1BAnalyzerTest {
    private val asOf = LocalDate.parse("2026-10-12")

    @Test
    fun `RPE adjusted external load score retains its raw set provenance`() {
        val result = analyze(
            listOf(
                set(
                    key = "press",
                    name = "Press",
                    date = "2026-10-12",
                    entryId = 101,
                    session = "s1",
                    weight = 60.0,
                    reps = 8,
                    rpe = 7.5
                )
            )
        ).stage1B!!.exercises.single()

        val score = result.performance.latestScoredSet!!
        assertEquals(81.0, score.loadIndex, 0.0)
        assertEquals(2.5, score.rirAssumed, 0.0)
        assertEquals(7.5, score.rpe!!, 0.0)
        assertEquals(101L, score.entryId)
        assertEquals(7.5, result.performance.latestRawSet!!.recordedRpe!!, 0.0)
        assertEquals("EXTERNAL_LOAD_INDEX_NOT_ACTUAL_1RM", result.performance.metric)
    }

    @Test
    fun `missing and invalid RPE score without RIR and emit evidence warnings`() {
        val result = analyze(
            listOf(
                set("press", "Press", "2026-10-12", 1, "s1", 50.0, 10, null),
                set("press", "Press", "2026-10-12", 2, "s1", 52.0, 12, 11.0, setIndex = 2)
            )
        ).stage1B!!.exercises.single()

        val session = result.sessionEvidence.single()
        assertEquals(2, session.scoredSetCount)
        assertTrue(result.warnings.contains("RPE_MISSING_OR_OUT_OF_RANGE_NO_RIR_ADJUSTMENT"))
        assertTrue(result.warnings.contains("HIGH_REP_LOAD_PROXY_UNCERTAIN"))
        val invalidRpe = session.sets.first { it.recordedRpe == 11.0 }
        assertEquals(11.0, invalidRpe.recordedRpe!!, 0.0)
        assertNull(invalidRpe.normalizedRpe)
        assertEquals(0.0, invalidRpe.rirAssumed!!, 0.0)
        assertEquals(72.8, invalidRpe.loadIndex!!, 0.0)
    }

    @Test
    fun `multiple same date sessions reduce to one trend representative`() {
        val rows = listOf(
            set("squat", "Squat", "2026-09-01", 1, "a", 60.0, 10, 10.0),
            set("squat", "Squat", "2026-09-01", 2, "z", 90.0, 10, 10.0),
            set("squat", "Squat", "2026-09-10", 3, "a", 90.0, 10, 10.0),
            set("squat", "Squat", "2026-09-10", 4, "z", 100.0, 10, 10.0)
        )

        val result = analyze(rows).stage1B!!.exercises.single()
        assertEquals(4, result.sessionCount56d)
        assertEquals(2, result.trainingDays56d)
        assertEquals(2, result.dayEvidence.size)
        assertEquals(listOf("z", "z"), result.dayEvidence.map { it.representativeSessionKey })
        assertEquals(listOf(LocalDate.parse("2026-09-01")), result.trend.baselineDates)
        assertEquals(listOf(LocalDate.parse("2026-09-10")), result.trend.recentDates)
        assertEquals(V2Stage1BTrendDirection.IMPROVING, result.trend.direction)
    }

    @Test
    fun `one session and unscorable movements remain in result without trend`() {
        val profile = analyze(
            listOf(
                set("pull_up", "풀업", "2026-10-10", 1, "only", 0.0, 8, 8.0, loadState = ProgramLoadState.REAL_ZERO_LOAD.name),
                set("dumbbell_curl", "덤벨 컬", "2026-10-10", 2, "only", 12.0, 8, 7.0),
                set("weighted_pull_up", "중량 풀업", "2026-10-10", 3, "only", 10.0, 7, 7.5)
            )
        )
        val stage1B = profile.stage1B!!
        assertEquals(3, stage1B.exercises.size)
        assertEquals(0, stage1B.summary.rosterRestrictedByStage1B)

        val pullUp = stage1B.exercises.single { it.stableKey == "pull_up" }
        assertNull(pullUp.performance.latestScoredSet)
        assertEquals(V2Stage1BTrendDirection.NO_TREND_EVIDENCE, pullUp.trend.direction)
        assertTrue(pullUp.warnings.contains("BODYWEIGHT_NOT_INCLUDED_IN_LOAD_INDEX"))
        assertTrue(pullUp.warnings.contains("NO_EXPLICIT_POSITIVE_LOAD_FOR_PROXY"))
        assertEquals(V2Stage1BRosterEligibility.NOT_RESTRICTED_BY_ANALYSIS_CONFIDENCE, pullUp.rosterEligibilityFromThisStage)

        val dumbbell = stage1B.exercises.single { it.stableKey == "dumbbell_curl" }
        assertTrue(dumbbell.warnings.contains("DUMBBELL_UNIT_CONVENTION_UNVERIFIED"))
        assertEquals(V2Stage1BTrendConfidence.INSUFFICIENT, dumbbell.trend.confidence)
        assertEquals(1, dumbbell.sessionEvidence.single().completedSetCount)

        val weightedPullUp = stage1B.exercises.single { it.stableKey == "weighted_pull_up" }
        assertTrue(weightedPullUp.warnings.contains("BODYWEIGHT_NOT_INCLUDED_IN_LOAD_INDEX"))
        assertNull(weightedPullUp.trend.changePercent)
    }

    @Test
    fun `tied session and day representatives preserve Python first item behavior`() {
        val result = analyze(
            listOf(
                set("press", "Press", "2026-10-01", 1, "session-a", 60.0, 8, 8.0),
                set("press", "Press", "2026-10-01", 2, "session-z", 60.0, 8, 8.0),
                set("press", "Press", "2026-10-12", 3, "session-a", 60.0, 8, 8.0),
                set("press", "Press", "2026-10-12", 4, "session-z", 60.0, 8, 8.0)
            )
        ).stage1B!!.exercises.single()

        assertEquals("session-a", result.dayEvidence.first().representativeSessionKey)
        assertEquals("session-z", result.performance.latestScoredSet!!.sessionKey)
        assertEquals(V2Stage1BTrendDirection.STABLE, result.trend.direction)
        assertEquals(0.0, result.trend.changePercent!!, 0.0)
    }

    @Test
    fun `median midpoint rounding matches Python binary floating point round`() {
        val result = analyze(
            listOf(
                set("press", "Press", "2026-08-20", 1, "s1", 52.0, 10, 6.5), // 75.40
                set("press", "Press", "2026-08-25", 2, "s2", 56.0, 6, 6.0), // 74.67
                set("press", "Press", "2026-09-20", 3, "s3", 60.0, 8, 7.5),
                set("press", "Press", "2026-10-01", 4, "s4", 60.0, 8, 7.5)
            )
        ).stage1B!!.exercises.single()

        assertEquals(75.03, result.trend.baselineIndex!!, 0.0)
    }

    @Test
    fun `weighted pull-up retains external load caveat`() {
        val result = analyze(
            listOf(set("weighted_pull_up", "중량 풀업", "2026-10-12", 1, "s1", 10.0, 7, 7.5))
        ).stage1B!!.exercises.single()

        assertEquals(13.17, result.performance.latestScoredSet!!.loadIndex, 0.0)
        assertTrue(result.warnings.contains("BODYWEIGHT_NOT_INCLUDED_IN_LOAD_INDEX"))
    }

    @Test
    fun `repetitions above proxy range keep raw exercise but do not score`() {
        val profile = analyze(
            listOf(set("band", "Band", "2026-10-12", 1, "s1", 15.0, 16, 8.0))
        )
        val result = profile.stage1B!!.exercises.single()
        assertEquals(1, profile.exercises.size)
        assertEquals("REPETITIONS_OUTSIDE_1_TO_15", result.sessionEvidence.single().sets.single().scoreExclusionReason)
        assertNull(result.performance.latestScoredSet)
    }

    @Test
    fun `profile diagnostic includes V2-1B evidence for every observed exercise`() {
        val profile = analyze(
            listOf(
                set("press", "Press", "2026-10-12", 1, "s1", 60.0, 8, 7.5),
                set("pull_up", "Pull-up", "2026-10-12", 2, "s2", 0.0, 8, null, loadState = ProgramLoadState.REAL_ZERO_LOAD.name)
            )
        )
        val report = V2Stage1ProfileDiagnosticReport.format(profile)
        assertTrue(report.contains("V2-1B 수행 지표 및 추세"))
        assertTrue(report.contains("EXTERNAL_LOAD_INDEX_NOT_ACTUAL_1RM") || report.contains("실제 1RM 아님"))
        assertTrue(report.contains("Press [press]"))
        assertTrue(report.contains("Pull-up [pull_up]"))
        assertFalse(report.contains("후보 제외"))
    }

    private fun analyze(rows: List<V2Stage1SetObservation>) =
        V2Stage1TrainingProfileAnalyzer.analyze(rows, asOf = asOf)

    private fun set(
        key: String,
        name: String,
        date: String,
        entryId: Long,
        session: String,
        weight: Double,
        reps: Int,
        rpe: Double?,
        setIndex: Int = 1,
        loadState: String = ProgramLoadState.EXPLICIT_LOAD.name
    ) = V2Stage1SetObservation(
        entryId = entryId,
        date = date,
        sessionStableKey = session,
        backupSourceId = null,
        exerciseStableKey = key,
        exerciseName = name,
        category = "근력운동",
        setIndex = setIndex,
        confirmed = true,
        analysisEligibleCompletedSet = true,
        loadState = loadState,
        weightKg = weight,
        repetitions = reps,
        rpe = rpe
    )
}
