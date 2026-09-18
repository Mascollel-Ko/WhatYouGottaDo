package com.training.trackplanner.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CommunityClient(
    private val config: CloudBackupConfig = CloudBackupConfig.fromBuildConfig(),
    private val transport: CloudHttpTransport = UrlConnectionCloudHttpTransport(config.publishableKey)
) {
    private suspend fun call(session: CloudAuthSession, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        require(config.configured) { "Community is not configured for this build." }
        val response = transport.postJson(
            "${config.supabaseUrl}/functions/v1/community-api",
            session,
            body.toString()
        )
        val payload = runCatching { JSONObject(response.body) }.getOrElse { JSONObject() }
        if (response.status !in 200..299) {
            throw CommunityException(payload.optString("error").ifBlank { "COMMUNITY_REQUEST_FAILED" })
        }
        return@withContext payload
    }

    suspend fun profile(session: CloudAuthSession): CommunityProfile =
        parseProfile(call(session, JSONObject().put("op", "profile_get")).getJSONObject("profile"))

    suspend fun setNickname(session: CloudAuthSession, nickname: String): CommunityProfile =
        parseProfile(call(session, JSONObject().put("op", "profile_set").put("nickname", nickname)).getJSONObject("profile"))

    suspend fun setPrivacy(
        session: CloudAuthSession,
        shareLastWorkoutTime: Boolean,
        shareCurrentTrainingStatus: Boolean,
        shareCurrentExerciseName: Boolean
    ): CommunityProfile = parseProfile(
        call(session, JSONObject()
            .put("op", "privacy_set")
            .put("shareLastWorkoutTime", shareLastWorkoutTime)
            .put("shareCurrentTrainingStatus", shareCurrentTrainingStatus)
            .put("shareCurrentExerciseName", shareCurrentExerciseName)
        ).getJSONObject("profile")
    )

    suspend fun feed(
        session: CloudAuthSession,
        query: String = "",
        sort: String = "LATEST",
        strengthRegion: String? = null,
        strengthGoal: String? = null,
        includesFunctional: Boolean? = null,
        includesBadminton: Boolean? = null
    ): List<CommunityProgram> {
        val body = JSONObject().put("op", "program_feed").put("q", query).put("sort", sort).put("pageSize", 20)
        strengthRegion?.let { body.put("strengthRegion", it) }
        strengthGoal?.let { body.put("strengthGoal", it) }
        includesFunctional?.let { body.put("includesFunctional", it) }
        includesBadminton?.let { body.put("includesBadminton", it) }
        return parsePrograms(call(session, body).optJSONArray("programs"))
    }

    suspend fun detail(session: CloudAuthSession, publicProgramId: String): CommunityProgram =
        parseProgram(call(session, JSONObject().put("op", "program_detail").put("publicProgramId", publicProgramId)).getJSONObject("program"))

    suspend fun like(session: CloudAuthSession, publicProgramId: String, liked: Boolean): Pair<Boolean, Long> {
        val result = call(session, JSONObject().put("op", "program_like").put("publicProgramId", publicProgramId).put("liked", liked))
        return result.optBoolean("liked") to result.optLong("likeCount")
    }

    suspend fun publish(
        session: CloudAuthSession,
        sourceProgramStableKey: String,
        sourceUpdatedAt: Long,
        snapshot: JSONObject,
        programName: String,
        labels: CommunityProgramLabels,
        authorComment: String,
        cautionText: String
    ): CommunityProgram {
        val body = JSONObject()
            .put("op", "program_publish")
            .put("sourceProgramStableKey", sourceProgramStableKey)
            .put("sourceUpdatedAt", sourceUpdatedAt)
            .put("snapshot", snapshot)
            .put("programName", programName)
            .put("strengthRegion", labels.strengthRegion)
            .put("strengthGoal", labels.strengthGoal)
            .put("includesFunctional", labels.includesFunctional)
            .put("includesBadminton", labels.includesBadminton)
            .put("authorComment", authorComment)
            .put("cautionText", cautionText)
        labels.functionalPrimaryGoal?.let { body.put("functionalPrimaryGoal", it) }
        labels.badmintonPrimaryGoal?.let { body.put("badmintonPrimaryGoal", it) }
        return parseProgram(call(session, body).getJSONObject("program"))
    }

    suspend fun unpublish(session: CloudAuthSession, publicProgramId: String) {
        call(session, JSONObject().put("op", "program_unpublish").put("publicProgramId", publicProgramId))
    }

    suspend fun weeklyFeed(session: CloudAuthSession): List<CommunityWeeklySummary> {
        val rows = call(session, JSONObject().put("op", "weekly_feed").put("pageSize", 20)).optJSONArray("summaries") ?: return emptyList()
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            CommunityWeeklySummary(
                summaryId = row.getString("summaryId"), nickname = row.optString("nickname"),
                authorReceivedLikeCount = row.optLong("authorReceivedLikeCount"), weekStart = row.getString("weekStart"),
                payload = row.optJSONObject("payload") ?: JSONObject(), publishedAt = row.optString("publishedAt")
            )
        }
    }

    suspend fun publishWeekly(session: CloudAuthSession, weekStart: String, payload: JSONObject) {
        call(session, JSONObject().put("op", "weekly_publish").put("weekStart", weekStart).put("payload", payload))
    }

    suspend fun unpublishWeekly(session: CloudAuthSession, weekStart: String) {
        call(session, JSONObject().put("op", "weekly_unpublish").put("weekStart", weekStart))
    }

    suspend fun friendLookup(session: CloudAuthSession, friendCode: String): CommunityFriendPreview {
        val row = call(session, JSONObject().put("op", "friend_lookup").put("friendCode", friendCode)).getJSONObject("preview")
        return CommunityFriendPreview(row.optString("nickname"), row.optLong("receivedLikeCount"), row.optString("friendCode"))
    }

    suspend fun sendFriendRequest(session: CloudAuthSession, friendCode: String) {
        call(session, JSONObject().put("op", "friend_request_send").put("friendCode", friendCode))
    }

    suspend fun friends(session: CloudAuthSession): CommunityFriendsState {
        val row = call(session, JSONObject().put("op", "friends"))
        return CommunityFriendsState(
            incoming = parseFriendRequests(row.optJSONArray("incoming")),
            outgoing = parseFriendRequests(row.optJSONArray("outgoing")),
            friends = parseFriendRequests(row.optJSONArray("friends")),
            activity = parseActivity(row.optJSONArray("activity"))
        )
    }

    suspend fun respondFriendRequest(session: CloudAuthSession, requestId: String, accept: Boolean) {
        call(session, JSONObject().put("op", "friend_respond").put("requestId", requestId).put("accept", accept))
    }

    suspend fun removeFriend(session: CloudAuthSession, friendshipId: String) {
        call(session, JSONObject().put("op", "friend_remove").put("friendshipId", friendshipId))
    }

    suspend fun blockFriend(session: CloudAuthSession, friendCode: String) {
        call(session, JSONObject().put("op", "friend_block").put("friendCode", friendCode))
    }

    suspend fun updateActivity(
        session: CloudAuthSession,
        currentExerciseStableKey: String?,
        currentExerciseName: String?,
        workoutStartedAt: Long?
    ) {
        call(session, JSONObject().put("op", "activity_update").put("performed", true).apply {
            currentExerciseStableKey?.let { put("currentExerciseStableKey", it) }
            currentExerciseName?.let { put("currentExerciseName", it) }
            workoutStartedAt?.let { put("workoutStartedAt", it) }
        })
    }

    private fun parseProfile(row: JSONObject) = CommunityProfile(
        nickname = row.optString("nickname").takeIf(String::isNotBlank),
        receivedLikeCount = row.optLong("receivedLikeCount"),
        friendCode = row.optString("friendCode"),
        shareLastWorkoutTime = row.optBoolean("shareLastWorkoutTime"),
        shareCurrentTrainingStatus = row.optBoolean("shareCurrentTrainingStatus"),
        shareCurrentExerciseName = row.optBoolean("shareCurrentExerciseName")
    )

    private fun parsePrograms(rows: JSONArray?): List<CommunityProgram> = if (rows == null) emptyList() else
        (0 until rows.length()).map { parseProgram(rows.getJSONObject(it)) }

    private fun parseProgram(row: JSONObject): CommunityProgram {
        val labels = row.optJSONObject("labels") ?: JSONObject()
        val exercises = row.optJSONArray("representativeExercises")
        return CommunityProgram(
            publicProgramId = row.getString("publicProgramId"), nickname = row.optString("nickname"),
            authorReceivedLikeCount = row.optLong("authorReceivedLikeCount"), publishedAt = row.optString("publishedAt"),
            updatedAt = row.optString("updatedAt"), programName = row.optString("programName"),
            labels = CommunityProgramLabels(labels.optString("strengthRegion"), labels.optString("strengthGoal"), labels.optBoolean("includesFunctional"), labels.optString("functionalPrimaryGoal").takeIf(String::isNotBlank), labels.optBoolean("includesBadminton"), labels.optString("badmintonPrimaryGoal").takeIf(String::isNotBlank)),
            authorComment = row.optString("authorComment"), cautionText = row.optString("cautionText"),
            representativeExercises = if (exercises == null) emptyList() else (0 until exercises.length()).map { exercises.optString(it) },
            likeCount = row.optLong("likeCount"), likedByMe = row.optBoolean("likedByMe"), isMine = row.optBoolean("isMine"), snapshot = row.optJSONObject("snapshot")
        )
    }

    private fun parseFriendRequests(rows: JSONArray?): List<CommunityFriendRequest> = if (rows == null) emptyList() else
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            CommunityFriendRequest(row.optString("requestId"), row.optString("friendshipId").takeIf(String::isNotBlank), row.optString("nickname"), row.optLong("receivedLikeCount"), row.optString("friendCode"), row.optString("status"), row.optString("createdAt"))
        }

    private fun parseActivity(rows: JSONArray?): List<CommunityFriendActivity> = if (rows == null) emptyList() else
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            CommunityFriendActivity(row.optString("nickname"), row.optLong("receivedLikeCount"), row.optString("lastWorkoutAt").takeIf(String::isNotBlank), if (row.isNull("isTraining")) null else row.optBoolean("isTraining"), row.optString("workoutStartedAt").takeIf(String::isNotBlank), row.optString("currentExerciseName").takeIf(String::isNotBlank))
        }
}

internal class CommunityException(val code: String) : IOException(code)
