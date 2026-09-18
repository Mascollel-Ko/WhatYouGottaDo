package com.training.trackplanner.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CommunityActivityPublisherTest {
    @Test fun confirmedMutationPublishesOnceWithEntryIdentityAndStartTime() = runBlocking {
        val calls = mutableListOf<List<Any?>>()
        val publisher = CommunityActivityPublisher { session, key, name, startedAt ->
            calls += listOf(session.userId, key, name, startedAt)
        }
        val entry = WorkoutEntry(
            date = "2026-09-18",
            exerciseStableKey = "exercise.squat",
            exerciseName = "Squat",
            category = "STRENGTH",
            createdAt = 100L,
            firstConfirmedAt = 200L
        )
        val result = RecordSetMutationResult(
            date = entry.date,
            beforeCompletionState = StrengthSessionCompletionState(1, 0),
            afterCompletionState = StrengthSessionCompletionState(0, 1),
            newlyConfirmed = true,
            derivedAnalysisDirty = true
        )
        CommunityActivityConfirmationPolicy(publisher).publishIfConfirmed(
            result,
            CloudAuthSession("user-1", "token"),
            entry
        )
        assertEquals(listOf("user-1", "exercise.squat", "Squat", 200L), calls.single())
    }

    @Test fun plannedOrUnconfirmedMutationDoesNotPublish() = runBlocking {
        var calls = 0
        val publisher = CommunityActivityPublisher { _, _, _, _ -> calls++ }
        val entry = WorkoutEntry(date = "2026-09-18", exerciseStableKey = "exercise.squat", exerciseName = "Squat", category = "STRENGTH")
        val result = RecordSetMutationResult(
            date = entry.date,
            beforeCompletionState = StrengthSessionCompletionState(1, 0),
            afterCompletionState = StrengthSessionCompletionState(1, 1),
            newlyConfirmed = false,
            derivedAnalysisDirty = true
        )
        CommunityActivityConfirmationPolicy(publisher).publishIfConfirmed(result, CloudAuthSession("user-1", "token"), entry)
        assertFalse(calls > 0)
    }
}
