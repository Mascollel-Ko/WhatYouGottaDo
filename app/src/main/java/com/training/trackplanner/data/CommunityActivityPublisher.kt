package com.training.trackplanner.data

/** Best-effort social projection; it is never part of local workout persistence. */
internal fun interface CommunityActivityPublisher {
    suspend fun publish(
        session: CloudAuthSession,
        exerciseStableKey: String,
        exerciseName: String,
        workoutStartedAt: Long?
    )
}

internal class DefaultCommunityActivityPublisher(
    private val client: CommunityClient = CommunityClient()
) : CommunityActivityPublisher {
    override suspend fun publish(
        session: CloudAuthSession,
        exerciseStableKey: String,
        exerciseName: String,
        workoutStartedAt: Long?
    ) = client.updateActivity(session, exerciseStableKey, exerciseName, workoutStartedAt)
}

internal class CommunityActivityConfirmationPolicy(
    private val publisher: CommunityActivityPublisher
) {
    suspend fun publishIfConfirmed(
        result: RecordSetMutationResult,
        session: CloudAuthSession?,
        entry: WorkoutEntry
    ) {
        if (!result.newlyConfirmed || session == null) return
        publisher.publish(
            session,
            entry.exerciseStableKey,
            entry.exerciseName,
            entry.firstConfirmedAt ?: entry.performedAt ?: entry.createdAt
        )
    }
}
