package com.training.trackplanner

import com.training.trackplanner.data.RecordSetMutationResult
import com.training.trackplanner.data.StrengthSessionCompletionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordDerivedRefreshCoordinatorTest {
    private fun edit(before: Int, after: Int, date: String = "2026-09-10") = RecordSetMutationResult(
        date, StrengthSessionCompletionState(before, 2), StrengthSessionCompletionState(after, 3),
        newlyConfirmed = after < before, derivedAnalysisDirty = true
    )

    @Test fun `incomplete edits and intermediate confirmations never refresh`() = runTest {
        var calls = 0
        val worker = RecordDerivedRefreshCoordinator(backgroundScope, { calls++ }, { throw it })
        worker.afterCommit(edit(3, 2))
        worker.afterCommit(edit(2, 2))
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, calls)
    }

    @Test fun `completion starts one refresh independently of subsequent commits`() = runTest {
        var progression = 0
        var analysis = 0
        val gate = CompletableDeferred<Unit>()
        val worker = RecordDerivedRefreshCoordinator(backgroundScope, { progression++; gate.await(); analysis++ }, { throw it })
        worker.afterCommit(edit(1, 0))
        runCurrent()
        assertEquals(1, progression)
        assertEquals(0, analysis)
        // Notification must return despite the suspended heavy dependency.
        worker.afterCommit(edit(0, 0))
        worker.afterCommit(edit(0, 0))
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, analysis)
        advanceTimeBy(750)
        runCurrent()
        assertEquals(2, progression)
        assertEquals(2, analysis)
    }

    @Test fun `rapid completed edits coalesce and no-op does not refresh`() = runTest {
        var calls = 0
        val worker = RecordDerivedRefreshCoordinator(backgroundScope, { calls++ }, { throw it })
        repeat(10) { worker.afterCommit(edit(0, 0)); advanceTimeBy(100) }
        assertEquals(0, calls)
        advanceTimeBy(750)
        runCurrent()
        assertEquals(1, calls)
        worker.afterCommit(edit(0, 0).copy(derivedAnalysisDirty = false))
        advanceTimeBy(1000)
        assertEquals(1, calls)
    }

    @Test fun `two dates coalesce without dropping completion on another date`() = runTest {
        var calls = 0
        val worker = RecordDerivedRefreshCoordinator(backgroundScope, { calls++ }, { throw it })
        worker.afterCommit(edit(1, 0))
        worker.afterCommit(edit(4, 3, "2026-09-09"))
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(2000)
        assertEquals(1, calls)
    }

    @Test fun `derived failure is isolated and next edit retries`() = runTest {
        var calls = 0
        var errors = 0
        val worker = RecordDerivedRefreshCoordinator(backgroundScope, { if (++calls == 1) error("blocked derived") }, { errors++ })
        worker.afterCommit(edit(1, 0))
        runCurrent()
        assertEquals(1, errors)
        worker.afterCommit(edit(0, 0))
        advanceTimeBy(751)
        runCurrent()
        assertEquals(2, calls)
    }
}
