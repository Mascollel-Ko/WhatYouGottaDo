package com.training.trackplanner.data

import org.json.JSONObject

internal data class CommunityProfile(
    val nickname: String?,
    val receivedLikeCount: Long,
    val friendCode: String,
    val shareLastWorkoutTime: Boolean = false,
    val shareCurrentTrainingStatus: Boolean = false,
    val shareCurrentExerciseName: Boolean = false
)

internal data class CommunityProgramLabels(
    val strengthRegion: String,
    val strengthGoal: String,
    val includesFunctional: Boolean,
    val functionalPrimaryGoal: String?,
    val includesBadminton: Boolean,
    val badmintonPrimaryGoal: String?
)

internal data class CommunityProgram(
    val publicProgramId: String,
    val sourceProgramStableKey: String? = null,
    val nickname: String,
    val authorReceivedLikeCount: Long,
    val publishedAt: String,
    val updatedAt: String,
    val programName: String,
    val labels: CommunityProgramLabels,
    val authorComment: String,
    val cautionText: String,
    val representativeExercises: List<String>,
    val likeCount: Long,
    val likedByMe: Boolean,
    val isMine: Boolean = false,
    val snapshot: JSONObject? = null
)

internal data class CommunityWeeklySummary(
    val summaryId: String,
    val nickname: String,
    val authorReceivedLikeCount: Long,
    val weekStart: String,
    val payload: JSONObject,
    val publishedAt: String
)

internal data class CommunityFriendPreview(
    val nickname: String,
    val receivedLikeCount: Long,
    val friendCode: String
)

internal data class CommunityFriendRequest(
    val requestId: String,
    val friendshipId: String? = null,
    val nickname: String,
    val receivedLikeCount: Long,
    val friendCode: String,
    val status: String,
    val createdAt: String
)

internal data class CommunityFriendActivity(
    val nickname: String,
    val receivedLikeCount: Long,
    val lastWorkoutAt: String?,
    val isTraining: Boolean?,
    val workoutStartedAt: String?,
    val currentExerciseName: String?
)

internal data class CommunityFriendsState(
    val incoming: List<CommunityFriendRequest> = emptyList(),
    val outgoing: List<CommunityFriendRequest> = emptyList(),
    val friends: List<CommunityFriendRequest> = emptyList(),
    val activity: List<CommunityFriendActivity> = emptyList()
)
