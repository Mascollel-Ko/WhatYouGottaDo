package com.training.trackplanner

import com.training.trackplanner.data.RecordSetMutationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Called on the owning (ViewModel) dispatcher, only AFTER a Room commit. Never owns raw saves. */
internal class RecordDerivedRefreshCoordinator(
    scope: CoroutineScope,
    private val refresh: suspend () -> Unit,
    private val onFailure: (Exception) -> Unit,
    private val idleMillis: Long = 750L
) {
    private data class Dirty(val revision: Long, val eligible: Boolean, val immediate: Boolean)
    private val dirty = mutableMapOf<String, Dirty>()
    private var revision = 0L
    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (signal in wake) {
                while (dirty.values.any { it.eligible }) {
                    // Engineering/UI coalescing only; never a physiological threshold.
                    if (dirty.values.none { it.immediate } &&
                        withTimeoutOrNull(idleMillis) { wake.receive() } != null) continue
                    val refreshing = dirty.mapValues { (_, value) -> value.copy(immediate = false) }
                    dirty.putAll(refreshing)
                    try {
                        refresh()
                        refreshing.forEach { (date, version) ->
                            if (dirty[date] == version) dirty.remove(date)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        // Retain dirty state for the next edit; startup also rebuilds from Room.
                        onFailure(failure)
                        break
                    }
                }
            }
        }
    }

    fun afterCommit(result: RecordSetMutationResult) {
        if (!result.derivedAnalysisDirty) return
        val pendingCompletion = dirty[result.date]?.immediate == true
        val immediate = pendingCompletion || result.dateJustCompleted
        val complete = result.afterCompletionState.let { it.unconfirmedSetCount == 0 && it.confirmedSetCount > 0 }
        dirty[result.date] = Dirty(++revision, complete || immediate, immediate)
        wake.trySend(Unit)
    }
}
