package com.training.trackplanner.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** One process owns the application Room database. Acquire before Room, never inside it. */
private val localDataGate = Mutex()
private class LocalDataOwner : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<LocalDataOwner>
}
internal suspend fun <T> withLocalDataGate(block: suspend () -> T): T {
    if (coroutineContext[LocalDataOwner] != null) return block()
    localDataGate.lock()
    try { return withContext(LocalDataOwner()) { block() } }
    finally { localDataGate.unlock() }
}
