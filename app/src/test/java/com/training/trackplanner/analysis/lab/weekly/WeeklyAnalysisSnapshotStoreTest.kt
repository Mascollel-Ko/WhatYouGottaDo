package com.training.trackplanner.analysis.lab.weekly

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyAnalysisSnapshotStoreTest {
    @Test
    fun `conflated dirtiness publishes only the newest revision`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val built = mutableListOf<Long>()
        val store = WeeklyAnalysisSnapshotStore(
            scope = backgroundScope,
            dispatcher = dispatcher,
            debounceMillis = 0L
        ) { revision ->
            built += revision
            snapshot(revision)
        }

        store.markDirty()
        store.markDirty()
        store.markDirty()
        testScheduler.runCurrent()

        assertEquals(listOf(3L), built)
        val ready = assertState<WeeklyAnalysisSnapshotState.Ready>(store.state.value)
        assertEquals(3L, ready.snapshot.sourceRevision)
    }

    @Test
    fun `stale in-flight result cannot replace a newer revision`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val store = WeeklyAnalysisSnapshotStore(
            scope = backgroundScope,
            dispatcher = dispatcher,
            debounceMillis = 0L
        ) { revision ->
            if (revision == 1L) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            snapshot(revision)
        }

        store.markDirty()
        val firstWait = async { firstStarted.await() }
        testScheduler.runCurrent()
        firstWait.await()
        store.markDirty()
        releaseFirst.complete(Unit)
        testScheduler.runCurrent()

        val ready = assertState<WeeklyAnalysisSnapshotState.Ready>(store.state.value)
        assertEquals(2L, ready.requestedRevision)
        assertEquals(2L, ready.snapshot.sourceRevision)
    }

    @Test
    fun `await fresh starts the first build and returns its immutable snapshot`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = WeeklyAnalysisSnapshotStore(
            scope = backgroundScope,
            dispatcher = dispatcher,
            debounceMillis = 0L
        ) { revision -> snapshot(revision) }

        val waiting = async { store.awaitFresh() }
        advanceUntilIdle()

        val result = waiting.await()
        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrThrow().sourceRevision)
    }

    private fun snapshot(revision: Long): WeeklyAnalysisFeatureSnapshot {
        val week = LocalDate.of(2026, 8, 10)
        val key = AnalysisFeatureKey.metric(com.training.trackplanner.analysis.trends.TrendMetricId.STRENGTH_VOLUME)
        return WeeklyAnalysisFeatureSnapshot.createValidated(
            weeks = listOf(week),
            weekStateByStart = mapOf(week to AnalysisWeekState.CLOSED),
            descriptors = mapOf(
                key to AnalysisFeatureDescriptor(
                    key,
                    AnalysisSourceKey.metric(com.training.trackplanner.analysis.trends.TrendMetricId.STRENGTH_VOLUME),
                    "strength volume",
                    AnalysisFeatureFamily.TRAINING_FLOW
                )
            ),
            cellsByFeature = mapOf(
                key to listOf(WeeklyFeatureCell(key, week, WeeklyCellState.STRUCTURAL_ZERO, 0.0, "test"))
            ),
            exerciseAggregates = emptyList(),
            sourceRevision = revision,
            metadataRevision = "test-metadata",
            calculatorVersionSet = setOf("test-calculator")
        )
    }

    private inline fun <reified T> assertState(value: Any?): T {
        assertTrue("Expected ${T::class.java.simpleName}, got ${value?.javaClass?.simpleName}", value is T)
        return value as T
    }
}
