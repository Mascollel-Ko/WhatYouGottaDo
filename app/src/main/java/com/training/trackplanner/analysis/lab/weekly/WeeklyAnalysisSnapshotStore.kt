package com.training.trackplanner.analysis.lab.weekly

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal sealed interface WeeklyAnalysisSnapshotState {
    data object Empty : WeeklyAnalysisSnapshotState

    data class Building(
        val requestedRevision: Long,
        val previousSnapshot: WeeklyAnalysisFeatureSnapshot?
    ) : WeeklyAnalysisSnapshotState

    data class Ready(
        val requestedRevision: Long,
        val snapshot: WeeklyAnalysisFeatureSnapshot
    ) : WeeklyAnalysisSnapshotState

    data class Failed(
        val requestedRevision: Long,
        val message: String
    ) : WeeklyAnalysisSnapshotState
}

internal class WeeklyAnalysisSnapshotStore(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val debounceMillis: Long = 250L,
    private val rebuild: suspend (requestedRevision: Long) -> WeeklyAnalysisFeatureSnapshot
) {
    private val requestedRevision = AtomicLong(0L)
    private val rebuildSignals = Channel<Unit>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow<WeeklyAnalysisSnapshotState>(WeeklyAnalysisSnapshotState.Empty)

    val state: StateFlow<WeeklyAnalysisSnapshotState> = mutableState.asStateFlow()

    init {
        scope.launch(dispatcher) {
            for (ignored in rebuildSignals) {
                if (debounceMillis > 0L) delay(debounceMillis)
                while (true) {
                    val revision = requestedRevision.get()
                    val previous = when (val current = mutableState.value) {
                        is WeeklyAnalysisSnapshotState.Ready -> current.snapshot
                        is WeeklyAnalysisSnapshotState.Building -> current.previousSnapshot
                        else -> null
                    }
                    mutableState.value = WeeklyAnalysisSnapshotState.Building(revision, previous)
                    val result = runCatching { rebuild(revision) }
                    if (revision != requestedRevision.get()) continue
                    mutableState.value = result.fold(
                        onSuccess = { snapshot ->
                            require(snapshot.sourceRevision == revision) {
                                "weekly snapshot revision ${snapshot.sourceRevision} does not match request $revision"
                            }
                            WeeklyAnalysisSnapshotState.Ready(revision, snapshot)
                        },
                        onFailure = { failure ->
                            WeeklyAnalysisSnapshotState.Failed(
                                revision,
                                failure.message ?: failure::class.simpleName.orEmpty()
                            )
                        }
                    )
                    break
                }
            }
        }
    }

    fun markDirty(): Long {
        val revision = requestedRevision.incrementAndGet()
        rebuildSignals.trySend(Unit)
        return revision
    }

    suspend fun awaitFresh(): Result<WeeklyAnalysisFeatureSnapshot> {
        if (requestedRevision.get() == 0L) markDirty()
        while (true) {
            val expected = requestedRevision.get()
            when (val terminal = state.first { current ->
                when (current) {
                    is WeeklyAnalysisSnapshotState.Ready -> current.requestedRevision >= expected
                    is WeeklyAnalysisSnapshotState.Failed -> current.requestedRevision >= expected
                    else -> false
                }
            }) {
                is WeeklyAnalysisSnapshotState.Ready -> {
                    if (terminal.requestedRevision == requestedRevision.get()) return Result.success(terminal.snapshot)
                }
                is WeeklyAnalysisSnapshotState.Failed -> {
                    if (terminal.requestedRevision == requestedRevision.get()) {
                        return Result.failure(IllegalStateException(terminal.message))
                    }
                }
                else -> Unit
            }
        }
    }
}
