package com.training.trackplanner

import com.training.trackplanner.data.*
import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalizedGenerationRunnerTest {
    @Test fun longGenerationHasNoFifteenSecondTimeoutAndDuplicateLaunchIsRejected() = runTest {
        val state = MutableStateFlow<ProgramBuildProgressState>(ProgramBuildProgressState.Idle)
        val runner = PersonalizedGenerationRunner(this, state)
        var calls = 0
        var result = 0
        assertTrue(runner.launch({ calls++; delay(20_000); 42 }) {
            result = it; state.value = ProgramBuildProgressState.Completed(ProgramOptimizationSummary())
        })
        assertTrue(state.value is ProgramBuildProgressState.Running) // before the coroutine gets a turn
        assertFalse(runner.launch({ calls++; 0 }) { })
        runCurrent(); advanceTimeBy(15_001); runCurrent()
        assertEquals(1, calls); assertEquals(0, result)
        assertTrue(state.value is ProgramBuildProgressState.Running)
        advanceUntilIdle()
        assertEquals(42, result); assertTrue(state.value is ProgramBuildProgressState.Completed)
    }

    @Test fun reportedMilestonesAreMonotonicAndDoNotAdvanceWithTime() = runTest {
        val state = MutableStateFlow<ProgramBuildProgressState>(ProgramBuildProgressState.Idle)
        val gate = CompletableDeferred<Unit>()
        var report: PersonalizedPlannerProgressReporter? = null
        PersonalizedGenerationRunner(this, state).launch({ report = it; gate.await() }) { state.value = ProgramBuildProgressState.Idle }
        runCurrent()
        report!!.report(PersonalizedPlannerStage.PATTERNS)
        advanceTimeBy(30_000)
        assertEquals(20, (state.value as ProgramBuildProgressState.Running).progressPercent)
        report!!.report(PersonalizedPlannerStage.EXPANSION_RECHECK)
        report!!.report(PersonalizedPlannerStage.DISTRIBUTION)
        assertEquals(80, (state.value as ProgramBuildProgressState.Running).progressPercent)
        report!!.report(PersonalizedPlannerStage.EXPANSION_RECHECK)
        assertEquals(PersonalizedPlannerStage.EXPANSION_RECHECK.message, (state.value as ProgramBuildProgressState.Running).message)
        gate.complete(Unit)
    }

    @Test fun failureExitsRunningAndAllowsRetry() = runTest {
        val state = MutableStateFlow<ProgramBuildProgressState>(ProgramBuildProgressState.Idle)
        val runner = PersonalizedGenerationRunner(this, state)
        runner.launch<Unit>({ error("actual failure") }) { fail("unexpected success") }
        runCurrent()
        assertEquals(ProgramBuildProgressState.Failed("actual failure"), state.value)
        assertTrue(runner.launch({ 1 }) { state.value = ProgramBuildProgressState.Idle })
        runCurrent(); assertEquals(ProgramBuildProgressState.Idle, state.value)
    }

    @Test fun lifecycleCancellationPropagatesAndDoesNotBecomeFailure() = runTest {
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)
        val state = MutableStateFlow<ProgramBuildProgressState>(ProgramBuildProgressState.Idle)
        var cancelled = false
        PersonalizedGenerationRunner(scope, state).launch({ try { awaitCancellation() } finally { cancelled = true } }) { }
        runCurrent(); job.cancel(); runCurrent()
        assertTrue(cancelled); assertEquals(ProgramBuildProgressState.Idle, state.value)
    }

    @Test fun recordBasedRoutesUseRunnerWithoutTimeoutAndFreezeAnswersBeforeLaunch() {
        val root = generateSequence(File(System.getProperty("user.dir")), File::getParentFile).first { File(it, "settings.gradle.kts").isFile }
        val vm = File(root, "app/src/main/java/com/training/trackplanner/TrainingViewModel.kt").readText()
        val personalized = vm.substringAfter("private val personalizedGenerationRunner").substringBefore("fun saveLegacyAutoProgram")
        assertFalse(personalized.contains("withTimeout"))
        assertEquals(3, Regex("personalizedGenerationRunner.launch").findAll(personalized).count())
        assertEquals(2, Regex("answers.values.toMap").findAll(personalized).count())
        assertTrue(vm.substringBefore("private val personalizedGenerationRunner").contains("withTimeout(12_000)"))
        val ui = File(root, "app/src/main/java/com/training/trackplanner/PlanScreen.kt").readText()
            .substringAfter("fun runPreparedPersonalized").substringBefore("fun preparePersonalized")
        assertTrue(ui.indexOf("pendingPersonalizedPreflight = null") < ui.indexOf("viewModel.generatePreparedPersonalizedProgram"))
        assertTrue(ui.contains("answers.toMap()")); assertTrue(ui.contains("personalizedDraft = generated.copy"))
        val dialog = File(root, "app/src/main/java/com/training/trackplanner/PersonalizedGenerationProgressDialog.kt").readText()
        assertTrue(dialog.contains("dismissOnBackPress = false")); assertTrue(dialog.contains("dismissOnClickOutside = false"))
    }
}
