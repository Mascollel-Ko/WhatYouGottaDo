package com.training.trackplanner.analysis.v2profile

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class V2Stage1TrainingProfileAnalyzerTest {
    private val asOf = LocalDate.parse("2026-10-12")

    @Test
    fun `confirmed sets use inclusive calendar windows and report exclusions`() {
        val input = listOf(
            observation("main", "2026-10-12", entryId = 1, session = "same", setIndex = 1),
            observation("main", "2026-10-12", entryId = 1, session = "same", setIndex = 2),
            observation("other", "2026-10-12", entryId = 2, session = "same", category = "기타"),
            observation("main", "2026-10-06", entryId = 3), // inclusive 7-day boundary
            observation("main", "2026-10-05", entryId = 4), // outside 7 days
            observation("main", "2026-09-15", entryId = 5), // inclusive 28-day boundary
            observation("main", "2026-09-14", entryId = 6), // outside 28 days
            observation("main", "2026-08-18", entryId = 7), // inclusive 56-day boundary
            observation("main", "2026-08-17", entryId = 8), // outside 56 days
            observation("main", "2026-10-12", entryId = 9, confirmed = false, eligible = false),
            observation("main", "2026-10-12", entryId = 10, eligible = false),
            observation("main", "2026-10-13", entryId = 11),
            observation("main", "not-a-date", entryId = 12)
        )

        val profile = V2Stage1TrainingProfileAnalyzer.analyze(input, asOf = asOf)

        assertEquals(9, profile.completedSetCount)
        assertEquals(4, profile.windows.single { it.days == 7 }.completedSetCount)
        assertEquals(3, profile.windows.single { it.days == 7 }.resistanceSetCount)
        assertEquals(6, profile.windows.single { it.days == 28 }.completedSetCount)
        assertEquals(8, profile.windows.single { it.days == 56 }.completedSetCount)
        assertEquals(7, profile.windows.single { it.days == 56 }.resistanceSetCount)
        assertEquals(6, profile.windows.single { it.days == 56 }.activeDays)
        assertEquals(6, profile.windows.single { it.days == 56 }.resistanceDays)
        assertEquals(6, profile.windows.single { it.days == 56 }.sessionCount)
        assertEquals(6, profile.windows.single { it.days == 56 }.dailyEvidence.size)
        assertEquals(6, profile.windows.single { it.days == 56 }.sessionEvidence.size)
        assertEquals(1, profile.exclusions.unconfirmedSets)
        assertEquals(1, profile.exclusions.loadCalibrationPendingSets)
        assertEquals(1, profile.exclusions.futureCompletedSets)
        assertEquals(1, profile.exclusions.invalidDateCompletedSets)
        assertEquals(1, profile.exclusions.olderThan56DaysCompletedSets)
        assertEquals(V2Stage1Interpretation.DESCRIPTIVE_ONLY, profile.interpretation)
    }

    @Test
    fun `attached JSON aggregate summary remains a regression reference`() {
        // These aggregate-only values are from v2_stage1_profile_2026-10-12.json.
        // The personal CSV and full per-exercise JSON are intentionally not copied into the repo.
        val reference = listOf(
            ReferenceWindow(days = 7, all = 51, resistance = 37, other = 14, activeDays = 5, resistanceDays = 4, sessions = 5, exerciseCount = 8),
            ReferenceWindow(days = 28, all = 152, resistance = 103, other = 49, activeDays = 18, resistanceDays = 12, sessions = 18, exerciseCount = 11),
            ReferenceWindow(days = 56, all = 332, resistance = 199, other = 133, activeDays = 35, resistanceDays = 23, sessions = 35, exerciseCount = 21)
        )
        val input = referenceFixture()
        val profile = V2Stage1TrainingProfileAnalyzer.analyze(input, asOf = asOf)

        assertEquals(1017, profile.completedSetCount)
        assertEquals(685, profile.exclusions.olderThan56DaysCompletedSets)
        reference.forEach { expected ->
            val actual = profile.windows.single { it.days == expected.days }
            assertEquals(expected.all, actual.completedSetCount)
            assertEquals(expected.resistance, actual.resistanceSetCount)
            assertEquals(expected.other, actual.otherSetCount)
            assertEquals(expected.activeDays, actual.activeDays)
            assertEquals(expected.resistanceDays, actual.resistanceDays)
            assertEquals(expected.sessions, actual.sessionCount)
            assertEquals(expected.exerciseCount, actual.resistanceExerciseCount)
            assertEquals(expected.resistance, actual.resistanceExerciseFrequencies.sumOf { it.completedSetCount })
        }
        assertEquals(37.0, profile.windows.single { it.days == 7 }.resistanceWeeklySetEquivalent, 0.0)
        assertEquals(25.75, profile.windows.single { it.days == 28 }.resistanceWeeklySetEquivalent, 0.0)
        assertEquals(24.88, profile.windows.single { it.days == 56 }.resistanceWeeklySetEquivalent, 0.0)
    }

    @Test
    fun `window frequency ties retain first observed order while exercise summaries sort by name`() {
        val rows = listOf(
            observation("z-exercise", "2026-10-12", entryId = 10, setIndex = 1, name = "Zulu"),
            observation("z-exercise", "2026-10-12", entryId = 10, setIndex = 2, name = "Zulu"),
            observation("a-exercise", "2026-10-12", entryId = 11, setIndex = 1, name = "Alpha"),
            observation("a-exercise", "2026-10-12", entryId = 11, setIndex = 2, name = "Alpha")
        )

        val profile = V2Stage1TrainingProfileAnalyzer.analyze(rows, asOf = asOf)
        val sevenDay = profile.windows.single { it.days == 7 }

        assertEquals(listOf("z-exercise", "a-exercise"), sevenDay.resistanceExerciseFrequencies.map { it.stableKey })
        assertEquals(listOf("a-exercise", "z-exercise"), profile.exercises.map { it.stableKey })
    }

    @Test
    fun `exercise load repetitions and RPE retain direct evidence and unknowns`() {
        val rows = mutableListOf<V2Stage1SetObservation>()
        val dates = listOf("2026-08-21", "2026-08-28", "2026-09-05", "2026-09-12", "2026-09-15", "2026-09-18", "2026-09-23", "2026-09-30", "2026-10-06")
        val countsByDate = listOf(3, 3, 3, 3, 4, 3, 4, 3, 3)
        val baselineRpes = buildList {
            repeat(26) { add(7.24) }
            add(7.25)
            add(8.0)
            add(8.5)
        }
        var rowNumber = 0
        dates.forEachIndexed { dateIndex, date ->
            repeat(countsByDate[dateIndex]) { withinDate ->
                val isPeakEvidence = date == "2026-09-23" && withinDate == 0
                val isLatestEvidence = date == "2026-10-06" && withinDate == 2
                rows += observation(
                    key = "fixture_exercise_1",
                    date = date,
                    entryId = 100L + rowNumber,
                    session = "session-$dateIndex",
                    setIndex = withinDate + 1,
                    weight = if (isPeakEvidence || isLatestEvidence) 60.0 else 55.0,
                    reps = if (isPeakEvidence || isLatestEvidence) 8 else 7,
                    rpe = baselineRpes[rowNumber],
                    name = "Fixture exercise"
                )
                rowNumber++
            }
        }

        val summary = V2Stage1TrainingProfileAnalyzer.analyze(rows, asOf = asOf).exercises.single()
        val latest = summary.performance.latestExplicitLoad!!
        val peak = summary.performance.highestLoggedLoad!!

        assertEquals(29, summary.exposureByWindow.getValue(56).completedSetCount)
        assertEquals(17, summary.exposureByWindow.getValue(28).completedSetCount)
        assertEquals(3, summary.exposureByWindow.getValue(7).completedSetCount)
        assertEquals(9, summary.exposureByWindow.getValue(56).sessionCount)
        assertEquals(LocalDate.parse("2026-10-06"), summary.exposureByWindow.getValue(56).lastCompletedDate)
        assertEquals(60.0, latest.explicitPositiveWeightKg!!, 0.0)
        assertEquals(8, latest.repetitions)
        assertEquals(8.5, latest.rpe!!, 0.0)
        assertEquals(LocalDate.parse("2026-10-06"), latest.date)
        assertEquals(LocalDate.parse("2026-09-23"), peak.date) // stable first-peak tie handling
        assertEquals(29, summary.performance.recordedRpeSetCount)
        assertEquals(7.31, summary.performance.meanRecordedRpe!!, 0.0)
        assertEquals(29, summary.performance.evidence.size)
    }

    @Test
    fun `load state and missing RPE do not become measured load or zero RPE`() {
        val rows = listOf(
            observation("key", "2026-10-12", entryId = 1, weight = 45.0, rpe = 8.0),
            observation("key", "2026-10-11", entryId = 2, loadState = ProgramLoadState.REAL_ZERO_LOAD.name, weight = 0.0, rpe = 6.0),
            observation("key", "2026-10-10", entryId = 3, loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED.name, eligible = false, rpe = 9.0),
            observation("key", "2026-10-09", entryId = 4, weight = 30.0, rpe = null)
        )
        val summary = V2Stage1TrainingProfileAnalyzer.analyze(rows, asOf = asOf).exercises.single()

        assertEquals(3, summary.performance.evidence.size)
        assertEquals(45.0, summary.performance.latestExplicitLoad!!.explicitPositiveWeightKg!!, 0.0)
        assertEquals(45.0, summary.performance.highestLoggedLoad!!.explicitPositiveWeightKg!!, 0.0)
        assertEquals(2, summary.performance.recordedRpeSetCount)
        assertEquals(7.0, summary.performance.meanRecordedRpe!!, 0.0)
        assertEquals(8, summary.maintenancePriorityEvidence.unevaluatedFactors.size)
        assertNull(summary.maintenancePriorityEvidence.priorityScore)
        assertEquals(V2Stage1MaintenanceDecision.NOT_EVALUATED, summary.maintenancePriorityEvidence.decision)
        assertFalse(summary.maintenancePriorityEvidence.unevaluatedFactors.isEmpty())
        assertTrue(summary.performance.evidence.any { it.loadState == ProgramLoadState.REAL_ZERO_LOAD.name && it.recordedWeightKg == 0.0 })
    }

    @Test
    fun `database adapter uses existing completed set rule and preserves metadata`() {
        val exercise = Exercise(
            stableKey = "press",
            name = "Press",
            category = "근력운동",
            movementPattern = "HORIZONTAL_PUSH",
            primaryMuscles = "CHEST",
            metadataConfidence = "REVIEWED"
        )
        val entry = WorkoutEntry(
            id = 1,
            date = "2026-10-12",
            exerciseStableKey = "press",
            exerciseName = "Press",
            category = "",
            rpe = 8.0,
            sessionStableKey = "shared-session"
        )
        val record = WorkoutEntryWithSets(
            entry = entry,
            sets = listOf(
                WorkoutSet(id = 1, entryId = 1, setIndex = 1, reps = 8, weightKg = 40.0, confirmed = true, rpe = 8.5),
                WorkoutSet(id = 2, entryId = 1, setIndex = 2, reps = 8, weightKg = 40.0, confirmed = true, rpe = 9.0, loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED),
                WorkoutSet(id = 3, entryId = 1, setIndex = 3, reps = 10, weightKg = 35.0, confirmed = true)
            )
        )

        val profile = V2Stage1TrainingProfileAnalyzer.analyzeRecords(listOf(record), mapOf("press" to exercise), asOf)

        assertEquals(2, profile.completedSetCount)
        assertEquals(1, profile.exclusions.loadCalibrationPendingSets)
        assertEquals("CHEST", profile.exercises.single().metadata?.primaryMuscles)
        assertEquals("HORIZONTAL_PUSH", profile.exercises.single().metadata?.movementPattern)
        assertEquals(35.0, profile.exercises.single().performance.latestExplicitLoad!!.explicitPositiveWeightKg!!, 0.0)
        assertEquals(8.0, profile.exercises.single().performance.latestExplicitLoad!!.rpe!!, 0.0)
        assertEquals(8.25, profile.exercises.single().performance.meanRecordedRpe!!, 0.0)
    }

    @Test
    fun `diagnostic report states calculation basis and limitations`() {
        val profile = V2Stage1TrainingProfileAnalyzer.analyze(
            listOf(observation("key", "2026-10-12", entryId = 1, name = "Fixture exercise")),
            asOf = asOf
        )

        val report = V2Stage1ProfileDiagnosticReport.format(profile)

        assertTrue(report.contains("56일 (2026-08-18~2026-10-12)"))
        assertTrue(report.contains("유지 우선순위: 미평가"))
        assertTrue(report.contains("자극 및 회복 부담: 계산하지 않음"))
        assertTrue(report.contains("날짜별 근거 (완료/근력)"))
        assertTrue(report.contains("미확인 0세트"))
    }

    private fun observation(
        key: String,
        date: String,
        entryId: Long,
        session: String = "session-$entryId",
        setIndex: Int = 1,
        category: String = "근력운동",
        confirmed: Boolean = true,
        eligible: Boolean = confirmed,
        loadState: String = ProgramLoadState.EXPLICIT_LOAD.name,
        weight: Double = 40.0,
        reps: Int = 8,
        rpe: Double? = 7.0,
        name: String = key
    ) = V2Stage1SetObservation(
        entryId = entryId,
        date = date,
        sessionStableKey = session,
        backupSourceId = null,
        exerciseStableKey = key,
        exerciseName = name,
        category = category,
        setIndex = setIndex,
        confirmed = confirmed,
        analysisEligibleCompletedSet = eligible,
        loadState = loadState,
        weightKg = weight,
        repetitions = reps,
        rpe = rpe
    )

    private fun referenceFixture(): List<V2Stage1SetObservation> {
        val rows = mutableListOf<V2Stage1SetObservation>()
        var id = 1L
        fun addSegment(
            dayIndexes: List<Int>,
            resistanceDayIndexes: List<Int>,
            resistanceSetCount: Int,
            otherSetCount: Int,
            exerciseKeys: List<String>
        ) {
            val resistancePerDay = distribute(resistanceSetCount, resistanceDayIndexes.size)
            val otherPerDay = distribute(otherSetCount, dayIndexes.size)
            dayIndexes.forEachIndexed { index, dayIndex ->
                val date = asOf.minusDays((55 - dayIndex).toLong()).toString()
                val session = "reference-session-$dayIndex"
                val resistanceCount = resistanceDayIndexes.indexOf(dayIndex).takeIf { it >= 0 }
                    ?.let(resistancePerDay::get) ?: 0
                repeat(resistanceCount) { setInDay ->
                    rows += observation(
                        key = exerciseKeys[(setInDay + dayIndex) % exerciseKeys.size],
                        date = date,
                        entryId = id++,
                        session = session,
                        setIndex = setInDay + 1,
                        name = exerciseKeys[(setInDay + dayIndex) % exerciseKeys.size]
                    )
                }
                repeat(otherPerDay[index]) { setInDay ->
                    rows += observation(
                        key = "other-category",
                        date = date,
                        entryId = id++,
                        session = session,
                        category = "기타",
                        name = "Other"
                    )
                }
            }
        }

        // 7-day window: 51 total, 37 resistance, 5 active days, 4 resistance days, 8 exercises.
        addSegment(
            dayIndexes = listOf(51, 52, 53, 54, 55),
            resistanceDayIndexes = listOf(52, 53, 54, 55),
            resistanceSetCount = 37,
            otherSetCount = 14,
            exerciseKeys = (1..8).map { "reference-ex-$it" }
        )
        // Days 8-28 add 101 total / 66 resistance sets, 13 active / 8 resistance days,
        // and three exercises, matching the attached 28-day aggregate reference.
        addSegment(
            dayIndexes = (28..40).toList(),
            resistanceDayIndexes = (28..35).toList(),
            resistanceSetCount = 66,
            otherSetCount = 35,
            exerciseKeys = (9..11).map { "reference-ex-$it" }
        )
        // Days 29-56 add 180 total / 96 resistance sets, 17 active / 11 resistance days,
        // and ten exercises, matching the attached 56-day aggregate reference.
        addSegment(
            dayIndexes = (0..16).toList(),
            resistanceDayIndexes = (0..10).toList(),
            resistanceSetCount = 96,
            otherSetCount = 84,
            exerciseKeys = (12..21).map { "reference-ex-$it" }
        )
        repeat(685) {
            rows += observation(
                key = "pre-window-history",
                date = asOf.minusDays(56).toString(),
                entryId = id++,
                name = "Older history"
            )
        }
        return rows
    }

    private fun distribute(total: Int, buckets: Int): List<Int> =
        List(buckets) { index -> total / buckets + if (index < total % buckets) 1 else 0 }

    private data class ReferenceWindow(
        val days: Int,
        val all: Int,
        val resistance: Int,
        val other: Int,
        val activeDays: Int,
        val resistanceDays: Int,
        val sessions: Int,
        val exerciseCount: Int
    )
}
