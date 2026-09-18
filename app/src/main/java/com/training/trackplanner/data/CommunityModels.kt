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
    val strengthRegions: List<String>,
    val strengthGoals: List<String>,
    val functionalGoals: List<String>,
    val badmintonGoals: List<String>
)

/** Canonical, deterministic label vocabulary shared by publication and search UI. */
internal object CommunityProgramLabelCatalog {
    val strengthRegions = listOf("UPPER_BODY", "LOWER_BODY", "ALL_LIMBS")
    val strengthGoals = listOf("HYPERTROPHY", "STRENGTH")
    val functionalGoals = listOf("EXPLOSIVE_ACCELERATION", "ELASTIC_GROUND_REACTION", "BODY_COORDINATION")
    val badmintonGoals = listOf("SWING_POWER", "LANDING_DECELERATION_STABILITY", "FOOTWORK")

    fun normalize(
        strengthRegions: List<String>,
        strengthGoals: List<String>,
        functionalGoals: List<String> = emptyList(),
        badmintonGoals: List<String> = emptyList()
    ): CommunityProgramLabels {
        return CommunityProgramLabels(
            normalizeRequired(strengthRegions, this.strengthRegions, "strengthRegions"),
            normalizeRequired(strengthGoals, this.strengthGoals, "strengthGoals"),
            normalizeOptional(functionalGoals, this.functionalGoals, "functionalGoals"),
            normalizeOptional(badmintonGoals, this.badmintonGoals, "badmintonGoals")
        )
    }

    private fun normalizeRequired(values: List<String>, allowed: List<String>, field: String): List<String> {
        val result = normalizeOptional(values, allowed, field)
        require(result.isNotEmpty()) { "$field must contain at least one value" }
        return result
    }

    private fun normalizeOptional(values: List<String>, allowed: List<String>, field: String): List<String> {
        val normalized = values.map { it.trim().uppercase() }.distinct()
        require(normalized.size <= allowed.size) { "$field contains too many values" }
        require(normalized.all { it in allowed }) { "$field contains an unsupported value" }
        return allowed.filter { it in normalized }
    }
}

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
