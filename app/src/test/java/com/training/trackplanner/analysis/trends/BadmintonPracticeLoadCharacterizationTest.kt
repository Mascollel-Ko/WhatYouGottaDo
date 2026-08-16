package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveStimulusCalculator
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.BadmintonPracticeLoadCalculator
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation
import com.training.trackplanner.analysis.coach.CoachingSignalSeverity
import com.training.trackplanner.analysis.coach.CourtDurationRecoveryAnalyzer
import com.training.trackplanner.analysis.coach.SleepRecoverySignal
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataAssetLoader
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadmintonPracticeLoadCharacterizationTest {
    private val date = LocalDate.parse("2026-08-10")
    private val canonicalRows by lazy(::loadCanonicalRows)
    private val canonicalCatalog by lazy {
        RuntimeExerciseMetadataCatalog.of(
            metadata = canonicalRows,
            canonicalBadmintonAuthorityKeys = canonicalRows.map(RuntimeExerciseMetadata::stableKey)
        )
    }

    @Test
    fun canonicalPracticeProviderHasExactParityWithLegacyCourtRaw() {
        val badminton = canonicalExercise("ex_ae9ecdbc")
        val lesson = canonicalExercise("ex_badminton_lesson")
        val tennis = exercise(canonicalRows.single { it.stableKey == "ex_2f3b56d0" })
        val demotedCatalog = RuntimeExerciseMetadataCatalog.of(
            listOf(canonicalRows.single { it.stableKey == badminton.stableKey }.copy(activityKind = "EXERCISE"))
        )
        val confirmedSet = { seconds: Int, rpe: Double? -> set(seconds, true, rpe) }
        val unconfirmedSet = { seconds: Int -> set(seconds, false) }
        val cases = buildList {
            add(ParityCase("empty", emptyList(), emptyList(), canonicalCatalog))
            add(ParityCase("canonical badminton", listOf(record(badminton, date.toString(), listOf(confirmedSet(600, null)))), listOf(badminton), canonicalCatalog))
            add(ParityCase("canonical lesson", listOf(record(lesson, date.toString(), listOf(confirmedSet(600, null)))), listOf(lesson), canonicalCatalog))
            add(ParityCase("non badminton sport", listOf(record(tennis, date.toString(), listOf(confirmedSet(600, null)))), listOf(tennis), canonicalCatalog))
            add(ParityCase("wrong resolved kind", listOf(record(badminton, date.toString(), listOf(confirmedSet(600, null)))), listOf(badminton), demotedCatalog))
            add(ParityCase("unconfirmed", listOf(record(badminton, date.toString(), listOf(unconfirmedSet(600)))), listOf(badminton), canonicalCatalog))
            add(ParityCase("multiple confirmed", listOf(record(badminton, date.toString(), listOf(confirmedSet(120, null), confirmedSet(180, null)))), listOf(badminton), canonicalCatalog))
            add(ParityCase("set RPE precedence", listOf(record(badminton, date.toString(), listOf(confirmedSet(120, 6.0), confirmedSet(180, 10.0)), 2.0)), listOf(badminton), canonicalCatalog))
            add(ParityCase("entry RPE fallback", listOf(record(badminton, date.toString(), listOf(confirmedSet(600, null)), 9.0)), listOf(badminton), canonicalCatalog))
            add(ParityCase("null RPE", listOf(record(badminton, date.toString(), listOf(confirmedSet(600, null)))), listOf(badminton), canonicalCatalog))
            listOf(6.0, 6.5, 7.999, 8.0, 8.999, 9.0, 9.999, 10.0, 11.0).forEach { rpe ->
                add(ParityCase("RPE $rpe", listOf(record(badminton, date.toString(), listOf(confirmedSet(600, rpe)))), listOf(badminton), canonicalCatalog))
            }
            add(ParityCase("zero duration", listOf(record(badminton, date.toString(), listOf(confirmedSet(0, null)))), listOf(badminton), canonicalCatalog))
            add(
                ParityCase(
                    "same date aggregation",
                    listOf(
                        record(badminton, date.toString(), listOf(confirmedSet(300, null))),
                        record(badminton, date.toString(), listOf(confirmedSet(600, 8.0)))
                    ),
                    listOf(badminton),
                    canonicalCatalog
                )
            )
            add(
                ParityCase(
                    "multiple dates",
                    listOf(
                        record(badminton, date.toString(), listOf(confirmedSet(300, null))),
                        record(badminton, date.plusDays(2).toString(), listOf(confirmedSet(600, 8.0)))
                    ),
                    listOf(badminton),
                    canonicalCatalog
                )
            )
        }

        cases.forEach { fixture ->
            val exerciseMap = fixture.exercises.associateBy(Exercise::stableKey)
            val old = BadmintonTrainingLoadIndexCalculator(fixture.catalog).calculate(
                listOf(week(date, fixture.records)),
                exerciseMap
            ).single().courtRaw
            val new = BadmintonPracticeLoadCalculator(fixture.catalog).calculateRaw(fixture.records, exerciseMap)
            assertEquals("EXACT_PARITY ${fixture.name}", old, new, 0.0000001)
        }

        val weeks = listOf(
            week(date, listOf(record(badminton, date.toString(), listOf(set(300, true))))),
            week(date.plusWeeks(1), listOf(record(lesson, date.plusWeeks(1).toString(), listOf(set(1_200, true, 9.0)))))
        )
        val exerciseMap = listOf(badminton, lesson).associateBy(Exercise::stableKey)
        assertEquals(
            BadmintonTrainingLoadIndexCalculator(canonicalCatalog).calculate(weeks, exerciseMap).map { it.courtRaw },
            BadmintonPracticeLoadCalculator(canonicalCatalog).weeklyLoads(weeks, exerciseMap).map { it.practiceLoad }
        )
    }

    @Test
    fun currentCanonicalPracticeAndCourtExposureSetsAreEqualButOwnedByDifferentRules() {
        val exercises = canonicalRows.map(::exercise)
        val practiceKeys = exercises.filterTo(linkedSetOf()) { candidate ->
            practiceRaw(
                records = listOf(record(candidate, date.toString(), listOf(set(seconds = 60, confirmed = true)))),
                exercises = listOf(candidate),
                catalog = canonicalCatalog
            ) > 0.0
        }.mapTo(linkedSetOf(), Exercise::stableKey)
        val courtExposureKeys = exercises.filterTo(linkedSetOf()) { candidate ->
            CourtDurationRecoveryAnalyzer().analyze(
                today = date,
                entriesWithSets = listOf(
                    record(candidate, date.toString(), listOf(set(seconds = 60, confirmed = true)))
                ),
                exercises = listOf(candidate),
                runtimeMetadataCatalog = canonicalCatalog,
                checkIns = emptyList(),
                history = emptyList(),
                sleepSignal = emptySleepSignal()
            ) != null
        }.mapTo(linkedSetOf(), Exercise::stableKey)

        val expected = linkedSetOf("ex_ae9ecdbc", "ex_badminton_lesson")
        assertEquals("EXACT_PARITY practice identities", expected, practiceKeys)
        assertEquals("current canonical court-exposure identities", expected, courtExposureKeys)
        assertEquals(emptySet<String>(), practiceKeys - courtExposureKeys)
        assertEquals(emptySet<String>(), courtExposureKeys - practiceKeys)
        assertTrue(
            canonicalRows.single { it.stableKey == "ex_ae9ecdbc" }
                .sportContextTags.values.contains("BADMINTON_MATCH")
        )
    }

    @Test
    fun courtExposureRuleCanAdmitSyntheticMatchWhilePracticeAllowlistDoesNot() {
        // Current implementation detail only: this fixture is not a canonical practice identity.
        val match = Exercise(stableKey = "test_match_record", name = "Match fixture", category = "Sport")
        val matchMetadata = RuntimeExerciseMetadataDefaults.forIdentity(match.stableKey, match.name).copy(
            activityKind = "MATCH_RECORD",
            badmintonTransferLevel = "DIRECT",
            sportContextTags = MetadataTokenField.parse("BADMINTON_MATCH")
        )
        val catalog = RuntimeExerciseMetadataCatalog.of(listOf(matchMetadata))
        val matchRecord = record(match, date.toString(), listOf(set(seconds = 3_600, confirmed = true)))

        assertEquals(0.0, practiceRaw(listOf(matchRecord), listOf(match), catalog), 0.0)
        assertNotNull(
            CourtDurationRecoveryAnalyzer().analyze(
                today = date,
                entriesWithSets = listOf(matchRecord),
                exercises = listOf(match),
                runtimeMetadataCatalog = catalog,
                checkIns = emptyList(),
                history = emptyList(),
                sleepSignal = emptySleepSignal()
            )
        )
    }

    @Test
    fun currentPracticeAdmissionRequiresExactIdentityAndResolvedSportSessionKind() {
        // EXACT_PARITY for the current canonical practice admission predicate.
        val badminton = canonicalExercise("ex_ae9ecdbc")
        val demotedMetadata = canonicalRows.single { it.stableKey == badminton.stableKey }.copy(
            activityKind = "EXERCISE"
        )
        val demotedCatalog = RuntimeExerciseMetadataCatalog.of(listOf(demotedMetadata))
        val badmintonRecord = record(
            badminton,
            date.toString(),
            listOf(set(seconds = 600, confirmed = true))
        )
        assertEquals(0.0, practiceRaw(listOf(badmintonRecord), listOf(badminton), demotedCatalog), 0.0)

        val syntheticSession = Exercise(
            stableKey = "test_unlisted_badminton_session",
            name = "Unlisted badminton fixture",
            category = "Sport"
        )
        val syntheticMetadata = RuntimeExerciseMetadataDefaults
            .forIdentity(syntheticSession.stableKey, syntheticSession.name)
            .copy(activityKind = "SPORT_SESSION", badmintonTransferLevel = "DIRECT")
        val syntheticCatalog = RuntimeExerciseMetadataCatalog.of(listOf(syntheticMetadata))
        val syntheticRecord = record(
            syntheticSession,
            date.toString(),
            listOf(set(seconds = 600, confirmed = true))
        )
        assertEquals(
            0.0,
            practiceRaw(listOf(syntheticRecord), listOf(syntheticSession), syntheticCatalog),
            0.0
        )
    }

    @Test
    fun emptyHistoryAndNonBadmintonSportSessionsHaveNoPracticeLoad() {
        val calculator = BadmintonTrainingLoadIndexCalculator(canonicalCatalog)
        assertTrue(calculator.calculate(emptyList(), emptyMap()).isEmpty())
        assertTrue(calculator.dailyLoads(emptyList(), emptyMap()).isEmpty())

        val tennis = exercise(canonicalRows.single { it.stableKey == "ex_2f3b56d0" })
        val tennisRecord = record(tennis, date.toString(), listOf(set(seconds = 3_600, confirmed = true)))
        assertEquals(0.0, practiceRaw(listOf(tennisRecord), listOf(tennis), canonicalCatalog), 0.0)
    }

    @Test
    fun practiceUsesConfirmedMinutesAndConfirmedSetRpeBeforeEntryFallback() {
        val badminton = canonicalExercise("ex_ae9ecdbc")
        val mixed = record(
            exercise = badminton,
            dateText = date.toString(),
            sets = listOf(
                set(seconds = 120, rpe = 6.0, confirmed = true),
                set(seconds = 180, rpe = 10.0, confirmed = true),
                set(seconds = 600, rpe = 10.0, confirmed = false)
            ),
            entryRpe = 2.0
        )
        val entryFallback = record(
            exercise = badminton,
            dateText = date.plusDays(1).toString(),
            sets = listOf(set(seconds = 300, confirmed = true)),
            entryRpe = 9.0
        )

        assertEquals("5 minutes at average set RPE 8.0", 5.25, practiceRaw(listOf(mixed), listOf(badminton)), 0.0001)
        assertEquals("5 minutes at entry RPE 9.0", 5.50, practiceRaw(listOf(entryFallback), listOf(badminton)), 0.0001)
    }

    @Test
    fun practiceRpeModifierBoundariesMatchCurrentRuntime() {
        val badminton = canonicalExercise("ex_badminton_lesson")
        val cases = listOf(
            null to 10.00,
            6.0 to 9.00,
            6.5 to 10.00,
            7.999 to 10.00,
            8.0 to 10.50,
            8.999 to 10.50,
            9.0 to 11.00,
            9.999 to 11.00,
            10.0 to 11.50,
            11.0 to 11.50
        )

        cases.forEach { (rpe, expected) ->
            val input = record(
                badminton,
                date.toString(),
                listOf(set(seconds = 600, rpe = rpe, confirmed = true))
            )
            assertEquals("EXACT_PARITY RPE=$rpe", expected, practiceRaw(listOf(input), listOf(badminton)), 0.0001)
        }
    }

    @Test
    fun zeroNegativeAndUnconfirmedDurationsContributeNoPracticeLoad() {
        val badminton = canonicalExercise("ex_ae9ecdbc")
        val zero = record(badminton, date.toString(), listOf(set(seconds = 0, confirmed = true)))
        // Negative seconds are a characterized CURRENT_IMPLEMENTATION_DETAIL, not future input authority.
        val negative = record(badminton, date.toString(), listOf(set(seconds = -60, confirmed = true)))
        val unconfirmed = record(badminton, date.toString(), listOf(set(seconds = 3_600, confirmed = false)))

        assertEquals(0.0, practiceRaw(listOf(zero), listOf(badminton)), 0.0)
        assertEquals(0.0, practiceRaw(listOf(negative), listOf(badminton)), 0.0)
        assertEquals(0.0, practiceRaw(listOf(unconfirmed), listOf(badminton)), 0.0)
    }

    @Test
    fun practiceAggregatesSameDateMultipleDatesAndCallerSuppliedWeeks() {
        val badminton = canonicalExercise("ex_ae9ecdbc")
        val firstDate = date
        val secondDate = date.plusDays(2)
        val records = listOf(
            record(badminton, firstDate.toString(), listOf(set(seconds = 300, confirmed = true))),
            record(badminton, firstDate.toString(), listOf(set(seconds = 600, rpe = 8.0, confirmed = true))),
            record(badminton, secondDate.toString(), listOf(set(seconds = 60, rpe = 10.0, confirmed = true))),
            record(badminton, "invalid-date", listOf(set(seconds = 600, confirmed = true)))
        )
        val daily = BadmintonTrainingLoadIndexCalculator(canonicalCatalog).dailyLoads(
            records,
            mapOf(badminton.stableKey to badminton)
        )

        assertEquals(listOf(firstDate, secondDate), daily.map { it.date })
        assertEquals(15.50, daily[0].courtRaw, 0.0001)
        assertEquals(1.15, daily[1].courtRaw, 0.0001)

        val weeks = listOf(
            week(firstDate, records.take(2)),
            week(firstDate.plusWeeks(1), listOf(record(badminton, firstDate.plusWeeks(1).toString(), listOf(set(seconds = 1_200, confirmed = true)))))
        )
        val weekly = BadmintonTrainingLoadIndexCalculator(canonicalCatalog).calculate(
            weeks,
            mapOf(badminton.stableKey to badminton)
        )
        assertEquals(listOf(15.50, 20.00), weekly.map { it.courtRaw })
    }

    @Test
    fun genericPracticeDoesNotPopulateTheNineObjectiveStimulusValues() {
        val badminton = canonicalExercise("ex_ae9ecdbc").copy(activityKind = "SPORT_SESSION")
        val objectiveCatalog = CanonicalBadmintonObjectiveCatalog.of(
            listOf(
                CanonicalBadmintonObjectiveRelation(
                    relationId = "fixture_footwork",
                    exerciseStableKey = badminton.stableKey,
                    objective = BadmintonObjective.FOOTWORK,
                    transferLevel = BadmintonObjectiveTransferLevel.DIRECT,
                    provenance = "TEST",
                    evidenceRelationKeys = setOf("fixture"),
                    reviewReason = "Practice/objective boundary characterization"
                )
            )
        )
        val stimulus = BadmintonObjectiveStimulusCalculator(objectiveCatalog).calculate(
            listOf(record(badminton, date.toString(), listOf(set(seconds = 3_600, rpe = 8.0, confirmed = true)))),
            mapOf(badminton.stableKey to badminton)
        )

        assertEquals(9, stimulus.size)
        assertTrue(stimulus.values.all { it == 0.0 })
    }

    private fun practiceRaw(
        records: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        catalog: RuntimeExerciseMetadataCatalog = canonicalCatalog
    ): Double = BadmintonTrainingLoadIndexCalculator(catalog).calculate(
        weeks = listOf(week(date, records)),
        exerciseMap = exercises.associateBy(Exercise::stableKey)
    ).single().courtRaw

    private fun week(start: LocalDate, records: List<WorkoutEntryWithSets>) = WeeklyTrainingData(
        weekStart = start,
        weekEnd = start.plusDays(6),
        entries = records,
        dailyMetrics = emptyList()
    )

    private fun canonicalExercise(stableKey: String): Exercise =
        exercise(canonicalRows.single { it.stableKey == stableKey })

    private fun exercise(metadata: RuntimeExerciseMetadata): Exercise = Exercise(
        stableKey = metadata.stableKey,
        name = metadata.exerciseName,
        category = if (metadata.activityKind == "SPORT_SESSION") "Sport" else "Exercise",
        activityKind = metadata.activityKind
    )

    private fun record(
        exercise: Exercise,
        dateText: String,
        sets: List<WorkoutSet>,
        entryRpe: Double? = null
    ): WorkoutEntryWithSets {
        val entryId = (exercise.stableKey + dateText + sets.hashCode()).hashCode().toLong()
        val entry = WorkoutEntry(
            id = entryId,
            date = dateText,
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category,
            rpe = entryRpe
        )
        return WorkoutEntryWithSets(
            entry = entry,
            sets = sets.mapIndexed { index, item ->
                item.copy(id = entryId * 10 + index, entryId = entryId, setIndex = index + 1)
            }
        )
    }

    private fun set(seconds: Int, confirmed: Boolean, rpe: Double? = null) = WorkoutSet(
        entryId = 0,
        setIndex = 0,
        seconds = seconds,
        confirmed = confirmed,
        rpe = rpe
    )

    private fun emptySleepSignal() = SleepRecoverySignal(
        recentAverageHours = null,
        baselineAverageHours = null,
        sleepDeficitHours = null,
        severity = CoachingSignalSeverity.INFO,
        headline = "",
        detail = ""
    )

    private fun loadCanonicalRows(): List<RuntimeExerciseMetadata> =
        RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(canonicalMetadataFile().readText(Charsets.UTF_8))

    private fun canonicalMetadataFile(): File = sequenceOf(
        File("src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}"),
        File("app/src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}")
    ).firstOrNull(File::isFile) ?: error("Canonical runtime metadata test asset not found.")

    private data class ParityCase(
        val name: String,
        val records: List<WorkoutEntryWithSets>,
        val exercises: List<Exercise>,
        val catalog: RuntimeExerciseMetadataCatalog
    )
}
