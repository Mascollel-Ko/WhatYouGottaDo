package com.training.trackplanner

import com.training.trackplanner.data.*
import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalizedPreparedRetryStateTest {
    private fun preflight() = PersonalizedPlanningPreflight("exact", LocalDate.of(2026,9,2),
        ProgramSkeletonRequest("test",ProgramGoal.BODYBUILDING,3,60,emptySet(),"",.5,"AUTO",ProgramPeriodizationType.AUTO,3),
        PersonalizedGenerationConstraints(), emptyList(), 123)

    @Test fun preparedFailureRetryUsesProductionPayloadAndSingleFlightWithoutPreparingAgain() = runTest {
        val retry = PersonalizedPreparedRetryState()
        val progress = MutableStateFlow<ProgramBuildProgressState>(ProgramBuildProgressState.Idle)
        val runner = PersonalizedGenerationRunner(this, progress)
        val preflight = preflight()
        val uiAnswers = mutableMapOf("INTENT" to "MIXED")
        val payload = retry.confirm(preflight, uiAnswers)
        uiAnswers["INTENT"] = "CHANGED"
        var preparedCalls = 1
        var generationCalls = 0
        val received = mutableListOf<PersonalizedPreparedRetryState.Payload>()
        fun generate(input: PersonalizedPreparedRetryState.Payload) {
            runner.launch({
                generationCalls++; received += input
                delay(1)
                if (generationCalls == 1) error("actual generation failure")
            }) { retry.clear(); progress.value = ProgramBuildProgressState.Completed(ProgramOptimizationSummary()) }
        }
        generate(payload); advanceUntilIdle()
        assertTrue(progress.value is ProgramBuildProgressState.Failed)
        assertSame(payload, retry.payload)
        retry.retry({ preparedCalls++ }, ::generate)
        retry.retry({ preparedCalls++ }, ::generate)
        advanceUntilIdle()
        assertEquals(1, preparedCalls); assertEquals(2, generationCalls)
        received.forEach { assertSame(preflight, it.preflight); assertEquals(mapOf("INTENT" to "MIXED"), it.answers.values) }
        assertNull(retry.payload); assertTrue(progress.value is ProgramBuildProgressState.Completed)
    }

    @Test fun preparationFailureRetriesPreparationAndNewAttemptClearsOldPayload() = runTest {
        val retry = PersonalizedPreparedRetryState()
        val progress = MutableStateFlow<ProgramBuildProgressState>(ProgramBuildProgressState.Idle)
        val runner = PersonalizedGenerationRunner(this, progress)
        var calls = 0
        fun prepare() { runner.launch({ calls++; error("prepare failure") }) { } }
        prepare(); runCurrent()
        retry.retry(::prepare) { fail("No prepared payload should exist") }; runCurrent()
        assertEquals(2,calls)
        retry.confirm(preflight(), mapOf("ANSWER" to "YES")); retry.clear()
        assertNull(retry.payload)
    }

    @Test fun actualEditorUsesPayloadForFailureCardAndClearsOnlyOnNewAttemptOrSuccess() {
        val root=generateSequence(File(System.getProperty("user.dir")),File::getParentFile).first { File(it,"settings.gradle.kts").isFile }
        val source=File(root,"app/src/main/java/com/training/trackplanner/PlanScreen.kt").readText()
        assertTrue(source.contains("personalizedRetry.retry(::preparePersonalized)"))
        assertTrue(source.contains("runPreparedPersonalized(it.preflight, it.answers.values)"))
        val generate=source.substringAfter("fun runPreparedPersonalized").substringBefore("fun preparePersonalized")
        assertTrue(generate.indexOf("personalizedRetry.confirm") < generate.indexOf("generatePreparedPersonalizedProgram"))
        assertTrue(generate.indexOf("personalizedRetry.clear()") > generate.indexOf("{ generated ->"))
        assertFalse(generate.contains("onFailure"))
    }
}
